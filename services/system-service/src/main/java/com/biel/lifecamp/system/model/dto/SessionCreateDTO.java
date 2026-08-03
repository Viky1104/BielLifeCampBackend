package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 创建已认证会话所需的参数。
 *
 * @param sessionId 会话标识
 * @param employeeId 员工标识
 * @param clientType 客户端类型
 * @param authMethod 认证方式
 * @param authzVersion 签发时的权限版本
 * @param absoluteExpiresAt 会话绝对到期时间
 * @param idleExpiresAt 会话空闲到期时间
 * @param now 创建时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionCreateDTO(String sessionId, Long employeeId,
                               String clientType, String authMethod, Long authzVersion,
                               Instant absoluteExpiresAt, Instant idleExpiresAt, Instant now) {
}
