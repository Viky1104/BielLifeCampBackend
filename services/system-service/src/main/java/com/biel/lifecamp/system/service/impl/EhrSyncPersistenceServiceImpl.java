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
import com.biel.lifecamp.system.model.dto.EmployeeIdentityDTO;
import com.biel.lifecamp.system.model.dto.EmployeeReferenceDTO;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 使用单个本地事务生效已校验 EHR 人员快照。
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
                                         EhrProperties ehrProperties) {
        this.ehrSyncMapper = ehrSyncMapper;
        this.secretHashing = secretHashing;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.persistenceBatchSize = ehrProperties.getPersistenceBatchSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EhrSyncPromotionResultDTO promote(long runId,
                                              EhrEmployeeValidationResultDTO validationResult) {
        Instant now = clock.instant();
        List<EhrEmployeeUpsertDTO> employees = validationResult.employees();
        List<EhrEmployeeSyncIssueDTO> issues =
                new ArrayList<>(validationResult.issues());
        LOGGER.debug(
                "开始在本地事务中生效 EHR 人员快照，runId={}，employeeCount={}，validationIssueCount={}",
                runId, employees.size(), issues.size());

        /*
         * 预查询使用固定大小 IN 批次，避免全量人员再生成两个大型字段列表，也避免单条
         * SQL 包含数十万个占位符。业务主键由本系统生成，EHR 标识仅作为外部唯一身份。
         */
        Set<String> existingPersonIds = selectExistingPersonIds(employees);
        Map<String, String> personIdsByEmployeeNumber =
                selectPersonIdsByEmployeeNumber(employees);
        List<EhrEmployeeUpsertDTO> persistedEmployees =
                new ArrayList<>(employees.size());
        Set<String> failedPersonIds = new HashSet<>();
        List<EhrEmployeeUpsertDTO> persistenceBatch =
                new ArrayList<>(persistenceBatchSize);

        /*
         * 正常路径先聚合固定数量人员，再用两条批量 SQL 写入 stage 和员工投影。
         * 工号归属冲突在入批前隔离，防止 ON DUPLICATE KEY 静默覆盖其他人员。
         */
        for (EhrEmployeeUpsertDTO employee : employees) {
            String employeeNumberOwner =
                    personIdsByEmployeeNumber.get(employee.employeeNo());
            if (employeeNumberOwner != null
                    && !employeeNumberOwner.equals(employee.ehrPersonId())) {
                /*
                 * ON DUPLICATE KEY 无法区分命中 EHR 人员标识还是工号唯一键，因此必须前置校验归属。
                 * 否则新人员复用旧工号时会静默覆盖旧员工资料，破坏人员身份稳定性。
                 */
                EhrEmployeeSyncIssueDTO issue = new EhrEmployeeSyncIssueDTO(
                        "EHR_EMPLOYEE_NO_CONFLICT", employee.ehrPersonId(),
                        employee.employeeNo(),
                        "EHR employee number is already assigned to another person",
                        "PERSISTING");
                issues.add(issue);
                failedPersonIds.add(employee.ehrPersonId());
                continue;
            }
            persistenceBatch.add(employee);
            if (persistenceBatch.size() == persistenceBatchSize) {
                persistBatchWithFallback(runId, persistenceBatch, now,
                        persistedEmployees, failedPersonIds, issues);
                persistenceBatch.clear();
            }
        }
        if (!persistenceBatch.isEmpty()) {
            persistBatchWithFallback(runId, persistenceBatch, now,
                    persistedEmployees, failedPersonIds, issues);
        }

        /*
         * 直属上级依赖本批次员工主键，因此必须在全部人员 upsert 后进行第二遍解析。
         * 上级工号未命中时写空值，避免遗留上一批次已失效的组织关系。
         */
        List<EmployeeReferenceDTO> references = ehrSyncMapper.selectEmployeeReferences(runId);
        Map<String, Long> employeeIdsByNumber = new HashMap<>(references.size());
        for (EmployeeReferenceDTO reference : references) {
            employeeIdsByNumber.put(reference.employeeNo(), reference.id());
        }
        long roleInitializedCount = 0;
        for (EmployeeReferenceDTO reference : references) {
            Long supervisorId = employeeIdsByNumber.get(reference.supervisorEmployeeNo());
            TransactionStatus transaction =
                    TransactionAspectSupport.currentTransactionStatus();
            Object savepoint = transaction.createSavepoint();
            try {
                ehrSyncMapper.updateSupervisor(reference.id(), supervisorId, now);

                /*
                 * 普通员工角色采用唯一约束和幂等插入初始化；已拥有角色的员工不会重复授权，
                 * 也不会覆盖后续由管理端配置的其他角色和数据范围。
                 */
                roleInitializedCount += ehrSyncMapper.insertEmployeeRoleAssignment(
                        idGenerator.next(), reference.id(),
                        Long.toString(reference.id()), now);
                transaction.releaseSavepoint(savepoint);
            } catch (RuntimeException exception) {
                transaction.rollbackToSavepoint(savepoint);
                transaction.releaseSavepoint(savepoint);
                EhrEmployeeSyncIssueDTO issue = new EhrEmployeeSyncIssueDTO(
                        "EHR_EMPLOYEE_ENRICH_FAILED", reference.ehrPersonId(),
                        reference.employeeNo(),
                        "EHR employee enrichment failed: "
                                + exception.getClass().getSimpleName(),
                        "ENRICHING");
                issues.add(issue);
                failedPersonIds.add(reference.ehrPersonId());
            }
        }

        /*
         * 只要存在任一人员问题，本批次就不能证明快照已完整生效，因此暂停缺失人员离职对账，
         * 也不打开首次同步认证门禁。合法人员仍会提交，问题修正后再次全量同步即可完成对账。
         */
        long resignedCount = 0;
        if (issues.isEmpty()) {
            resignedCount = ehrSyncMapper.markMissingEmployeesResigned(runId, now);
            ehrSyncMapper.completeIntegrationState(now);
        }
        long insertedCount = persistedEmployees.stream()
                .filter(employee -> !existingPersonIds.contains(employee.ehrPersonId()))
                .count();
        long updatedCount = persistedEmployees.size() - insertedCount;
        EhrSyncPromotionResultDTO result = new EhrSyncPromotionResultDTO(
                validationResult.fetchedCount(), insertedCount, updatedCount,
                resignedCount, roleInitializedCount, issues.size());
        List<EhrEmployeeUpsertDTO> successfulEmployees = persistedEmployees.stream()
                .filter(employee -> !failedPersonIds.contains(employee.ehrPersonId()))
                .toList();
        registerPersonnelLogsAfterCommit(
                runId, successfulEmployees, existingPersonIds, issues, now);
        LOGGER.debug(
                "EHR 人员快照事务生效阶段完成，runId={}，insertedCount={}，updatedCount={}，"
                        + "resignedCount={}，roleInitializedCount={}，issueCount={}",
                runId, result.insertedCount(), result.updatedCount(),
                result.resignedCount(), result.roleInitializedCount(),
                result.issueCount());
        return result;
    }

    /**
     * 分批查询同步前已经存在的 EHR 人员标识。
     *
     * @param employees 全量已校验人员
     * @return 同步前已存在人员标识集合
     */
    private Set<String> selectExistingPersonIds(
            List<EhrEmployeeUpsertDTO> employees) {
        if (employees.isEmpty()) {
            return Set.of();
        }
        Set<String> existingPersonIds = new HashSet<>(employees.size());
        for (int fromIndex = 0; fromIndex < employees.size();
             fromIndex += persistenceBatchSize) {
            int toIndex = Math.min(
                    fromIndex + persistenceBatchSize, employees.size());
            List<String> personIds =
                    new ArrayList<>(toIndex - fromIndex);
            for (EhrEmployeeUpsertDTO employee
                    : employees.subList(fromIndex, toIndex)) {
                personIds.add(employee.ehrPersonId());
            }
            existingPersonIds.addAll(
                    ehrSyncMapper.selectExistingEhrPersonIds(personIds));
        }
        return existingPersonIds;
    }

    /**
     * 分批查询工号当前归属，避免生成全量工号副本和超大 IN SQL。
     *
     * @param employees 全量已校验人员
     * @return 工号到 EHR 人员标识的现有归属
     */
    private Map<String, String> selectPersonIdsByEmployeeNumber(
            List<EhrEmployeeUpsertDTO> employees) {
        Map<String, String> personIdsByEmployeeNumber =
                new HashMap<>(employees.size());
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
            for (EmployeeIdentityDTO identity
                    : ehrSyncMapper.selectExistingEmployeeIdentities(
                            employeeNumbers)) {
                personIdsByEmployeeNumber.put(
                        identity.employeeNo(), identity.ehrPersonId());
            }
        }
        return personIdsByEmployeeNumber;
    }

    /**
     * 批量写入人员；批次失败时回滚保存点并逐人重试。
     *
     * <p>批量失败不能直接认定每个人员都失败，因为可能只有一条数据违反数据库约束。
     * 回滚批次 SQL 后，逐人保存点重试可以提交其他合法人员，并产生准确的失败日志。</p>
     *
     * @param runId 同步运行标识
     * @param employees 当前固定大小批次
     * @param syncedAt 同步时间
     * @param persistedEmployees 已成功人员汇总
     * @param failedPersonIds 已失败人员标识
     * @param issues 人员问题汇总
     */
    private void persistBatchWithFallback(
            long runId,
            List<EhrEmployeeUpsertDTO> employees,
            Instant syncedAt,
            List<EhrEmployeeUpsertDTO> persistedEmployees,
            Set<String> failedPersonIds,
            List<EhrEmployeeSyncIssueDTO> issues) {
        List<EhrEmployeePersistItemDTO> items =
                new ArrayList<>(employees.size());
        for (EhrEmployeeUpsertDTO employee : employees) {
            items.add(toPersistItem(employee));
        }

        TransactionStatus transaction =
                TransactionAspectSupport.currentTransactionStatus();
        Object savepoint = transaction.createSavepoint();
        try {
            ehrSyncMapper.insertStageBatch(runId, items);
            ehrSyncMapper.upsertEmployeeBatch(items, syncedAt);
            transaction.releaseSavepoint(savepoint);
            persistedEmployees.addAll(employees);
            LOGGER.debug(
                    "EHR 人员批量写入完成，runId={}，batchSize={}",
                    runId, employees.size());
        } catch (RuntimeException batchException) {
            /*
             * 保存点回滚失败代表主事务已经不可安全继续，rollbackToSavepoint 会直接向上抛出。
             * 日志只记录批次大小和异常类型，不输出 SQL 参数或人员字段。
             */
            transaction.rollbackToSavepoint(savepoint);
            transaction.releaseSavepoint(savepoint);
            LOGGER.warn(
                    "EHR 人员批量写入失败，降级逐人处理，batchSize={}，failureType={}",
                    employees.size(), batchException.getClass().getSimpleName());
            for (EhrEmployeeUpsertDTO employee : employees) {
                persistSingleEmployee(runId, employee, syncedAt,
                        persistedEmployees, failedPersonIds, issues);
            }
        }
    }

    /**
     * 批量失败后的逐人保存点降级路径。
     *
     * @param runId 同步运行标识
     * @param employee 当前人员
     * @param syncedAt 同步时间
     * @param persistedEmployees 已成功人员汇总
     * @param failedPersonIds 已失败人员标识
     * @param issues 人员问题汇总
     */
    private void persistSingleEmployee(
            long runId,
            EhrEmployeeUpsertDTO employee,
            Instant syncedAt,
            List<EhrEmployeeUpsertDTO> persistedEmployees,
            Set<String> failedPersonIds,
            List<EhrEmployeeSyncIssueDTO> issues) {
        EhrEmployeePersistItemDTO item = toPersistItem(employee);
        TransactionStatus transaction =
                TransactionAspectSupport.currentTransactionStatus();
        Object savepoint = transaction.createSavepoint();
        try {
            ehrSyncMapper.insertStage(
                    runId, employee, item.payloadDigest());
            ehrSyncMapper.upsertEmployee(
                    employee, item.mobileHash(), syncedAt);
            transaction.releaseSavepoint(savepoint);
            persistedEmployees.add(employee);
        } catch (RuntimeException exception) {
            transaction.rollbackToSavepoint(savepoint);
            transaction.releaseSavepoint(savepoint);
            issues.add(new EhrEmployeeSyncIssueDTO(
                    "EHR_EMPLOYEE_PERSIST_FAILED", employee.ehrPersonId(),
                    employee.employeeNo(),
                    "EHR employee persistence failed: "
                            + exception.getClass().getSimpleName(),
                    "PERSISTING"));
            failedPersonIds.add(employee.ehrPersonId());
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
                "ehr-stage",
                employee.ehrPersonId() + ":" + employee.employeeNo());
        String mobileHash = employee.mobile() == null ? null
                : secretHashing.identifier("mobile", employee.mobile());
        return new EhrEmployeePersistItemDTO(
                employee, payloadDigest, mobileHash);
    }

    /**
     * 在事务提交成功后记录逐人同步结果，防止回滚事务产生虚假的成功或失败记录。
     *
     * <p>失败人员明细不写数据库，只通过日志保留。人员姓名、手机号、邮箱等个人信息禁止进入日志；
     * personRef 使用不可逆 HMAC，工号仅保留后四位，兼顾故障定位和隐私保护。</p>
     *
     * @param runId 同步运行标识
     * @param employees 已完整生效的人员快照
     * @param existingPersonIds 同步前已存在的 EHR 人员标识
     * @param issues 人员级同步问题
     * @param synchronizedAt 本批人员同步时间
     */
    private void registerPersonnelLogsAfterCommit(
            long runId,
            List<EhrEmployeeUpsertDTO> employees,
            Set<String> existingPersonIds,
            List<EhrEmployeeSyncIssueDTO> issues,
            Instant synchronizedAt) {
        List<EhrEmployeeUpsertDTO> committedEmployees = List.copyOf(employees);
        List<EhrEmployeeSyncIssueDTO> committedIssues = List.copyOf(issues);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (EhrEmployeeUpsertDTO employee : committedEmployees) {
                            String personRef = personReference(
                                    employee.ehrPersonId(), employee.employeeNo());
                            String syncResult = existingPersonIds.contains(
                                    employee.ehrPersonId()) ? "UPDATED" : "INSERTED";
                            LOGGER.info(
                                    "event=ehr_employee_sync_item_succeeded runId={} "
                                            + "personRef={} employeeNoMasked={} "
                                            + "organizationCode={} syncResult={} "
                                            + "synchronizedAt={}",
                                    runId, personRef, maskIdentifier(employee.employeeNo()),
                                    employee.departmentCode(), syncResult, synchronizedAt);
                        }
                        for (EhrEmployeeSyncIssueDTO issue : committedIssues) {
                            LOGGER.warn(
                                    "event=ehr_employee_sync_item_failed runId={} "
                                            + "personRef={} employeeNoMasked={} "
                                            + "syncResult=FAILED failureStage={} "
                                            + "failureCode={} failureReason={} "
                                            + "synchronizedAt={}",
                                    runId, personReference(
                                            issue.ehrPersonId(), issue.employeeNo()),
                                    maskIdentifier(issue.employeeNo()),
                                    issue.failureStage(), issue.issueCode(),
                                    issue.detailDigest(), synchronizedAt);
                        }
                    }
                });
    }

    /**
     * 生成不可逆的人员日志引用；人员标识缺失时仍能得到稳定的未知人员引用。
     *
     * @param ehrPersonId EHR 人员标识
     * @param employeeNo 工号
     * @return 不可逆人员引用
     */
    private String personReference(String ehrPersonId, String employeeNo) {
        return secretHashing.identifier("ehr-stage",
                safeIdentifier(ehrPersonId) + ":" + safeIdentifier(employeeNo));
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
