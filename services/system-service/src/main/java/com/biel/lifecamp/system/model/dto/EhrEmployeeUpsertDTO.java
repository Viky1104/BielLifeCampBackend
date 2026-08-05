package com.biel.lifecamp.system.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 校验完成后用于持久化的 EHR 员工投影。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrEmployeeUpsertDTO(
        String employeeNo,
        String displayName,
        String genderCode,
        String genderSourceValue,
        LocalDate birthday,
        String mobile,
        String email,
        String departmentCode,
        String departmentName,
        String legalCompanyCode,
        String legalCompanyName,
        String supervisorEmployeeNo,
        String jobGrade,
        String professionalTitle,
        String jobCode,
        String jobName,
        String positionCode,
        String positionName,
        LocalDate hireDate,
        LocalDate terminationDate,
        LocalDateTime modifiedTime,
        LocalDateTime creationTime) {
}
