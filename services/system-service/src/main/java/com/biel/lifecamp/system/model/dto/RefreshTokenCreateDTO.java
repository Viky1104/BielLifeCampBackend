package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 持久化一代刷新令牌所需的参数。
 *
 * @param id 刷新令牌标识
 * @param sessionId 所属会话标识
 * @param familyId 轮换家族标识
 * @param hash 经保护的令牌摘要
 * @param parentId 上一代令牌标识；首代为空
 * @param expiresAt 令牌到期时间
 * @param now 创建时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record RefreshTokenCreateDTO(String id, String sessionId, String familyId, String hash,
                                    String parentId, Instant expiresAt, Instant now) {
}
