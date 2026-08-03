package com.biel.lifecamp.system.model.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 网关获取面向目标服务实时授权快照的请求。
 *
 * @param employeeId 已校验访问令牌中的员工标识
 * @param sessionId 已校验访问令牌中的会话标识
 * @param authzVersion 已校验访问令牌中的权限版本
 * @param targetService 接收内部身份的目标服务
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionContextReq(long employeeId, @NotBlank String sessionId,
                                long authzVersion, @NotBlank String targetService) {
}
