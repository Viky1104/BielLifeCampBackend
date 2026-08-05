package com.biel.lifecamp.system.model.dto;

/**
 * 认证服务使用的员工身份投影。
 *
 * @param id 员工标识
 * @param employeeNo EHR 权威员工编号
 * @param displayName 员工显示名称
 * @param organizationId 本地主组织数据库主键；组织主数据尚未映射时为空
 * @param employmentStatus EHR 在职状态
 * @param accountStatus 本地账号状态
 * @param authzVersion 权限版本
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record EmployeeDTO(Long id, String employeeNo, String displayName, Long organizationId,
                          String employmentStatus, String accountStatus, Long authzVersion) {
    /** 认证上下文中用于表示组织尚未解析的保留值。 */
    public static final String UNRESOLVED_ORGANIZATION_ID = "0";

    /**
     * 返回认证上下文使用的组织标识。
     *
     * <p>EHR 人员已经同步、但组织主数据尚未映射时返回 0。该值仅表示组织未解析，
     * 不代表真实组织，也不能据此授予组织数据范围。</p>
     *
     * @return 本地组织主键字符串，或未解析标识 0
     */
    public String organizationIdValue() {
        return organizationId == null
                ? UNRESOLVED_ORGANIZATION_ID
                : Long.toString(organizationId);
    }
}
