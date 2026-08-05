package com.biel.lifecamp.system.service.impl;

import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.config.properties.EhrProperties;
import com.biel.lifecamp.system.dao.EhrSyncMapper;
import com.biel.lifecamp.system.model.dto.EhrEmployeePersistItemDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSyncIssueDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeUpsertDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeValidationResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncPromotionResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncIssueCreateDTO;
import com.biel.lifecamp.system.model.dto.EmployeeReferenceDTO;
import com.biel.lifecamp.system.model.dto.EmployeeRoleAssignmentCreateDTO;
import com.biel.lifecamp.system.model.dto.EmployeeSupervisorUpdateDTO;
import com.biel.lifecamp.system.service.EhrSyncPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用可观测的独立短事务分批生效已校验 EHR 人员快照。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Service
public class EhrSyncPersistenceServiceImpl implements EhrSyncPersistenceService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EhrSyncPersistenceServiceImpl.class);
    private final EhrSyncMapper ehrSyncMapper;
    private final SecretHashing secretHashing;
    private final LongIdGenerator idGenerator;
    private final Clock clock;
    private final int persistenceBatchSize;
    private final TransactionTemplate shortTransaction;

    /**
     * 创建 EHR 快照原子生效服务。
     *
     * @param ehrSyncMapper 同步与员工投影数据访问对象
     * @param secretHashing 敏感标识摘要组件
     * @param idGenerator 本地主键生成器
     * @param clock 业务时钟
     * @param ehrProperties EHR 固定批量写入配置
     */
    public EhrSyncPersistenceServiceImpl(EhrSyncMapper ehrSyncMapper,
                                         SecretHashing secretHashing,
                                         LongIdGenerator idGenerator,
                                         Clock clock,
                                         EhrProperties ehrProperties,
                                         PlatformTransactionManager transactionManager) {
        this.ehrSyncMapper = ehrSyncMapper;
        this.secretHashing = secretHashing;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.persistenceBatchSize = ehrProperties.getPersistenceBatchSize();
        this.shortTransaction = new TransactionTemplate(transactionManager);
        this.shortTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrSyncPromotionResultDTO promote(long runId,
                                              EhrEmployeeValidationResultDTO validationResult) {
        Instant now = clock.instant();
        List<EhrEmployeeUpsertDTO> employees = validationResult.employees();
        List<EhrEmployeeSyncIssueDTO> issues =
                new ArrayList<>(validationResult.issues());
        String employeeUpsertStage = "EMPLOYEE_UPSERT";
        long employeeUpsertStartedNanos =
                logStageStarted(runId, employeeUpsertStage, 3, employees.size());

        /*
         * 预查询使用固定大小 IN 批次，避免全量人员再生成两个大型字段列表，也避免单条
         * SQL 包含数十万个占位符。业务主键由本系统生成，工号作为 EHR 人员唯一身份。
         */
        Set<String> existingEmployeeNumbers =
                selectExistingEmployeeNumbers(employees);
        List<EhrEmployeeUpsertDTO> persistedEmployees =
                new ArrayList<>(employees.size());
        Set<String> failedEmployeeNumbers = new HashSet<>();
        List<EhrEmployeeUpsertDTO> persistenceBatch =
                new ArrayList<>(persistenceBatchSize);
        int totalBatches = batchCount(employees.size());
        int batchNo = 0;
        int processedRecords = 0;

        /*
         * 正常路径先聚合固定数量人员，再用两条批量 SQL 写入 stage 和员工投影。
         * 校验阶段已经保证批次内工号唯一，数据库工号唯一键负责并发条件下的最终防线。
         */
        for (EhrEmployeeUpsertDTO employee : employees) {
            persistenceBatch.add(employee);
            if (persistenceBatch.size() == persistenceBatchSize) {
                batchNo++;
                persistBatchWithFallback(runId, persistenceBatch, now,
                        persistedEmployees, failedEmployeeNumbers, issues);
                processedRecords += persistenceBatch.size();
                logStageProgress(
                        runId, employeeUpsertStage, batchNo, totalBatches,
                        processedRecords, employees.size(), persistedEmployees.size(),
                        failedEmployeeNumbers.size(), 0, employeeUpsertStartedNanos);
                persistenceBatch.clear();
            }
        }
        if (!persistenceBatch.isEmpty()) {
            batchNo++;
            persistBatchWithFallback(runId, persistenceBatch, now,
                    persistedEmployees, failedEmployeeNumbers, issues);
            processedRecords += persistenceBatch.size();
            logStageProgress(
                    runId, employeeUpsertStage, batchNo, totalBatches,
                    processedRecords, employees.size(), persistedEmployees.size(),
                    failedEmployeeNumbers.size(), 0, employeeUpsertStartedNanos);
        }
        logStageCompleted(
                runId, employeeUpsertStage, employees.size(),
                persistedEmployees.size(), failedEmployeeNumbers.size(), 0,
                employeeUpsertStartedNanos);

        /*
         * 直属上级依赖本批次员工主键，因此必须在全部人员 upsert 后进行第二遍解析。
         * 上级工号未命中时写空值，避免遗留上一批次已失效的组织关系。
         */
        List<EmployeeReferenceDTO> references = ehrSyncMapper.selectEmployeeReferences(runId);
        Map<String, Long> employeeIdsByNumber = new HashMap<>(references.size());
        for (EmployeeReferenceDTO reference : references) {
            employeeIdsByNumber.put(reference.employeeNo(), reference.id());
        }
        linkSupervisorsInShortTransactions(
                runId, references, employeeIdsByNumber, now,
                issues, failedEmployeeNumbers);
        long roleInitializedCount = initializeDefaultRolesInShortTransactions(
                runId, references.size(), now, issues, failedEmployeeNumbers);

        /*
         * 人员级问题只影响对应员工，不得阻断已经成功投影人员登录。至少存在一名本次成功
         * 投影的员工时，幂等打开首次同步登录门禁；状态表缺少种子行时由 Mapper 自动补建。
         */
        if (!references.isEmpty()) {
            shortTransaction.executeWithoutResult(
                    status -> ehrSyncMapper.completeIntegrationState(now));
            LOGGER.info(
                    "event=ehr_login_gate_opened runId={} projectedEmployeeCount={} "
                            + "issueCount={} thread={}",
                    runId, references.size(), issues.size(), threadName());
        }

        /*
         * 只要存在任一人员问题，本批次就不能证明快照已完整生效，因此暂停缺失人员离职对账。
         * 合法人员仍可登录，问题修正后再次全量同步即可完成对账。
         */
        long resignedCount = 0;
        String reconciliationStage = "RECONCILING";
        long reconciliationStartedNanos =
                logStageStarted(runId, reconciliationStage, 6, employees.size());
        if (issues.isEmpty()) {
            Integer reconciled = shortTransaction.execute(status -> {
                return ehrSyncMapper.markMissingEmployeesResigned(runId, now);
            });
            resignedCount = reconciled == null ? 0 : reconciled;
            LOGGER.info(
                    "event=ehr_reconciliation_result runId={} resignedCount={} "
                            + "durationMs={} thread={}",
                    runId, resignedCount,
                    elapsedMillis(reconciliationStartedNanos), threadName());
            logStageProgress(
                    runId, reconciliationStage, 1, 1,
                    employees.size(), employees.size(), employees.size(),
                    0, 0, reconciliationStartedNanos);
            logStageCompleted(
                    runId, reconciliationStage, employees.size(),
                    employees.size(), 0, 0, reconciliationStartedNanos);
        } else {
            LOGGER.warn(
                    "event=ehr_reconciliation_skipped runId={} issueCount={} reason={} thread={}",
                    runId, issues.size(), "PERSONNEL_ISSUES_PRESENT", threadName());
            logStageProgress(
                    runId, reconciliationStage, 1, 1,
                    employees.size(), employees.size(), 0,
                    0, employees.size(), reconciliationStartedNanos);
            logStageCompleted(
                    runId, reconciliationStage, employees.size(),
                    0, 0, employees.size(), reconciliationStartedNanos);
        }
        long insertedCount = persistedEmployees.stream()
                .filter(employee -> !existingEmployeeNumbers.contains(employee.employeeNo()))
                .count();
        long updatedCount = persistedEmployees.size() - insertedCount;
        persistPersonnelIssues(runId, issues, now);
        EhrSyncPromotionResultDTO result = new EhrSyncPromotionResultDTO(
                validationResult.fetchedCount(), insertedCount, updatedCount,
                resignedCount, roleInitializedCount, issues.size());
        logPersonnelIssues(runId, issues, now);
        logIssueSummary(runId, issues);
        LOGGER.info(
                "event=ehr_persistence_completed runId={} insertedCount={} updatedCount={} "
                        + "resignedCount={} roleInitializedCount={} issueCount={} thread={}",
                runId, result.insertedCount(), result.updatedCount(),
                result.resignedCount(), result.roleInitializedCount(),
                result.issueCount(), threadName());
        return result;
    }

    /**
     * 分批查询同步前已经存在的员工工号。
     *
     * @param employees 全量已校验人员
     * @return 同步前已存在工号集合
     */
    private Set<String> selectExistingEmployeeNumbers(
            List<EhrEmployeeUpsertDTO> employees) {
        if (employees.isEmpty()) {
            return Set.of();
        }
        Set<String> existingEmployeeNumbers = new HashSet<>(employees.size());
        for (int fromIndex = 0; fromIndex < employees.size();
             fromIndex += persistenceBatchSize) {
            int toIndex = Math.min(
                    fromIndex + persistenceBatchSize, employees.size());
            List<String> employeeNumbers =
                    new ArrayList<>(toIndex - fromIndex);
            for (EhrEmployeeUpsertDTO employee
                    : employees.subList(fromIndex, toIndex)) {
                employeeNumbers.add(employee.employeeNo());
            }
            existingEmployeeNumbers.addAll(
                    ehrSyncMapper.selectExistingEmployeeNumbers(employeeNumbers));
        }
        return existingEmployeeNumbers;
    }

    /**
     * 在独立短事务中批量写入人员；批次失败时逐人使用短事务重试。
     *
     * <p>批量失败不能直接认定每个人员都失败，因为可能只有一条数据违反数据库约束。
     * 整批事务回滚后逐人重试，可以提交其他合法人员并产生准确的失败日志。</p>
     *
     * @param runId 同步运行标识
     * @param employees 当前固定大小批次
     * @param syncedAt 同步时间
     * @param persistedEmployees 已成功人员汇总
     * @param failedEmployeeNumbers 已失败人员工号
     * @param issues 人员问题汇总
     */
    private void persistBatchWithFallback(
            long runId,
            List<EhrEmployeeUpsertDTO> employees,
            Instant syncedAt,
            List<EhrEmployeeUpsertDTO> persistedEmployees,
            Set<String> failedEmployeeNumbers,
            List<EhrEmployeeSyncIssueDTO> issues) {
        List<EhrEmployeePersistItemDTO> items =
                new ArrayList<>(employees.size());
        for (EhrEmployeeUpsertDTO employee : employees) {
            items.add(toPersistItem(employee));
        }

        try {
            shortTransaction.executeWithoutResult(status -> {
                ehrSyncMapper.insertStageBatch(runId, items);
                ehrSyncMapper.upsertEmployeeBatch(items, syncedAt);
            });
            persistedEmployees.addAll(employees);
        } catch (RuntimeException batchException) {
            LOGGER.warn(
                    "event=ehr_persistence_batch_degraded runId={} batchSize={} "
                            + "failureType={} fallback=SINGLE_RECORD_TRANSACTION thread={}",
                    runId, employees.size(), batchException.getClass().getSimpleName(),
                    threadName());
            for (EhrEmployeeUpsertDTO employee : employees) {
                persistSingleEmployee(runId, employee, syncedAt,
                        persistedEmployees, failedEmployeeNumbers, issues);
            }
        }
    }

    /**
     * 批量失败后的逐人短事务降级路径。
     *
     * @param runId 同步运行标识
     * @param employee 当前人员
     * @param syncedAt 同步时间
     * @param persistedEmployees 已成功人员汇总
     * @param failedEmployeeNumbers 已失败人员工号
     * @param issues 人员问题汇总
     */
    private void persistSingleEmployee(
            long runId,
            EhrEmployeeUpsertDTO employee,
            Instant syncedAt,
            List<EhrEmployeeUpsertDTO> persistedEmployees,
            Set<String> failedEmployeeNumbers,
            List<EhrEmployeeSyncIssueDTO> issues) {
        EhrEmployeePersistItemDTO item = toPersistItem(employee);
        try {
            shortTransaction.executeWithoutResult(status -> {
                ehrSyncMapper.insertStage(
                        runId, employee, item.payloadDigest());
                ehrSyncMapper.upsertEmployee(
                        employee, item.mobileHash(), syncedAt);
            });
            persistedEmployees.add(employee);
        } catch (RuntimeException exception) {
            issues.add(new EhrEmployeeSyncIssueDTO(
                    "EHR_EMPLOYEE_PERSIST_FAILED", employee.employeeNo(),
                    employee.employeeNo(),
                    "EHR employee persistence failed: "
                            + exception.getClass().getSimpleName(),
                    "PERSISTING"));
            failedEmployeeNumbers.add(employee.employeeNo());
        }
    }

    private void linkSupervisorsInShortTransactions(
            long runId,
            List<EmployeeReferenceDTO> references,
            Map<String, Long> employeeIdsByNumber,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        String stage = "SUPERVISOR_LINKING";
        long stageStartedNanos = logStageStarted(runId, stage, 4, references.size());
        int totalBatches = batchCount(references.size());
        int successfulRecords = 0;
        int issueCountAtStart = issues.size();
        for (int fromIndex = 0, batchNo = 1;
             fromIndex < references.size();
             fromIndex += persistenceBatchSize, batchNo++) {
            int toIndex = Math.min(
                    fromIndex + persistenceBatchSize, references.size());
            List<EmployeeReferenceDTO> batch = references.subList(fromIndex, toIndex);
            int issueCountBefore = issues.size();
            linkSupervisorBatchWithFallback(
                    runId, batch, employeeIdsByNumber, synchronizedAt,
                    issues, failedEmployeeNumbers);
            int failedInBatch = issues.size() - issueCountBefore;
            successfulRecords += batch.size() - failedInBatch;
            logStageProgress(
                    runId, stage, batchNo, totalBatches, toIndex, references.size(),
                    successfulRecords, issues.size() - issueCountAtStart, 0,
                    stageStartedNanos);
        }
        logStageCompleted(
                runId, stage, references.size(), successfulRecords,
                issues.size() - issueCountAtStart, 0, stageStartedNanos);
    }

    private void linkSupervisorBatchWithFallback(
            long runId,
            List<EmployeeReferenceDTO> references,
            Map<String, Long> employeeIdsByNumber,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        List<EmployeeSupervisorUpdateDTO> items = new ArrayList<>(references.size());
        for (EmployeeReferenceDTO reference : references) {
            items.add(new EmployeeSupervisorUpdateDTO(
                    reference.id(),
                    employeeIdsByNumber.get(reference.supervisorEmployeeNo())));
        }
        try {
            shortTransaction.executeWithoutResult(
                    status -> ehrSyncMapper.updateSupervisorBatch(items, synchronizedAt));
        } catch (RuntimeException batchException) {
            LOGGER.warn(
                    "event=ehr_supervisor_linking_batch_degraded runId={} batchSize={} "
                            + "failureType={} fallback=SINGLE_RECORD_TRANSACTION thread={}",
                    runId, references.size(), batchException.getClass().getSimpleName(),
                    threadName());
            for (EmployeeReferenceDTO reference : references) {
                linkSingleSupervisor(
                        reference, employeeIdsByNumber, synchronizedAt,
                        issues, failedEmployeeNumbers);
            }
        }
    }

    private void linkSingleSupervisor(
            EmployeeReferenceDTO reference,
            Map<String, Long> employeeIdsByNumber,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        try {
            shortTransaction.executeWithoutResult(status -> ehrSyncMapper.updateSupervisor(
                    reference.id(),
                    employeeIdsByNumber.get(reference.supervisorEmployeeNo()),
                    synchronizedAt));
        } catch (RuntimeException exception) {
            issues.add(new EhrEmployeeSyncIssueDTO(
                    "EHR_SUPERVISOR_LINK_FAILED", reference.employeeNo(),
                    reference.employeeNo(),
                    "EHR supervisor linking failed: "
                            + exception.getClass().getSimpleName(),
                    "SUPERVISOR_LINKING"));
            failedEmployeeNumbers.add(reference.employeeNo());
        }
    }

    private long initializeDefaultRolesInShortTransactions(
            long runId,
            int totalRecords,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        String stage = "DEFAULT_ROLE_INITIALIZING";
        long stageStartedNanos = logStageStarted(runId, stage, 5, totalRecords);
        Long roleId = ehrSyncMapper.selectRoleId("EMPLOYEE");
        if (roleId == null) {
            throw new IllegalStateException("Default EMPLOYEE role is not configured");
        }
        List<EmployeeReferenceDTO> missingEmployees =
                ehrSyncMapper.selectEmployeesMissingRole(runId, "EMPLOYEE");
        int alreadyAssigned = totalRecords - missingEmployees.size();
        int totalBatches = batchCount(missingEmployees.size());
        long initializedCount = 0;
        int issueCountAtStart = issues.size();
        if (missingEmployees.isEmpty()) {
            logStageProgress(
                    runId, stage, 0, 0, totalRecords, totalRecords,
                    0, 0, alreadyAssigned, stageStartedNanos);
        }
        for (int fromIndex = 0, batchNo = 1;
             fromIndex < missingEmployees.size();
             fromIndex += persistenceBatchSize, batchNo++) {
            int toIndex = Math.min(
                    fromIndex + persistenceBatchSize, missingEmployees.size());
            List<EmployeeReferenceDTO> batch =
                    missingEmployees.subList(fromIndex, toIndex);
            initializedCount += initializeRoleBatchWithFallback(
                    runId, batch, roleId, synchronizedAt,
                    issues, failedEmployeeNumbers);
            int processedRecords = alreadyAssigned + toIndex;
            logStageProgress(
                    runId, stage, batchNo, totalBatches, processedRecords, totalRecords,
                    (int) initializedCount, issues.size() - issueCountAtStart,
                    alreadyAssigned,
                    stageStartedNanos);
        }
        logStageCompleted(
                runId, stage, totalRecords, (int) initializedCount,
                issues.size() - issueCountAtStart,
                alreadyAssigned, stageStartedNanos);
        return initializedCount;
    }

    private long initializeRoleBatchWithFallback(
            long runId,
            List<EmployeeReferenceDTO> employees,
            long roleId,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        List<EmployeeRoleAssignmentCreateDTO> items =
                new ArrayList<>(employees.size());
        for (EmployeeReferenceDTO employee : employees) {
            items.add(new EmployeeRoleAssignmentCreateDTO(
                    idGenerator.next(), employee.id(), roleId,
                    Long.toString(employee.id())));
        }
        try {
            Integer initialized = shortTransaction.execute(
                    status -> ehrSyncMapper.insertEmployeeRoleAssignmentBatch(
                            items, synchronizedAt));
            return initialized == null ? 0 : initialized;
        } catch (RuntimeException batchException) {
            LOGGER.warn(
                    "event=ehr_default_role_batch_degraded runId={} batchSize={} "
                            + "failureType={} fallback=SINGLE_RECORD_TRANSACTION thread={}",
                    runId, employees.size(), batchException.getClass().getSimpleName(),
                    threadName());
            long initialized = 0;
            for (EmployeeReferenceDTO employee : employees) {
                initialized += initializeSingleRole(
                        employee, synchronizedAt, issues, failedEmployeeNumbers);
            }
            return initialized;
        }
    }

    private long initializeSingleRole(
            EmployeeReferenceDTO employee,
            Instant synchronizedAt,
            List<EhrEmployeeSyncIssueDTO> issues,
            Set<String> failedEmployeeNumbers) {
        try {
            Integer initialized = shortTransaction.execute(
                    status -> ehrSyncMapper.insertEmployeeRoleAssignment(
                            idGenerator.next(), employee.id(),
                            Long.toString(employee.id()), synchronizedAt));
            return initialized == null ? 0 : initialized;
        } catch (RuntimeException exception) {
            issues.add(new EhrEmployeeSyncIssueDTO(
                    "EHR_DEFAULT_ROLE_INITIALIZATION_FAILED",
                    employee.employeeNo(), employee.employeeNo(),
                    "EHR default role initialization failed: "
                            + exception.getClass().getSimpleName(),
                    "DEFAULT_ROLE_INITIALIZING"));
            failedEmployeeNumbers.add(employee.employeeNo());
            return 0;
        }
    }

    /**
     * 计算单个人员批量写入所需的脱敏摘要。
     *
     * @param employee 已校验人员
     * @return 临时批量持久化参数
     */
    private EhrEmployeePersistItemDTO toPersistItem(
            EhrEmployeeUpsertDTO employee) {
        String payloadDigest = secretHashing.identifier(
                "ehr-stage", employee.employeeNo());
        String mobileHash = employee.mobile() == null ? null
                : secretHashing.identifier("mobile", employee.mobile());
        return new EhrEmployeePersistItemDTO(
                employee, payloadDigest, mobileHash);
    }

    private long logStageStarted(
            long runId, String stage, int stageNo, int totalRecords) {
        LOGGER.info(
                "event=ehr_sync_stage_started runId={} stage={} stageNo={} "
                        + "totalStages=7 totalRecords={} progressPercent=0 thread={}",
                runId, stage, stageNo, totalRecords, threadName());
        return System.nanoTime();
    }

    private void logStageProgress(
            long runId,
            String stage,
            int batchNo,
            int totalBatches,
            int processedRecords,
            int totalRecords,
            int successfulRecords,
            int failedRecords,
            int skippedRecords,
            long stageStartedNanos) {
        long elapsedMs = Math.max(1, elapsedMillis(stageStartedNanos));
        long recordsPerSecond = processedRecords * 1_000L / elapsedMs;
        int remainingRecords = remaining(processedRecords, totalRecords);
        long estimatedRemainingSeconds = recordsPerSecond == 0
                ? -1 : (remainingRecords + recordsPerSecond - 1) / recordsPerSecond;
        LOGGER.info(
                "event=ehr_sync_stage_progress runId={} stage={} batchNo={} "
                        + "totalBatches={} remainingBatches={} processedRecords={} "
                        + "totalRecords={} remainingRecords={} successfulRecords={} "
                        + "failedRecords={} skippedRecords={} recordsPerSecond={} "
                        + "elapsedMs={} estimatedRemainingSeconds={} progressPercent={} "
                        + "transactionMode=REQUIRES_NEW thread={}",
                runId, stage, batchNo, totalBatches,
                remaining(batchNo, totalBatches), processedRecords, totalRecords,
                remainingRecords, successfulRecords, failedRecords, skippedRecords,
                recordsPerSecond, elapsedMs, estimatedRemainingSeconds,
                progressPercent(processedRecords, totalRecords), threadName());
    }

    private void logStageCompleted(
            long runId,
            String stage,
            int totalRecords,
            int successfulRecords,
            int failedRecords,
            int skippedRecords,
            long stageStartedNanos) {
        LOGGER.info(
                "event=ehr_sync_stage_completed runId={} stage={} processedRecords={} "
                        + "totalRecords={} remainingRecords=0 successfulRecords={} "
                        + "failedRecords={} skippedRecords={} progressPercent=100 "
                        + "durationMs={} thread={}",
                runId, stage, totalRecords, totalRecords, successfulRecords,
                failedRecords, skippedRecords, elapsedMillis(stageStartedNanos),
                threadName());
    }

    private void logPersonnelIssues(long runId,
                                    List<EhrEmployeeSyncIssueDTO> issues,
                                    Instant synchronizedAt) {
        for (EhrEmployeeSyncIssueDTO issue : issues) {
            LOGGER.warn(
                    "event=ehr_employee_sync_item_failed runId={} personRef={} "
                            + "employeeNoMasked={} syncResult=FAILED failureStage={} "
                            + "failureCode={} failureReason={} synchronizedAt={} thread={}",
                    runId, personReference(issue.employeeNo()),
                    maskIdentifier(issue.employeeNo()), issue.failureStage(),
                    issue.issueCode(), issue.detailDigest(), synchronizedAt, threadName());
        }
    }

    private void persistPersonnelIssues(
            long runId,
            List<EhrEmployeeSyncIssueDTO> issues,
            Instant synchronizedAt) {
        for (int fromIndex = 0;
             fromIndex < issues.size();
             fromIndex += persistenceBatchSize) {
            int toIndex = Math.min(
                    fromIndex + persistenceBatchSize, issues.size());
            List<EhrSyncIssueCreateDTO> batch =
                    new ArrayList<>(toIndex - fromIndex);
            for (EhrEmployeeSyncIssueDTO issue
                    : issues.subList(fromIndex, toIndex)) {
                batch.add(new EhrSyncIssueCreateDTO(
                        idGenerator.next(), runId, "ERROR", issue.issueCode(),
                        issue.employeeNo(), issue.failureStage(),
                        issue.detailDigest(), synchronizedAt));
            }
            shortTransaction.executeWithoutResult(
                    status -> ehrSyncMapper.insertSyncIssueBatch(batch));
        }
    }

    private void logIssueSummary(
            long runId, List<EhrEmployeeSyncIssueDTO> issues) {
        long validationFailures = countIssues(issues, "VALIDATING");
        long persistenceFailures = countIssues(issues, "PERSISTING");
        long supervisorFailures = countIssues(issues, "SUPERVISOR_LINKING");
        long roleFailures = countIssues(issues, "DEFAULT_ROLE_INITIALIZING");
        LOGGER.info(
                "event=ehr_sync_issue_summary runId={} totalIssues={} "
                        + "validationFailures={} persistenceFailures={} "
                        + "supervisorFailures={} roleFailures={} thread={}",
                runId, issues.size(), validationFailures, persistenceFailures,
                supervisorFailures, roleFailures, threadName());
    }

    private long countIssues(
            List<EhrEmployeeSyncIssueDTO> issues, String failureStage) {
        return issues.stream()
                .filter(issue -> failureStage.equals(issue.failureStage()))
                .count();
    }

    private int batchCount(int totalRecords) {
        return totalRecords == 0 ? 0
                : (totalRecords + persistenceBatchSize - 1) / persistenceBatchSize;
    }

    private int progressPercent(int completed, int total) {
        return total < 1 ? 0 : Math.min(100, (int) ((long) completed * 100 / total));
    }

    private int remaining(int completed, int total) {
        return Math.max(0, total - completed);
    }

    private long elapsedMillis(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos);
    }

    private String threadName() {
        return Thread.currentThread().getName();
    }

    /**
     * 生成不可逆的人员日志引用；人员标识缺失时仍能得到稳定的未知人员引用。
     *
     * @param employeeNo 工号
     * @return 不可逆人员引用
     */
    private String personReference(String employeeNo) {
        return secretHashing.identifier("ehr-stage", safeIdentifier(employeeNo));
    }

    private String safeIdentifier(String identifier) {
        return identifier == null || identifier.isBlank() ? "UNKNOWN" : identifier;
    }

    /**
     * 脱敏人员工号，仅保留最后四个字符用于运维对账。
     *
     * @param identifier 原始人员标识
     * @return 不暴露完整标识的日志值
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "****UNKNOWN";
        }
        int visibleLength = Math.min(4, identifier.length());
        return "****" + identifier.substring(identifier.length() - visibleLength);
    }
}
