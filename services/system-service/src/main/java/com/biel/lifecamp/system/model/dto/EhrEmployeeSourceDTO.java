package com.biel.lifecamp.system.model.dto;

/**
 * EHR 人员接口返回的源字段。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrEmployeeSourceDTO(
        String ehrPersonId,
        String employeeNo,
        String displayName,
        String gender,
        String birthday,
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
        String hireDate,
        String terminationDate,
        String modifiedTime,
        String creationTime) {
}
