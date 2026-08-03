package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 刷新令牌持久化投影。
 *
 * @param id 刷新令牌标识
 * @param sessionId 所属会话标识
 * @param familyId 轮换家族标识
 * @param status 令牌状态
 * @param expiresAt 到期时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record RefreshTokenDTO(String id, String sessionId, String familyId, String status,
                              Instant expiresAt) {
}
