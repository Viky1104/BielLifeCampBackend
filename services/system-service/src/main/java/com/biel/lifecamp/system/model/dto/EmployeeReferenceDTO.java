package com.biel.lifecamp.system.model.dto;

/**
 * 同步期间解析员工自关联所需的最小投影。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EmployeeReferenceDTO(Long id, String ehrPersonId, String employeeNo,
                                   String supervisorEmployeeNo) {
}
