package com.biel.lifecamp.system.service.impl;

import com.biel.lifecamp.system.common.exception.EhrSyncException;
import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.dao.EhrSyncMapper;
import com.biel.lifecamp.system.manager.EhrEmployeeManager;
import com.biel.lifecamp.system.manager.EhrEmployeeSnapshotValidator;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeValidationResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncPromotionResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncRunCreateDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncRunDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncIssueDTO;
import com.biel.lifecamp.system.service.EhrSyncPersistenceService;
import com.biel.lifecamp.system.service.EhrSyncService;
import com.biel.lifecamp.starter.task.TaskLease;
import com.biel.lifecamp.starter.task.TaskLeaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 编排 EHR 全量抓取、严格校验和员工投影原子生效。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Service
public final class EhrSyncServiceImpl implements EhrSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EhrSyncServiceImpl.class);
    private static final String TASK_TYPE = "EHR_EMPLOYEE_FULL_SYNC";
    private static final Duration LEASE_DURATION = Duration.ofHours(2);
    private final EhrEmployeeManager ehrEmployeeManager;
    private final EhrEmployeeSnapshotValidator snapshotValidator;
    private final EhrSyncPersistenceService persistenceService;
    private final EhrSyncMapper ehrSyncMapper;
    private final LongIdGenerator idGenerator;
    private final Clock clock;
    private final TaskLeaseRepository taskLeaseRepository;
    private final ThreadPoolTaskExecutor syncTaskExecutor;

    /**
     * 创建 EHR 全量同步编排服务。
     *
     * @param ehrEmployeeManager EHR 人员接口访问边界
     * @param snapshotValidator 全量快照校验器
     * @param persistenceService 快照原子生效服务
     * @param ehrSyncMapper 同步运行数据访问对象
     * @param idGenerator 本地主键生成器
     * @param clock 业务时钟
     * @param taskLeaseRepository 集群任务租约仓储
     * @param syncTaskExecutor EHR 同步编排执行器
     */
    public EhrSyncServiceImpl(EhrEmployeeManager ehrEmployeeManager,
                              EhrEmployeeSnapshotValidator snapshotValidator,
                              EhrSyncPersistenceService persistenceService,
                              EhrSyncMapper ehrSyncMapper,
                              LongIdGenerator idGenerator,
                              Clock clock,
                              TaskLeaseRepository taskLeaseRepository,
                              @Qualifier("ehrSyncTaskExecutor")
                              ThreadPoolTaskExecutor syncTaskExecutor) {
        this.ehrEmployeeManager = ehrEmployeeManager;
        this.snapshotValidator = snapshotValidator;
        this.persistenceService = persistenceService;
        this.ehrSyncMapper = ehrSyncMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.taskLeaseRepository = taskLeaseRepository;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrSyncRunDTO submitFullSync(
            String triggerType, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        EhrSyncRunDTO existing = findIdempotentRun(triggerType, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        PendingRun pendingRun = createPendingRun(triggerType, idempotencyKey);
        if (!pendingRun.created()) {
            return pendingRun.run();
        }
        long runId = pendingRun.run().id();
        try {
            syncTaskExecutor.execute(
                    () -> executeSubmittedRun(runId, triggerType));
        } catch (TaskRejectedException ex) {
            Instant failedAt = clock.instant();
            ehrSyncMapper.updateRunFailed(
                    runId,
                    "EHR_SYNC_QUEUE_FULL",
                    "EHR synchronization queue is full",
                    failedAt);
            LOGGER.warn(
                    "event=ehr_employee_sync_submission_rejected runId={} "
                            + "triggerType={} failureCode=EHR_SYNC_QUEUE_FULL "
                            + "failedAt={} thread={}",
                    runId, triggerType, failedAt, threadName());
            throw new EhrSyncException(
                    "EHR_SYNC_QUEUE_FULL",
                    "EHR synchronization queue is full");
        }
        LOGGER.info(
                "event=ehr_employee_sync_submitted runId={} triggerType={} "
                        + "runStatus=PENDING thread={}",
                runId, triggerType, threadName());
        return pendingRun.run();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrSyncRunDTO executeFullSync(String triggerType, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        EhrSyncRunDTO existing = findIdempotentRun(triggerType, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        /*
         * 租约覆盖整个“拉取、校验、生效”过程。租约竞争失败表示已有实例执行，
         * 调用方应查询已有运行，不得把它当成可立即重试的普通技术失败。
         */
        TaskLease lease = taskLeaseRepository.tryAcquire(TASK_TYPE, LEASE_DURATION)
                .orElseThrow(() -> new EhrSyncException(
                        "EHR_SYNC_ALREADY_RUNNING", "An EHR synchronization is already running"));
        PendingRun pendingRun = createPendingRun(triggerType, idempotencyKey);
        if (!pendingRun.created()) {
            taskLeaseRepository.markSucceeded(lease);
            return pendingRun.run();
        }
        return executePendingRun(pendingRun.run().id(), triggerType, lease);
    }

    private void executeSubmittedRun(long runId, String triggerType) {
        TaskLease lease = taskLeaseRepository.tryAcquire(TASK_TYPE, LEASE_DURATION)
                .orElse(null);
        if (lease == null) {
            Instant failedAt = clock.instant();
            ehrSyncMapper.updateRunFailed(
                    runId,
                    "EHR_SYNC_ALREADY_RUNNING",
                    "An EHR synchronization is already running",
                    failedAt);
            LOGGER.warn(
                    "event=ehr_employee_sync_failed runId={} triggerType={} "
                            + "failureStage=PREPARING "
                            + "failureCode=EHR_SYNC_ALREADY_RUNNING failedAt={} thread={}",
                    runId, triggerType, failedAt, threadName());
            return;
        }
        try {
            executePendingRun(runId, triggerType, lease);
        } catch (RuntimeException ignored) {
            // executePendingRun 已持久化失败状态并输出包含阶段和 runId 的异常日志。
        }
    }

    private EhrSyncRunDTO executePendingRun(
            long runId, String triggerType, TaskLease lease) {
        Instant startedAt = clock.instant();
        SyncPhase failureStage = SyncPhase.PREPARING;
        LOGGER.info(
                "event=ehr_employee_sync_started runId={} triggerType={} startedAt={} "
                        + "leaseDurationSeconds={} thread={}",
                runId, triggerType, startedAt, LEASE_DURATION.toSeconds(), threadName());
        try {
            long preparationStartedNanos = System.nanoTime();
            if (ehrSyncMapper.updateRunRunning(runId, startedAt) == 0) {
                taskLeaseRepository.markSucceeded(lease);
                EhrSyncRunDTO currentRun = ehrSyncMapper.selectRun(runId);
                LOGGER.info(
                        "event=ehr_employee_sync_execution_skipped runId={} "
                                + "triggerType={} runStatus={} reason=NOT_PENDING thread={}",
                        runId, triggerType,
                        currentRun == null ? "MISSING" : currentRun.status(),
                        threadName());
                return currentRun;
            }
            LOGGER.info(
                    "event=ehr_sync_preparation_completed runId={} runStatus=RUNNING "
                            + "durationMs={} thread={}",
                    runId, elapsedMillis(preparationStartedNanos), threadName());

            failureStage = SyncPhase.FETCHING;
            long stageStartedNanos = logStageStarted(runId, failureStage, -1);
            EhrEmployeeSnapshotDTO snapshot =
                    ehrEmployeeManager.fetchActiveEmployeeSnapshot(runId);
            logStageCompleted(runId, failureStage, stageStartedNanos,
                    snapshot.totalRecords(), snapshot.totalRecords(),
                    "fetchedRecords=" + snapshot.totalRecords()
                            + " totalPages=" + snapshot.totalPages());

            // 快照完整性仍是批次门禁；单个人员字段问题则交给持久化层记录后继续。
            failureStage = SyncPhase.VALIDATING;
            stageStartedNanos = logStageStarted(
                    runId, failureStage, snapshot.totalRecords());
            EhrEmployeeValidationResultDTO validationResult =
                    snapshotValidator.validate(snapshot);
            logStageCompleted(runId, failureStage, stageStartedNanos,
                    snapshot.totalRecords(), snapshot.totalRecords(),
                    "validatedRecords=" + validationResult.employees().size()
                            + " validationIssueCount="
                            + validationResult.issues().size());

            failureStage = SyncPhase.EMPLOYEE_UPSERT;
            EhrSyncPromotionResultDTO result =
                    persistenceService.promote(runId, validationResult);
            failureStage = SyncPhase.FINALIZING;
            stageStartedNanos = logStageStarted(runId, failureStage, 1);
            Instant completedAt = clock.instant();
            ehrSyncMapper.updateRunSucceeded(runId, result, completedAt);
            taskLeaseRepository.markSucceeded(lease);
            EhrSyncRunDTO completedRun = ehrSyncMapper.selectRun(runId);
            logStageCompleted(runId, failureStage, stageStartedNanos, 1, 1,
                    "runStatus=" + completedRun.status());
            LOGGER.info(
                    "event=ehr_employee_sync_succeeded runId={} triggerType={} "
                            + "sourceCount={} insertedCount={} updatedCount={} "
                            + "resignedCount={} roleInitializedCount={} issueCount={} "
                            + "status={} startedAt={} "
                            + "completedAt={} durationMs={} thread={}",
                    runId, triggerType, result.fetchedCount(), result.insertedCount(),
                    result.updatedCount(), result.resignedCount(),
                    result.roleInitializedCount(), result.issueCount(),
                    completedRun.status(), startedAt, completedAt,
                    Duration.between(startedAt, completedAt).toMillis(), threadName());
            return completedRun;
        } catch (EhrSyncException ex) {
            Instant failedAt = clock.instant();
            ehrSyncMapper.updateRunFailed(runId, ex.code(), ex.getMessage(), failedAt);
            taskLeaseRepository.markFailedManual(lease, ex);
            LOGGER.warn(
                    "event=ehr_employee_sync_failed runId={} triggerType={} "
                            + "failureStage={} failureCode={} failureReason={} "
                            + "startedAt={} failedAt={} durationMs={} thread={}",
                    runId, triggerType, failureStage, ex.code(), ex.getMessage(),
                    startedAt, failedAt,
                    Duration.between(startedAt, failedAt).toMillis(), threadName(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            Instant failedAt = clock.instant();
            ehrSyncMapper.updateRunFailed(runId, "EHR_SYNC_TECHNICAL_FAILURE",
                    "EHR synchronization failed: " + ex.getClass().getSimpleName(),
                    failedAt);
            taskLeaseRepository.markFailedManual(lease, ex);
            LOGGER.error(
                    "event=ehr_employee_sync_failed runId={} triggerType={} "
                            + "failureStage={} failureCode={} failureReason={} "
                            + "startedAt={} failedAt={} durationMs={} thread={}",
                    runId, triggerType, failureStage, "EHR_SYNC_TECHNICAL_FAILURE",
                    ex.getClass().getSimpleName(), startedAt, failedAt,
                    Duration.between(startedAt, failedAt).toMillis(), threadName(), ex);
            throw ex;
        }
    }

    private PendingRun createPendingRun(
            String triggerType, String idempotencyKey) {
        long runId = idGenerator.next();
        Instant createdAt = clock.instant();
        try {
            ehrSyncMapper.insertRun(new EhrSyncRunCreateDTO(
                    runId, idempotencyKey, triggerType, createdAt));
        } catch (DuplicateKeyException ex) {
            EhrSyncRunDTO existing =
                    ehrSyncMapper.selectRunByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                LOGGER.info(
                        "event=ehr_employee_sync_idempotency_hit runId={} "
                                + "triggerType={} status={} thread={}",
                        existing.id(), triggerType, existing.status(), threadName());
                return new PendingRun(existing, false);
            }
            throw ex;
        }
        return new PendingRun(ehrSyncMapper.selectRun(runId), true);
    }

    private EhrSyncRunDTO findIdempotentRun(
            String triggerType, String idempotencyKey) {
        EhrSyncRunDTO existing =
                ehrSyncMapper.selectRunByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            LOGGER.info(
                    "event=ehr_employee_sync_idempotency_hit runId={} "
                            + "triggerType={} status={} thread={}",
                    existing.id(), triggerType, existing.status(), threadName());
        }
        return existing;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrSyncRunDTO getRun(long runId) {
        return ehrSyncMapper.selectRun(runId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EhrSyncRunDTO> listRuns(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return ehrSyncMapper.selectRecentRuns(safeLimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EhrSyncIssueDTO> listIssues(
            long runId, long afterId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 101));
        return ehrSyncMapper.selectSyncIssues(
                runId, Math.max(0, afterId), safeLimit);
    }

    private long logStageStarted(
            long runId, SyncPhase stage, long totalRecords) {
        LOGGER.info(
                "event=ehr_sync_stage_started runId={} stage={} stageNo={} "
                        + "totalStages=7 totalRecords={} progressPercent=0 thread={}",
                runId, stage, stage.stageNo(), totalRecords, threadName());
        return System.nanoTime();
    }

    private void logStageCompleted(
            long runId,
            SyncPhase stage,
            long startedNanos,
            long processedRecords,
            long totalRecords,
            String details) {
        LOGGER.info(
                "event=ehr_sync_stage_completed runId={} stage={} stageNo={} "
                        + "totalStages=7 processedRecords={} totalRecords={} "
                        + "remainingRecords=0 progressPercent=100 durationMs={} "
                        + "thread={} {}",
                runId, stage, stage.stageNo(), processedRecords, totalRecords,
                elapsedMillis(startedNanos), threadName(), details);
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private String threadName() {
        return Thread.currentThread().getName();
    }

    private record PendingRun(EhrSyncRunDTO run, boolean created) {
    }

    /**
     * 同步失败时标识最后执行阶段，便于区分外部拉取、校验、入库和收尾故障。
     */
    private enum SyncPhase {
        PREPARING(0),
        FETCHING(1),
        VALIDATING(2),
        EMPLOYEE_UPSERT(3),
        FINALIZING(7);

        private final int stageNo;

        SyncPhase(int stageNo) {
            this.stageNo = stageNo;
        }

        private int stageNo() {
            return stageNo;
        }
    }
}
