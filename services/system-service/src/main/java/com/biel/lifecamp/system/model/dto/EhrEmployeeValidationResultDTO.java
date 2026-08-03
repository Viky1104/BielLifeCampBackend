package com.biel.lifecamp.system.model.dto;

import java.util.List;

/**
 * EHR 全量快照的人员级校验结果。
 *
 * @param fetchedCount EHR 快照实际人员数
 * @param employees 校验通过、可继续生效的人员
 * @param issues 校验失败、需要人工核对的人员问题
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
public record EhrEmployeeValidationResultDTO(
        long fetchedCount,
        List<EhrEmployeeUpsertDTO> employees,
        List<EhrEmployeeSyncIssueDTO> issues) {

    /**
     * 固化结果集合，防止进入持久化阶段后被调用方修改。
     */
    public EhrEmployeeValidationResultDTO {
        employees = List.copyOf(employees);
        issues = List.copyOf(issues);
    }
}
