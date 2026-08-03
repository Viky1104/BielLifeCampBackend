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
     * 验证人员级日志包含脱敏身份、处理结果和同步时间，且不会输出姓名或手机号。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void fullSyncLogsMaskedEmployeeResultAndSynchronizationTime(CapturedOutput output) {
        ehrManager.snapshot = snapshot(List.of(
                employee("PERSON-LOG-100", "EMPLOYEE-LOG-100",
                        "日志验证姓名", null)));

        ehrSyncService.executeFullSync("MANUAL", "manual-log-success");

        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_item_succeeded")
                .contains("employeeNoMasked=****-100")
                .contains("organizationCode=D-1")
                .contains("syncResult=INSERTED")
                .contains("synchronizedAt=")
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
        assertThat(run.issueCount()).isZero();
        assertThat(testMapper.selectEmployees()).hasSize(1);
        assertThat(testMapper.countSyncIssues()).isZero();
        assertThat(testMapper.selectInitialSyncCompleted()).isFalse();
        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_item_failed")
                .contains("employeeNoMasked=****ALID")
                .contains("failureStage=VALIDATING")
                .contains("failureReason=EHR snapshot contains an invalid mobile number")
                .doesNotContain("错误人员")
                .doesNotContain("not-a-phone");
    }

    /**
     * 验证单个人员落库违反唯一约束时只回滚该人员，并暂停本批次离职对账。
     *
     * @param output 当前测试方法捕获的控制台日志
     */
    @Test
    void persistenceFailureIsIsolatedAndDoesNotResignExistingEmployee(
            CapturedOutput output) {
        ehrManager.snapshot = snapshot(List.of(
                employee("P-EXISTING", "E-COLLISION", "已有人员", null)));
        ehrSyncService.executeFullSync("MANUAL", "manual-seed-collision");

        /*
         * 新 EHR 人员复用了数据库中已有工号，触发员工唯一约束；同批另一名人员应正常写入。
         * 因本批存在问题，未出现在快照中的已有人员不能被误判为离职。
         */
        ehrManager.snapshot = snapshot(List.of(
                employee("P-DB-FAIL", "E-COLLISION", "冲突人员", null),
                employee("P-DB-VALID", "E-NEW", "正常人员", null)));

        EhrSyncRunDTO run = ehrSyncService.executeFullSync(
                "MANUAL", "manual-persistence-partial");

        assertThat(run.status()).isEqualTo("PARTIAL_SUCCEEDED");
        assertThat(run.insertedCount()).isOne();
        assertThat(run.resignedCount()).isZero();
        assertThat(run.issueCount()).isZero();
        assertThat(testMapper.countSyncIssues()).isZero();
        assertThat(testMapper.selectEmployees())
                .extracting(EhrSyncTestMapper.EmployeeProjection::ehrPersonId)
                .containsExactlyInAnyOrder("P-EXISTING", "P-DB-VALID");
        assertThat(testMapper.selectEmployees())
                .filteredOn(employee -> employee.ehrPersonId().equals("P-EXISTING"))
                .singleElement()
                .satisfies(employee -> {
                    assertThat(employee.employmentStatus()).isEqualTo("ACTIVE");
                    assertThat(employee.accountStatus()).isEqualTo("ACTIVE");
                });
        assertThat(output.getOut())
                .contains("event=ehr_employee_sync_item_failed")
                .contains("employeeNoMasked=****SION")
                .contains("failureStage=PERSISTING")
                .contains("failureCode=EHR_EMPLOYEE_NO_CONFLICT")
                .contains("failureReason=EHR employee number is already assigned "
                        + "to another person")
                .doesNotContain("冲突人员");
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
        assertThat(run.issueCount()).isZero();
        assertThat(testMapper.selectEmployees())
                .extracting(EhrSyncTestMapper.EmployeeProjection::ehrPersonId)
                .containsExactly("P-AFTER-ERROR");
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

        @Override
        public EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot() {
            return snapshot;
        }
    }
}
