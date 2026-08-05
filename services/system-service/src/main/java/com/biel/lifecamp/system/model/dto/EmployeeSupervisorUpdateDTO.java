package com.biel.lifecamp.system.model.dto;

/**
 * EHR 同步批量更新直属领导关系的最小参数。
 *
 * @param employeeId 员工本地主键
 * @param supervisorEmployeeId 直属领导本地主键，未匹配时为空
 * @author Biel Life Camp Team
 * @since 2026-08-04
 */
public record EmployeeSupervisorUpdateDTO(
        long employeeId,
        Long supervisorEmployeeId) {
}
