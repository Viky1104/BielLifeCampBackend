package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 延长会话空闲有效期所需的参数。
 *
 * @param sessionId 会话标识
 * @param idleExpiresAt 新的空闲到期时间
 * @param now 校验成功时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionTouchDTO(String sessionId, Instant idleExpiresAt, Instant now) {
}
