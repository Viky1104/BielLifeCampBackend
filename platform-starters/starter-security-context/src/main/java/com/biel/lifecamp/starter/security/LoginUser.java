package com.biel.lifecamp.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 当前请求内使用的不可变登录用户。
 *
 * <p>该对象由已经验签的内部身份和同版本 Redis 授权快照组合产生，不直接持久化到 Redis。
 * 它不包含访问令牌、刷新令牌、手机号、OpenID 或其他认证秘密。</p>
 *
 * @param employeeId 员工标识
 * @param employeeNo EHR 工号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param sessionId 当前会话标识
 * @param clientType 登录客户端类型
 * @param targetService 当前权限集合对应的目标服务
 * @param authzVersion 当前权限版本
 * @param roles 当前生效角色
 * @param permissions 目标服务权限
 * @param dataScopes 当前生效数据范围
 * @param authenticationMethods 会话认证方式
 * @param refreshedAt 授权快照建立时间
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record LoginUser(
        String employeeId,
        String employeeNo,
        String displayName,
        String organizationId,
        String sessionId,
        String clientType,
        String targetService,
        long authzVersion,
        Set<String> roles,
        Set<String> permissions,
        List<IdentityContext.DataScope> dataScopes,
        Set<String> authenticationMethods,
        Instant refreshedAt) {
    /** 在请求域中保存登录用户快照的属性名。 */
    public static final String REQUEST_ATTRIBUTE = LoginUser.class.getName();

    /**
     * 防止可变集合或缺失关键身份字段进入全局安全上下文。
     */
    public LoginUser {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(employeeNo, "employeeNo must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(targetService, "targetService must not be null");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        dataScopes = List.copyOf(dataScopes);
        authenticationMethods = Set.copyOf(authenticationMethods);
        Objects.requireNonNull(refreshedAt, "refreshedAt must not be null");
    }

    /**
     * 从请求域读取已通过 Redis 与内部身份双重校验的登录用户。
     *
     * @param request 当前 HTTP 请求
     * @return 登录用户；未建立 Redis 登录上下文时返回 {@code null}
     */
    public static LoginUser from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

}
