package com.biel.lifecamp.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * 下游服务可读取的不可变内部身份上下文。
 *
 * @param employeeId 员工标识
 * @param organizationId 主组织标识
 * @param sessionId 当前会话标识
 * @param clientType 登录客户端类型
 * @param authzVersion 当前权限版本
 * @param roles 生效角色集合
 * @param permissions 面向当前服务的权限集合
 * @param dataScopes 生效数据范围
 * @param authenticationMethods 本次会话使用的认证方式
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record IdentityContext(String employeeId, String organizationId, String sessionId,
                              String clientType,
                              long authzVersion, Set<String> roles, Set<String> permissions,
                              List<DataScope> dataScopes, Set<String> authenticationMethods) {
    /** 在请求域中保存身份上下文的属性名。 */
    public static final String REQUEST_ATTRIBUTE = IdentityContext.class.getName();

    /**
     * 从请求域读取身份上下文。
     *
     * @param request HTTP 请求
     * @return 已验证的身份上下文；请求未携带可信身份时返回 {@code null}
     */
    public static IdentityContext from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof IdentityContext context ? context : null;
    }

    /**
     * 获取必需的身份上下文。
     *
     * @param request HTTP 请求
     * @return 已验证的身份上下文
     * @throws MissingIdentityContextException 请求没有可信身份上下文时抛出
     */
    public static IdentityContext require(HttpServletRequest request) {
        IdentityContext context = from(request);
        if (context == null) {
            throw new MissingIdentityContextException();
        }
        return context;
    }
    /**
     * 内部身份携带的数据范围。
     *
     * @param type 数据范围类型
     * @param value 数据范围值；全局范围可为空
     */
    public record DataScope(String type, String value) {
    }

    /**
     * 请求缺少可信内部身份上下文时抛出的异常。
     */
    public static final class MissingIdentityContextException extends RuntimeException {
    }
}
