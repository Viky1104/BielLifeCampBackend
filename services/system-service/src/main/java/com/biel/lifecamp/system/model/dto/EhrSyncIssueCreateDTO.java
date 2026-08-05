package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * EHR 人员同步问题批量持久化参数。
 *
 * @param id 问题记录主键
 * @param runId 同步运行标识
 * @param severity 问题严重级别
 * @param issueCode 稳定问题编码
 * @param employeeNo 涉及的员工工号
 * @param failureStage 失败阶段
 * @param detailDigest 不包含敏感字段值的问题摘要
 * @param createdAt 记录时间
 * @author Biel Life Camp Team
 * @since 2026-08-04
 */
public record EhrSyncIssueCreateDTO(
        long id,
        long runId,
        String severity,
        String issueCode,
        String employeeNo,
        String failureStage,
        String detailDigest,
        Instant createdAt) {
}
