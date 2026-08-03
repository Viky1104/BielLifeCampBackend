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
     */
    public EhrSyncServiceImpl(EhrEmployeeManager ehrEmployeeManager,
                              EhrEmployeeSnapshotValidator snapshotValidator,
                              EhrSyncPersistenceService persistenceService,
                              EhrSyncMapper ehrSyncMapper,
                              LongIdGenerator idGenerator,
                              Clock clock,
                              TaskLeaseRepository taskLeaseRepository) {
        this.ehrEmployeeManager = ehrEmployeeManager;
        this.snapshotValidator = snapshotValidator;
        this.persistenceService = persistenceService;
        this.ehrSyncMapper = ehrSyncMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.taskLeaseRepository = taskLeaseRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrSyncRunDTO executeFullSync(String triggerType, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }

        /*
         * 先查询幂等运行，确保 ACK 多副本、调度重试和人工重试返回同一业务结果。
         * 幂等键不写日志，避免外部输入污染日志，也避免泄露发布批次命名细节。
         */
        EhrSyncRunDTO existing = ehrSyncMapper.selectRunByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            LOGGER.info("EHR 人员全量同步命中幂等运行，runId={}，triggerType={}，status={}",
                    existing.id(), triggerType, existing.status());
            return existing;
        }

        /*
         * 租约覆盖整个“拉取、校验、生效”过程。租约竞争失败表示已有实例执行，
         * 调用方应查询已有运行，不得把它当成可立即重试的普通技术失败。
         */
        TaskLease lease = taskLeaseRepository.tryAcquire(TASK_TYPE, LEASE_DURATION)
                .orElseThrow(() -> new EhrSyncException(
                        "EHR_SYNC_ALREADY_RUNNING", "An EHR synchronization is already running"));
        long runId = idGenerator.next();
        Instant startedAt = clock.instant();
        SyncPhase failureStage = SyncPhase.PREPARING;
        LOGGER.info(
                "event=ehr_employee_sync_started runId={} triggerType={} startedAt={} "
                        + "leaseDurationSeconds={}",
                runId, triggerType, startedAt, LEASE_DURATION.toSeconds());
        try {
            // 先持久化运行事实，后续每个失败分支都能按 runId 追踪和人工对账。
            ehrSyncMapper.insertRun(new EhrSyncRunCreateDTO(
                    runId, idempotencyKey, triggerType, startedAt));
            ehrSyncMapper.updateRunRunning(runId, startedAt);

            failureStage = SyncPhase.FETCHING;
            EhrEmployeeSnapshotDTO snapshot =
                    ehrEmployeeManager.fetchActiveEmployeeSnapshot();
            LOGGER.info("EHR 人员全量快照拉取完成，runId={}，declaredRecords={}，totalPages={}",
                    runId, snapshot.totalRecords(), snapshot.totalPages());

            // 快照完整性仍是批次门禁；单个人员字段问题则交给持久化层记录后继续。
            failureStage = SyncPhase.VALIDATING;
            EhrEmployeeValidationResultDTO validationResult =
                    snapshotValidator.validate(snapshot);
            LOGGER.info(
                    "EHR 人员全量快照校验完成，runId={}，validatedRecords={}，validationIssueCount={}",
                    runId, validationResult.employees().size(),
                    validationResult.issues().size());

            failureStage = SyncPhase.PROMOTING;
            EhrSyncPromotionResultDTO result =
                    persistenceService.promote(runId, validationResult);
            failureStage = SyncPhase.FINALIZING;
            Instant completedAt = clock.instant();
            ehrSyncMapper.updateRunSucceeded(runId, result, completedAt);
            taskLeaseRepository.markSucceeded(lease);
            EhrSyncRunDTO completedRun = ehrSyncMapper.selectRun(runId);
            LOGGER.info(
                    "event=ehr_employee_sync_succeeded runId={} triggerType={} "
                            + "sourceCount={} insertedCount={} updatedCount={} "
                            + "resignedCount={} roleInitializedCount={} issueCount={} "
                            + "status={} startedAt={} "
                            + "completedAt={} durationMs={}",
                    runId, triggerType, result.fetchedCount(), result.insertedCount(),
                    result.updatedCount(), result.resignedCount(),
                    result.roleInitializedCount(), result.issueCount(),
                    completedRun.status(), startedAt, completedAt,
                    Duration.between(startedAt, completedAt).toMillis());
            return completedRun;
        } catch (EhrSyncException ex) {
            Instant failedAt = clock.instant();
            ehrSyncMapper.updateRunFailed(runId, ex.code(), ex.getMessage(), failedAt);
            taskLeaseRepository.markFailedManual(lease, ex);
            LOGGER.warn(
                    "event=ehr_employee_sync_failed runId={} triggerType={} "
                            + "failureStage={} failureCode={} failureReason={} "
                            + "startedAt={} failedAt={} durationMs={}",
                    runId, triggerType, failureStage, ex.code(), ex.getMessage(),
                    startedAt, failedAt,
                    Duration.between(startedAt, failedAt).toMillis(), ex);
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
                            + "startedAt={} failedAt={} durationMs={}",
                    runId, triggerType, failureStage, "EHR_SYNC_TECHNICAL_FAILURE",
                    ex.getClass().getSimpleName(), startedAt, failedAt,
                    Duration.between(startedAt, failedAt).toMillis(), ex);
            throw ex;
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
     * 同步失败时标识最后执行阶段，便于区分外部拉取、校验、入库和收尾故障。
     */
    private enum SyncPhase {
        PREPARING,
        FETCHING,
        VALIDATING,
        PROMOTING,
        FINALIZING
    }
}
