package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 撤销已认证会话所需的参数。
 *
 * @param sessionId 会话标识
 * @param reason 稳定的撤销原因
 * @param now 撤销时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionRevokeDTO(String sessionId, String reason, Instant now) {
}
