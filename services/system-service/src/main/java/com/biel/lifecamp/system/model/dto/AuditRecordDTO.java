package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 认证操作审计记录。
 *
 * @param id 审计记录标识
 * @param employeeId 可识别时记录的员工标识
 * @param action 被审计的操作
 * @param result 操作结果
 * @param detailCode 稳定明细编码
 * @param requestId 请求链路标识
 * @param now 操作时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record AuditRecordDTO(Long id, Long employeeId, String action, String result,
                             String detailCode, String requestId, Instant now) {
}
