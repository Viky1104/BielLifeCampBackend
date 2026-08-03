package com.biel.lifecamp.system.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.common.exception.EhrSyncException;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSourceDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * EHR 人员全量快照校验测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
class EhrEmployeeSnapshotValidatorTest {
    private final EhrEmployeeSnapshotValidator validator = new EhrEmployeeSnapshotValidator();

    /**
     * 验证中文性别值会转换为本地标准枚举，同时保留源值。
     */
    @Test
    void normalizesGenderAndKeepsSourceValue() {
        EhrEmployeeSnapshotDTO snapshot = new EhrEmployeeSnapshotDTO(
                1, 1, List.of(employee("P-1", "E-1", "张三", "男")));

        var employee = validator.validate(snapshot).employees().getFirst();

        assertThat(employee.genderCode()).isEqualTo("MALE");
        assertThat(employee.genderSourceValue()).isEqualTo("男");
        assertThat(employee.mobile()).isEqualTo("+8613800138000");
    }

    /**
     * 验证快照记录数与 EHR 声明总数不一致时整批拒绝。
     */
    @Test
    void rejectsRecordCountMismatch() {
        EhrEmployeeSnapshotDTO snapshot = new EhrEmployeeSnapshotDTO(
                2, 1, List.of(employee("P-1", "E-1", "张三", "男")));

        assertThatThrownBy(() -> validator.validate(snapshot))
                .isInstanceOf(EhrSyncException.class)
                .hasMessageContaining("record count");
    }

    /**
     * 验证空快照不会被当作有效全量结果，避免误将全部员工标记为离职。
     */
    @Test
    void rejectsEmptySnapshot() {
        EhrEmployeeSnapshotDTO snapshot = new EhrEmployeeSnapshotDTO(0, 0, List.of());

        assertThatThrownBy(() -> validator.validate(snapshot))
                .isInstanceOf(EhrSyncException.class)
                .hasMessageContaining("empty");
    }

    /**
     * 验证重复 EHR 稳定人员标识只隔离重复人员。
     */
    @Test
    void isolatesDuplicateEhrPersonId() {
        EhrEmployeeSnapshotDTO snapshot = new EhrEmployeeSnapshotDTO(
                2, 1, List.of(
                        employee("P-1", "E-1", "张三", "男"),
                        employee("P-1", "E-2", "李四", "女")));

        var result = validator.validate(snapshot);

        assertThat(result.employees()).hasSize(1);
        assertThat(result.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.issueCode()).isEqualTo("EHR_DUPLICATE_IDENTITY");
                    assertThat(issue.employeeNo()).isEqualTo("E-2");
                    assertThat(issue.detailDigest()).contains("ehr person id");
                });
    }

    /**
     * 验证缺失工号等关键字段时隔离问题人员。
     */
    @Test
    void isolatesMissingRequiredEmployeeNumber() {
        EhrEmployeeSnapshotDTO snapshot = new EhrEmployeeSnapshotDTO(
                1, 1, List.of(employee("P-1", " ", "张三", "男")));

        var result = validator.validate(snapshot);

        assertThat(result.employees()).isEmpty();
        assertThat(result.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.issueCode())
                            .isEqualTo("EHR_REQUIRED_FIELD_MISSING");
                    assertThat(issue.ehrPersonId()).isEqualTo("P-1");
                    assertThat(issue.detailDigest()).contains("employee number");
                });
    }

    /**
     * 验证无法规范化的 EHR 手机号只隔离当前人员。
     */
    @Test
    void isolatesInvalidMobileNumber() {
        EhrEmployeeSourceDTO source = employee("P-1", "E-1", "张三", "男");
        source = new EhrEmployeeSourceDTO(
                source.ehrPersonId(), source.employeeNo(), source.displayName(),
                source.gender(), source.birthday(), "not-a-phone", source.email(),
                source.departmentCode(), source.departmentName(),
                source.legalCompanyCode(), source.legalCompanyName(),
                source.supervisorEmployeeNo(), source.jobGrade(),
                source.professionalTitle(), source.jobCode(), source.jobName(),
                source.positionCode(), source.positionName(), source.hireDate(),
                source.terminationDate(), source.modifiedTime(), source.creationTime());

        var result = validator.validate(
                new EhrEmployeeSnapshotDTO(1, 1, List.of(source)));

        assertThat(result.employees()).isEmpty();
        assertThat(result.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.issueCode()).isEqualTo("EHR_MOBILE_INVALID");
                    assertThat(issue.ehrPersonId()).isEqualTo("P-1");
                    assertThat(issue.employeeNo()).isEqualTo("E-1");
                });
    }

    private EhrEmployeeSourceDTO employee(String ehrPersonId, String employeeNo,
                                          String name, String gender) {
        return new EhrEmployeeSourceDTO(
                ehrPersonId, employeeNo, name, gender, "1990-01-02",
                "13800138000", "zhangsan@example.com", "D-1", "制造一部",
                "C-1", "伯恩公司", "M-1", "5", "工程师",
                "J-1", "工艺工程师", "PST-1", "高级工程师",
                "2020-01-01", null, "2026-07-29 10:00:00",
                "2020-01-01 08:00:00");
    }
}
