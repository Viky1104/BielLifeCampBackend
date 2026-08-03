package com.biel.lifecamp.system.model.dto;

/**
 * 单个人员同步失败的问题摘要。
 *
 * <p>问题对象只保留定位所需的 EHR 人员标识、工号和脱敏错误摘要，
 * 不携带姓名、手机号、邮箱等个人敏感信息。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
public record EhrEmployeeSyncIssueDTO(
        String issueCode,
        String ehrPersonId,
        String employeeNo,
        String detailDigest,
        String failureStage) {
}
