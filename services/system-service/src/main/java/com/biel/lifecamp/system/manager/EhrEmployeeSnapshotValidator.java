package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.system.common.exception.EhrSyncException;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSourceDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSyncIssueDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeUpsertDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeValidationResultDTO;
import com.biel.lifecamp.system.util.PhoneNumberNormalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 在业务表生效前校验并规范化 EHR 人员全量快照。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Component
public final class EhrEmployeeSnapshotValidator {
    private static final DateTimeFormatter EHR_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 校验快照完整性并转换人员字段。
     *
     * @param snapshot EHR 全量快照
     * @return 人员级校验结果
     * @throws EhrSyncException 快照整体不完整时抛出
     */
    public EhrEmployeeValidationResultDTO validate(EhrEmployeeSnapshotDTO snapshot) {
        /*
         * 空快照和声明数量不一致都视为阻断性错误。全量同步后续会把缺失人员标记离职，
         * 因此不能以“部分成功”方式继续，否则可能扩大上游临时故障的影响范围。
         */
        if (snapshot.totalRecords() == 0 || snapshot.employees().isEmpty()) {
            throw new EhrSyncException("EHR_EMPTY_SNAPSHOT",
                    "EHR returned an empty full employee snapshot");
        }
        if (snapshot.totalRecords() != snapshot.employees().size()) {
            throw new EhrSyncException("EHR_SNAPSHOT_COUNT_MISMATCH",
                    "EHR snapshot record count does not match declared total record count");
        }
        Set<String> personIds = new HashSet<>();
        Set<String> employeeNumbers = new HashSet<>();
        List<EhrEmployeeUpsertDTO> employees = new ArrayList<>(snapshot.employees().size());
        List<EhrEmployeeSyncIssueDTO> issues = new ArrayList<>();
        for (EhrEmployeeSourceDTO source : snapshot.employees()) {
            try {
                String personId = required(source.ehrPersonId(), "ehr person id");
                String employeeNo = required(source.employeeNo(), "employee number");
                String displayName = required(source.displayName(), "display name");

                /*
                 * 先完成字段规范化，再占用批次内的身份唯一值。这样第一条脏数据不会阻断
                 * 后面相同身份但内容有效的人员，且每个失败人员都能独立记录。
                 */
                EhrEmployeeUpsertDTO employee =
                        toUpsert(source, personId, employeeNo, displayName);
                if (personIds.contains(personId)) {
                    throw duplicate("ehr person id");
                }
                if (employeeNumbers.contains(employeeNo)) {
                    throw duplicate("employee number");
                }
                personIds.add(personId);
                employeeNumbers.add(employeeNo);
                employees.add(employee);
            } catch (EhrSyncException exception) {
                issues.add(new EhrEmployeeSyncIssueDTO(
                        exception.code(), trimToNull(source.ehrPersonId()),
                        trimToNull(source.employeeNo()), exception.getMessage(),
                        "VALIDATING"));
            }
        }
        return new EhrEmployeeValidationResultDTO(
                snapshot.employees().size(), employees, issues);
    }

    /**
     * 将已经通过身份唯一性检查的来源人员转换为持久化模型。
     *
     * @param source EHR 来源人员
     * @param personId 已清理的 EHR 人员标识
     * @param employeeNo 已清理的工号
     * @param displayName 已清理的姓名
     * @return 可写入员工投影的数据
     */
    private EhrEmployeeUpsertDTO toUpsert(EhrEmployeeSourceDTO source,
                                          String personId,
                                          String employeeNo,
                                          String displayName) {
        return new EhrEmployeeUpsertDTO(
                personId, employeeNo, displayName, normalizeGender(source.gender()),
                trimToNull(source.gender()), parseDate(source.birthday()),
                normalizeMobile(source.mobile()), trimToNull(source.email()),
                trimToNull(source.departmentCode()), trimToNull(source.departmentName()),
                trimToNull(source.legalCompanyCode()), trimToNull(source.legalCompanyName()),
                trimToNull(source.supervisorEmployeeNo()), trimToNull(source.jobGrade()),
                trimToNull(source.professionalTitle()), trimToNull(source.jobCode()),
                trimToNull(source.jobName()), trimToNull(source.positionCode()),
                trimToNull(source.positionName()), parseDate(source.hireDate()),
                parseDate(source.terminationDate()), parseDateTime(source.modifiedTime()),
                parseDateTime(source.creationTime()));
    }

    /**
     * 清理并校验必填字符串。
     *
     * @param value 原始字段值
     * @param field 用于脱敏错误摘要的字段名称
     * @return 去除首尾空白的非空值
     */
    private String required(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new EhrSyncException("EHR_REQUIRED_FIELD_MISSING",
                    "EHR snapshot contains a missing required " + field);
        }
        return normalized;
    }

    /**
     * 创建不包含具体人员值的重复身份异常。
     *
     * @param field 重复字段名称
     * @return 稳定同步异常
     */
    private EhrSyncException duplicate(String field) {
        return new EhrSyncException("EHR_DUPLICATE_IDENTITY",
                "EHR snapshot contains a duplicate " + field);
    }

    /**
     * 将 EHR 多种性别编码收敛到系统枚举值。
     *
     * @param value EHR 性别原值
     * @return {@code MALE}、{@code FEMALE} 或 {@code UNKNOWN}
     */
    private String normalizeGender(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "UNKNOWN";
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "男", "MALE", "M", "0" -> "MALE";
            case "女", "FEMALE", "F", "1" -> "FEMALE";
            default -> "UNKNOWN";
        };
    }

    /**
     * 解析 EHR 日期字段。
     *
     * <p>生日、入职和离职日期均为非身份主键字段；格式异常时降级为空，
     * 避免单个非关键日期阻断整批同步，原始问题由同步前数据质量核对发现。</p>
     *
     * @param value EHR 日期字符串
     * @return 日期，缺失或无法解析时返回 {@code null}
     */
    private LocalDate parseDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized.length() >= 10
                    ? normalized.substring(0, 10) : normalized);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 解析 EHR 数据变更时间。
     *
     * @param value EHR 日期时间字符串
     * @return 日期时间，缺失或无法解析时返回 {@code null}
     */
    private LocalDateTime parseDateTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized, EHR_DATE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 规范化手机号并拒绝明显非法值。
     *
     * @param value EHR 手机号
     * @return 规范化手机号，缺失时返回 {@code null}
     * @throws EhrSyncException 手机号格式不合法时抛出
     */
    private String normalizeMobile(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return PhoneNumberNormalizer.normalize(normalized);
        } catch (IllegalArgumentException ex) {
            throw new EhrSyncException(
                    "EHR_MOBILE_INVALID", "EHR snapshot contains an invalid mobile number");
        }
    }

    /**
     * 将空白字符串统一转换为空值。
     *
     * @param value 原始字符串
     * @return 去除首尾空白后的值，空白时返回 {@code null}
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
