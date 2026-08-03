package com.biel.lifecamp.system.model.dto;

/**
 * 本地管理员凭据与员工状态的只读投影。
 *
 * @param employeeId 员工主键
 * @param employeeNo 工号
 * @param displayName 显示名称
 * @param organizationId 主组织主键
 * @param employmentStatus 在职状态
 * @param accountStatus 账号状态
 * @param authzVersion 权限版本
 * @param passwordHash 带算法标识的密码哈希
 * @param credentialStatus 凭据状态
 * @param mustChangePassword 是否要求修改密码
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record AdminCredentialDTO(
        Long employeeId,
        String employeeNo,
        String displayName,
        Long organizationId,
        String employmentStatus,
        String accountStatus,
        Long authzVersion,
        String passwordHash,
        String credentialStatus,
        boolean mustChangePassword) {
    /**
     * 转换为认证流程通用的员工投影。
     *
     * @return 员工投影
     */
    public EmployeeDTO employee() {
        return new EmployeeDTO(employeeId, employeeNo, displayName, organizationId,
                employmentStatus, accountStatus, authzVersion);
    }
}

