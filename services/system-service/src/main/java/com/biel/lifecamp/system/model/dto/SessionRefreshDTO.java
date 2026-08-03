package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 刷新会话权限版本与活跃时间所需的参数。
 *
 * @param sessionId 会话标识
 * @param authzVersion 当前权限版本
 * @param idleExpiresAt 新的空闲到期时间
 * @param now 刷新时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionRefreshDTO(String sessionId, Long authzVersion,
                                Instant idleExpiresAt, Instant now) {
}
