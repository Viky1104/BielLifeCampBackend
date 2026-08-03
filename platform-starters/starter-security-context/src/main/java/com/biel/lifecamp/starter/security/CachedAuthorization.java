package com.biel.lifecamp.starter.security;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Redis 中按员工、目标服务和权限版本保存的不可变授权快照。
 *
 * <p>快照不绑定单个登录会话，因此同一员工的多个有效会话可以共享授权数据。
 * 会话标识和认证方式只从已经验签的内部身份中取得。</p>
 *
 * @param employeeId 员工标识
 * @param employeeNo EHR 工号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param targetService 权限对应的目标服务
 * @param authzVersion 权限版本
 * @param roles 当前生效角色
 * @param permissions 目标服务权限
 * @param dataScopes 当前生效数据范围
 * @param cachedAt 缓存建立时间
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record CachedAuthorization(
        String employeeId,
        String employeeNo,
        String displayName,
        String organizationId,
        String targetService,
        long authzVersion,
        Set<String> roles,
        Set<String> permissions,
        List<IdentityContext.DataScope> dataScopes,
        Instant cachedAt) {
    /**
     * 复制集合并拒绝缺失的身份字段，避免可变授权数据进入请求上下文。
     */
    public CachedAuthorization {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(employeeNo, "employeeNo must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(targetService, "targetService must not be null");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        dataScopes = List.copyOf(dataScopes);
        Objects.requireNonNull(cachedAt, "cachedAt must not be null");
    }

    /**
     * 校验缓存授权与内部身份中的安全关键声明完全一致。
     *
     * @param identity 已验签内部身份
     * @param expectedTargetService 当前应用服务名
     * @return 安全关键字段一致时返回 {@code true}
     */
    public boolean matches(IdentityContext identity, String expectedTargetService) {
        return employeeId.equals(identity.employeeId())
                && organizationId.equals(identity.organizationId())
                && targetService.equals(expectedTargetService)
                && authzVersion == identity.authzVersion()
                && roles.equals(identity.roles())
                && permissions.equals(identity.permissions())
                && dataScopes.equals(identity.dataScopes());
    }

    /**
     * 将共享授权快照与当前会话声明组合为请求级登录用户。
     *
     * @param identity 已验签内部身份
     * @return 当前请求登录用户
     */
    public LoginUser toLoginUser(IdentityContext identity) {
        return new LoginUser(
                employeeId, employeeNo, displayName, organizationId,
                identity.sessionId(), identity.clientType(), targetService, authzVersion,
                roles, permissions, dataScopes, identity.authenticationMethods(), cachedAt);
    }
}
