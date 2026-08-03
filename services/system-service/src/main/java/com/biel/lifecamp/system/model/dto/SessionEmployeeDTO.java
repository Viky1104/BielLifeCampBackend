package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 身份数据访问层返回的会话与员工关联投影。
 *
 * @param sessionId 会话标识
 * @param sessionStatus 会话状态
 * @param clientType 客户端类型
 * @param authMethod 认证方式
 * @param absoluteExpiresAt 会话绝对到期时间
 * @param idleExpiresAt 会话空闲到期时间
 * @param authzVersionAtIssue 会话签发时保存的权限版本
 * @param employeeId 员工标识
 * @param employeeNo 员工编号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param employmentStatus 在职状态
 * @param accountStatus 账号状态
 * @param employeeAuthzVersion 员工当前权限版本
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionEmployeeDTO(String sessionId, String sessionStatus,
                                 String clientType, String authMethod, Instant absoluteExpiresAt,
                                 Instant idleExpiresAt, Long authzVersionAtIssue, Long employeeId,
                                 String employeeNo, String displayName, Long organizationId,
                                 String employmentStatus, String accountStatus,
                                 Long employeeAuthzVersion) {
    /**
     * 将扁平化关联投影转换为员工身份对象。
     *
     * @return 员工身份投影
     */
    public EmployeeDTO employee() {
        return new EmployeeDTO(employeeId, employeeNo, displayName, organizationId,
                employmentStatus, accountStatus, employeeAuthzVersion);
    }
}
