package com.biel.lifecamp.system.model.dto.response;

import com.biel.lifecamp.system.model.dto.EhrSyncIssueDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * EHR 人员同步问题响应。
 *
 * @param id 问题记录主键
 * @param severity 问题严重级别
 * @param issueCode 稳定问题编码
 * @param employeeNo 涉及的员工工号
 * @param failureStage 失败阶段
 * @param detailDigest 脱敏问题摘要
 * @param createdAt 记录时间
 * @author Biel Life Camp Team
 * @since 2026-08-04
 */
@Schema(name = "EhrSyncIssue", description = "单个人员的 EHR 同步问题")
public record EhrSyncIssueResp(
        @Schema(description = "问题记录主键")
        long id,
        @Schema(description = "问题严重级别", example = "ERROR")
        String severity,
        @Schema(description = "稳定问题编码", example = "EHR_MOBILE_INVALID")
        String issueCode,
        @Schema(description = "涉及的员工工号", example = "E10001")
        String employeeNo,
        @Schema(description = "失败阶段", example = "VALIDATING")
        String failureStage,
        @Schema(description = "不包含敏感字段值的问题摘要")
        String detailDigest,
        @Schema(description = "记录时间")
        Instant createdAt) {

    public static EhrSyncIssueResp from(EhrSyncIssueDTO issue) {
        return new EhrSyncIssueResp(
                issue.id(), issue.severity(), issue.issueCode(),
                issue.employeeNo(), issue.failureStage(),
                issue.detailDigest(), issue.createdAt());
    }
}
