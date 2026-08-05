package com.biel.lifecamp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.dao.EhrSyncTestMapper;
import com.biel.lifecamp.system.manager.EhrEmployeeManager;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSourceDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncRunDTO;
import com.biel.lifecamp.system.service.EhrSyncService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * EHR 人员全量同步与 MyBatis 持久化集成测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.sentinel.enabled=false", "spring.flyway.enabled=true", "xxl.job.enabled=false",
        "platform.auth.enabled=false", "platform.ehr.enabled=false",
        "platform.ehr.persistence-batch-size=2"
})
@Import(EhrSyncServiceImplIntegrationTest.EhrStubConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class EhrSyncServiceImplIntegrationTest {
    @Autowired
    private EhrSyncService ehrSyncService;
    @Autowired
    private EhrSyncTestMapper testMapper;
    @Autowired
    private StubEhrEmployeeManager ehrManager;

    /**
     * 每项测试从空员工投影开始。
     */
    @BeforeEach
    void setUp() {
        ehrManager.resetBlocking();
        testMapper.deleteRoleAssignments();
        testMapper.deleteWechatProfiles();
        testMapper.deleteExternalIdentities();
        testMapper.deleteLocalCredentials();
        testMapper.deleteSyncIssues();
        testMapper.deleteEmployeeStages();
        testMapper.deleteSyncRuns();
        testMapper.deleteTaskLeases();
        testMapper.deleteEmployees();
        testMapper.resetIntegrationState();
    }

    /**
     * 验证人工提交立即返回待执行运行，实际拉取在专用后台线程继续。
     *
     * @throws Exception 等待后台任务超时或线程中断时抛出
     */
    @Test
    void submittedFullSyncReturnsPendingBeforeBackgroundFetchCompletes()
            throws Exception {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-ASYNC", "E-ASYNC", "异步人员", null)));
        ehrManager.blockNextFetch();

        EhrSyncRunDTO submitted;
        try {
            submitted = ehrSyncService.submitFullSync(
                    "MANUAL", "manual-async-submit");

            assertThat(submitted.status()).isEqualTo("PENDING");
            assertThat(ehrManager.awaitFetchStarted()).isTrue();
            assertThat(ehrManager.fetchThreadName).startsWith("ehr-sync-");
            assertThat(ehrSyncService.getRun(submitted.id()).status())
                    .isEqualTo("RUNNING");
        } finally {
            ehrManager.releaseFetch();
        }

        assertThat(awaitRunStatus(submitted.id(), "SUCCEEDED").status())
                .isEqualTo("SUCCEEDED");
    }

    /**
     * 验证历史库缺少 EHR 集成状态种子数据时，同步会自动补建并开放登录门禁。
     */
    @Test
    void successfulSyncCreatesMissingIntegrationStateAndOpensLoginGate() {
        testMapper.deleteIntegrationState();
        ehrManager.snapshot = snapshot(List.of(
                employee("P-STATE", "E-STATE", "状态自愈人员", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-missing-integration-state");

        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(testMapper.selectInitialSyncCompleted()).isTrue();
    }

    /**
     * 验证 EHR 缺失对账只处理 EHR 来源员工，不会停用本地应急管理员。
     */
    @Test
    void fullSyncDoesNotDisableLocalBootstrapAdministrator() {
        testMapper.insertLocalBootstrapEmployee();
        ehrManager.snapshot = snapshot(List.of(
                employee("P-100", "E100", "经理", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync("MANUAL", "manual-local-admin");

        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.resignedCount()).isZero();
        assertThat(testMapper.selectEmploymentStatusByEmployeeNo("admin"))
                .isEqualTo("ACTIVE");
    }

    /**
     * 验证成功快照生成自增员工主键、解析直属上级并补齐普通角色。
     */
    @Test
    void fullSyncInsertsEmployeesResolvesSupervisorAndInitializesRole() {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-100", "E100", "经理", null),
                employee("P-101", "E101", "员工", "E100"),
                employee("P-102", "E102", "员工二", "E100")));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync("MANUAL", "manual-1");

        assertThat(run.status()).isEqualTo("SUCCEEDED");
        var employees = testMapper.selectEmployees();
        assertThat(employees).hasSize(3);
        assertThat(employees.get(0).id()).isPositive();
        assertThat(employees.get(1).supervisorEmployeeId())
                .isEqualTo(employees.get(0).id());
        assertThat(employees.get(1).professionalTitle()).isEqualTo("工程师");
        assertThat(employees.get(1).jobName()).isEqualTo("工艺工程师");
        assertThat(employees.get(1).positionName()).isEqualTo("高级工程师");
        assertThat(testMapper.countEmployeeRoleAssignments()).isEqualTo(3);
        assertThat(testMapper.selectInitialSyncCompleted()).isTrue();
    }

    /**
     * 验证直属领导和默认角色使用独立、可识别的批量阶段日志。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void fullSyncLogsSeparatedSupervisorAndRoleBatchStages(CapturedOutput output) {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-BATCH-100", "E-BATCH-100", "批量经理", null),
                employee("P-BATCH-101", "E-BATCH-101", "批量员工一", "E-BATCH-100"),
                employee("P-BATCH-102", "E-BATCH-102", "批量员工二", "E-BATCH-100")));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-bulk-enrichment");

        assertThat(run.roleInitializedCount()).isEqualTo(3);
        assertThat(output.getOut())
                .contains("event=ehr_sync_stage_started runId=" + run.id()
                        + " stage=SUPERVISOR_LINKING")
                .contains("event=ehr_sync_stage_progress runId=" + run.id()
                        + " stage=SUPERVISOR_LINKING")
                .contains("event=ehr_sync_stage_started runId=" + run.id()
                        + " stage=DEFAULT_ROLE_INITIALIZING")
                .contains("event=ehr_sync_stage_progress runId=" + run.id()
                        + " stage=DEFAULT_ROLE_INITIALIZING")
                .contains("processedRecords=3")
                .contains("remainingRecords=0")
                .contains("progressPercent=100")
                .doesNotContain("event=ehr_enrichment_batch_committed");
    }

    /**
     * 验证后续完整快照会禁用缺失员工，但不会重复创建员工或角色。
     */
    @Test
    void laterFullSyncUpdatesPresentEmployeeAndDisablesMissingEmployee() {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-100", "E100", "经理", null),
                employee("P-101", "E101", "员工", "E100")));
        ehrSyncService.executeFullSync("MANUAL", "manual-1");

        ehrManager.snapshot = snapshot(List.of(
                employee("P-101", "E101", "员工新姓名", null)));
        EhrSyncRunDTO run = ehrSyncService.executeFullSync("SCHEDULED", "scheduled-2");

        assertThat(run.resignedCount()).isOne();
        var employees = testMapper.selectEmployees();
        assertThat(employees).hasSize(2);
        assertThat(employees.get(0).employmentStatus()).isEqualTo("RESIGNED");
        assertThat(employees.get(0).accountStatus()).isEqualTo("DISABLED");
        assertThat(employees.get(1).displayName()).isEqualTo("员工新姓名");
        assertThat(employees.get(1).employmentStatus()).isEqualTo("ACTIVE");
        assertThat(testMapper.countEmployeeRoleAssignments()).isEqualTo(2);
    }

    /**
     * 验证升级前保存了 EHR 人员标识的员工，升级后可仅凭相同工号继续更新。
     */
    @Test
    void updatesLegacyEmployeeByEmployeeNumberWithoutEhrPersonId() {
        testMapper.insertLegacyEhrEmployee("P-LEGACY", "E100", "旧姓名");
        ehrManager.snapshot = snapshot(List.of(
                employee(null, "E100", "新姓名", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-employee-number-identity");

        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.insertedCount()).isZero();
        assertThat(run.updatedCount()).isOne();
        assertThat(run.issueCount()).isZero();
        assertThat(testMapper.selectEmployees()).singleElement()
                .satisfies(employee -> {
                    assertThat(employee.employeeNo()).isEqualTo("E100");
                    assertThat(employee.displayName()).isEqualTo("新姓名");
                    assertThat(employee.employmentStatus()).isEqualTo("ACTIVE");
                });
    }

    /**
     * 验证空快照失败时不会把已有在职员工批量标记为离职。
     */
    @Test
    void emptySnapshotFailsWithoutChangingEmployees() {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-100", "E100", "经理", null)));
        ehrSyncService.executeFullSync("MANUAL", "manual-1");
        ehrManager.snapshot = new EhrEmployeeSnapshotDTO(0, 0, List.of());

        assertThatThrownBy(() -> ehrSyncService.executeFullSync(
                "SCHEDULED", "scheduled-empty"))
                .hasMessageContaining("empty");

        var employees = testMapper.selectEmployees();
        assertThat(employees).hasSize(1);
        assertThat(employees.getFirst().employmentStatus()).isEqualTo("ACTIVE");
        assertThat(employees.getFirst().accountStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 验证同步日志包含阶段、批次、线程和进度，且不会逐人输出成功日志或个人信息。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void fullSyncLogsPhaseBatchThreadAndProgressWithoutPerEmployeeSuccess(
            CapturedOutput output) {
        ehrManager.snapshot = snapshot(List.of(
                employee("PERSON-LOG-100", "EMPLOYEE-LOG-100",
                        "日志验证姓名", null),
                employee("PERSON-LOG-101", "EMPLOYEE-LOG-101",
                        "日志验证姓名二", null),
                employee("PERSON-LOG-102", "EMPLOYEE-LOG-102",
                        "日志验证姓名三", null)));

        ehrSyncService.executeFullSync("MANUAL", "manual-log-success");

        assertThat(output.getOut())
                .contains("event=ehr_sync_stage_started")
                .contains("stage=FETCHING")
                .contains("stage=VALIDATING")
                .contains("stage=EMPLOYEE_UPSERT")
                .contains("stage=SUPERVISOR_LINKING")
                .contains("stage=DEFAULT_ROLE_INITIALIZING")
                .contains("stage=RECONCILING")
                .contains("stage=FINALIZING")
                .contains("thread=")
                .contains("event=ehr_sync_stage_progress")
                .contains("batchNo=1")
                .contains("batchNo=2")
                .contains("totalBatches=2")
                .contains("processedRecords=3")
                .contains("totalRecords=3")
                .contains("remainingBatches=0")
                .contains("remainingRecords=0")
                .contains("progressPercent=100")
                .doesNotContain("event=ehr_employee_sync_item_succeeded")
                .doesNotContain("日志验证姓名")
                .doesNotContain("13800138000");
    }

    /**
     * 验证失败日志包含失败阶段、稳定错误码、失败原因和失败时间。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void failedSyncLogsFailureStageReasonAndTime(CapturedOutput output) {
        ehrManager.snapshot = new EhrEmployeeSnapshotDTO(0, 0, List.of());

        assertThatThrownBy(() -> ehrSyncService.executeFullSync(
                "MANUAL", "manual-log-failure"))
                .hasMessageContaining("empty");

        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_failed")
                .contains("failureStage=VALIDATING")
                .contains("failureCode=EHR_EMPTY_SNAPSHOT")
                .contains("failureReason=EHR returned an empty full employee snapshot")
                .contains("failedAt=")
                .contains("startedAt=");
    }

    /**
     * 验证单个人员校验失败时记录问题并继续同步其他有效人员，不回滚整批结果。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void invalidEmployeeIsRecordedWithoutRollingBackValidEmployees(CapturedOutput output) {
        EhrEmployeeSourceDTO invalidEmployee =
                withMobile(employee("P-INVALID", "E-INVALID", "错误人员", null),
                        "not-a-phone");
        ehrManager.snapshot = snapshot(List.of(
                employee("P-VALID", "E-VALID", "有效人员", null),
                invalidEmployee));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-partial-success");

        assertThat(run.status()).isEqualTo("PARTIAL_SUCCEEDED");
        assertThat(run.insertedCount()).isOne();
        assertThat(run.issueCount()).isOne();
        assertThat(testMapper.selectEmployees()).hasSize(1);
        assertThat(testMapper.countSyncIssues()).isOne();
        assertThat(testMapper.selectSyncIssues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.employeeNo()).isEqualTo("E-INVALID");
                    assertThat(issue.failureStage()).isEqualTo("VALIDATING");
                    assertThat(issue.issueCode()).isEqualTo("EHR_MOBILE_INVALID");
                });
        assertThat(ehrSyncService.listIssues(run.id(), 0, 10)).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.employeeNo()).isEqualTo("E-INVALID");
                    assertThat(issue.failureStage()).isEqualTo("VALIDATING");
                    assertThat(issue.issueCode()).isEqualTo("EHR_MOBILE_INVALID");
                });
        assertThat(testMapper.selectInitialSyncCompleted()).isTrue();
        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_item_failed")
                .contains("event=ehr_sync_issue_summary")
                .contains("totalIssues=1")
                .contains("validationFailures=1")
                .contains("employeeNoMasked=****ALID")
                .contains("failureStage=VALIDATING")
                .contains("failureReason=EHR snapshot contains an invalid mobile number")
                .doesNotContain("错误人员")
                .doesNotContain("not-a-phone");
    }

    /**
     * 验证来源人员标识变化时，相同工号仍更新同一员工。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void sameEmployeeNumberUpdatesSameEmployeeWhenSourcePersonIdChanges(
            CapturedOutput output) {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-EXISTING", "E-COLLISION", "已有人员", null)));
        ehrSyncService.executeFullSync("MANUAL", "manual-seed-collision");

        ehrManager.snapshot = snapshot(List.of(
                employee("P-DB-CHANGED", "E-COLLISION", "更新人员", null),
                employee("P-DB-VALID", "E-NEW", "正常人员", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-persistence-partial");

        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.insertedCount()).isOne();
        assertThat(run.updatedCount()).isOne();
        assertThat(run.resignedCount()).isZero();
        assertThat(run.issueCount()).isZero();
        assertThat(testMapper.countSyncIssues()).isZero();
        assertThat(testMapper.selectEmployees())
                .extracting(EhrSyncTestMapper.EmployeeProjection::employeeNo)
                .containsExactlyInAnyOrder("E-COLLISION", "E-NEW");
        assertThat(testMapper.selectEmployees())
                .filteredOn(employee -> employee.employeeNo().equals("E-COLLISION"))
                .singleElement()
                .satisfies(employee -> {
                    assertThat(employee.displayName()).isEqualTo("更新人员");
                    assertThat(employee.employmentStatus()).isEqualTo("ACTIVE");
                    assertThat(employee.accountStatus()).isEqualTo("ACTIVE");
                });
        assertThat(output.getOut())
                .contains("event=ehr_sync_stage_progress")
                .contains("stage=EMPLOYEE_UPSERT")
                .doesNotContain("EHR_EMPLOYEE_NO_CONFLICT")
                .doesNotContain("更新人员");
    }

    /**
     * 验证数据库写入异常会回滚到当前人员保存点，后续人员仍可继续提交。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void databaseWriteFailureRollsBackOnlyCurrentEmployee(CapturedOutput output) {
        EhrEmployeeSourceDTO oversizedEmailEmployee = withEmail(
                employee("P-DB-ERROR", "E-DB-ERROR", "落库异常人员", null),
                "x".repeat(257));
        ehrManager.snapshot = snapshot(List.of(
                oversizedEmailEmployee,
                employee("P-AFTER-ERROR", "E-AFTER-ERROR", "后续正常人员", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-savepoint-partial");

        assertThat(run.status()).isEqualTo("PARTIAL_SUCCEEDED");
        assertThat(run.insertedCount()).isOne();
        assertThat(run.issueCount()).isOne();
        assertThat(testMapper.selectEmployees())
                .extracting(EhrSyncTestMapper.EmployeeProjection::ehrPersonId)
                .containsExactly("E-AFTER-ERROR");
        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_item_failed")
                .contains("employeeNoMasked=****RROR")
                .contains("failureStage=PERSISTING")
                .contains("failureCode=EHR_EMPLOYEE_PERSIST_FAILED")
                .doesNotContain("落库异常人员")
                .doesNotContain("x".repeat(257));
    }

    private EhrEmployeeSnapshotDTO snapshot(List<EhrEmployeeSourceDTO> employees) {
        return new EhrEmployeeSnapshotDTO(employees.size(), 1, employees);
    }

    private EhrSyncRunDTO awaitRunStatus(long runId, String expectedStatus)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        EhrSyncRunDTO current = ehrSyncService.getRun(runId);
        while (!expectedStatus.equals(current.status())
                && System.nanoTime() < deadlineNanos) {
            Thread.sleep(10);
            current = ehrSyncService.getRun(runId);
        }
        return current;
    }

    private EhrEmployeeSourceDTO employee(String ehrPersonId, String employeeNo,
                                          String name, String supervisorNo) {
        return new EhrEmployeeSourceDTO(
                ehrPersonId, employeeNo, name, "男", "1990-01-02",
                "13800138000", "employee@example.com", "D-1", "制造一部",
                "C-1", "伯恩公司", supervisorNo, "5", "工程师",
                "J-1", "工艺工程师", "PST-1", "高级工程师",
                "2020-01-01", null, "2026-07-29 10:00:00",
                "2020-01-01 08:00:00");
    }

    private EhrEmployeeSourceDTO withMobile(EhrEmployeeSourceDTO source, String mobile) {
        return new EhrEmployeeSourceDTO(
                source.ehrPersonId(), source.employeeNo(), source.displayName(),
                source.gender(), source.birthday(), mobile, source.email(),
                source.departmentCode(), source.departmentName(),
                source.legalCompanyCode(), source.legalCompanyName(),
                source.supervisorEmployeeNo(), source.jobGrade(),
                source.professionalTitle(), source.jobCode(), source.jobName(),
                source.positionCode(), source.positionName(), source.hireDate(),
                source.terminationDate(), source.modifiedTime(), source.creationTime());
    }

    private EhrEmployeeSourceDTO withEmail(EhrEmployeeSourceDTO source, String email) {
        return new EhrEmployeeSourceDTO(
                source.ehrPersonId(), source.employeeNo(), source.displayName(),
                source.gender(), source.birthday(), source.mobile(), email,
                source.departmentCode(), source.departmentName(),
                source.legalCompanyCode(), source.legalCompanyName(),
                source.supervisorEmployeeNo(), source.jobGrade(),
                source.professionalTitle(), source.jobCode(), source.jobName(),
                source.positionCode(), source.positionName(), source.hireDate(),
                source.terminationDate(), source.modifiedTime(), source.creationTime());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EhrStubConfiguration {
        @Bean
        @Primary
        StubEhrEmployeeManager stubEhrEmployeeManager() {
            return new StubEhrEmployeeManager();
        }
    }

    static final class StubEhrEmployeeManager implements EhrEmployeeManager {
        private EhrEmployeeSnapshotDTO snapshot;
        private volatile CountDownLatch fetchStarted = new CountDownLatch(0);
        private volatile CountDownLatch fetchRelease = new CountDownLatch(0);
        private volatile String fetchThreadName;

        void blockNextFetch() {
            fetchStarted = new CountDownLatch(1);
            fetchRelease = new CountDownLatch(1);
        }

        boolean awaitFetchStarted() throws InterruptedException {
            return fetchStarted.await(5, TimeUnit.SECONDS);
        }

        void releaseFetch() {
            fetchRelease.countDown();
        }

        void resetBlocking() {
            releaseFetch();
            fetchStarted = new CountDownLatch(0);
            fetchRelease = new CountDownLatch(0);
            fetchThreadName = null;
        }

        @Override
        public EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot() {
            fetchThreadName = Thread.currentThread().getName();
            fetchStarted.countDown();
            try {
                if (!fetchRelease.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release EHR fetch");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("EHR fetch was interrupted", exception);
            }
            return snapshot;
        }
    }
}
