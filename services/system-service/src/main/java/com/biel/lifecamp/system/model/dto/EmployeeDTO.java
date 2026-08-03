package com.biel.lifecamp.system.model.dto;

/**
 * 认证服务使用的员工身份投影。
 *
 * @param id 员工标识
 * @param employeeNo EHR 权威员工编号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param employmentStatus EHR 在职状态
 * @param accountStatus 本地账号状态
 * @param authzVersion 权限版本
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record EmployeeDTO(Long id, String employeeNo, String displayName, Long organizationId,
                          String employmentStatus, String accountStatus, Long authzVersion) {
}
