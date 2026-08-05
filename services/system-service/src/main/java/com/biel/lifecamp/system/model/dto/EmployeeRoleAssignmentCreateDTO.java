package com.biel.lifecamp.system.model.dto;

/**
 * EHR 同步批量初始化默认角色的最小参数。
 *
 * @param id 角色分配主键
 * @param employeeId 员工本地主键
 * @param roleId 默认角色主键
 * @param scopeValue 员工本人数据范围值
 * @author Biel Life Camp Team
 * @since 2026-08-04
 */
public record EmployeeRoleAssignmentCreateDTO(
        long id,
        long employeeId,
        long roleId,
        String scopeValue) {
}
