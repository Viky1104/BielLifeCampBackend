package com.biel.lifecamp.system.model.dto;

import java.util.List;

/**
 * 会话校验后返回给可信网关的授权和认证上下文。
 *
 * @param authorization 实时授权快照
 * @param clientType 登录客户端类型
 * @param authenticationMethods 认证方式集合
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record ResolvedSessionContextDTO(
        AuthorizationSnapshotDTO authorization,
        String clientType,
        List<String> authenticationMethods) {
}

