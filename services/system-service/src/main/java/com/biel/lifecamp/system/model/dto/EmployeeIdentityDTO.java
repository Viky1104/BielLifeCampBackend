package com.biel.lifecamp.system.model.dto;

/**
 * 本地员工的 EHR 人员标识与工号归属关系。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
public record EmployeeIdentityDTO(String ehrPersonId, String employeeNo) {
}
