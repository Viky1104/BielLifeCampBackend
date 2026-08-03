package com.biel.lifecamp.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 读取当前请求登录用户及常用身份字段的安全工具类。
 *
 * <p>该工具只读取 {@link com.biel.lifecamp.starter.security.filter.IdentityContextFilter}
 * 已完成 JWS 验签和 Redis 快照一致性校验后写入的请求属性，不直接信任客户端请求头。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public final class SecurityUtils {
    private SecurityUtils() {
    }

    /**
     * 获取当前请求的 Redis 登录用户快照。
     *
     * @return 当前登录用户
     * @throws MissingLoginUserException 当前线程没有 HTTP 请求或没有登录上下文时抛出
     */
    public static LoginUser getLoginUser() {
        return getLoginUser(currentRequest());
    }

    /**
     * 从指定请求获取 Redis 登录用户快照。
     *
     * @param request 当前 HTTP 请求
     * @return 当前登录用户
     * @throws MissingLoginUserException 请求没有登录上下文时抛出
     */
    public static LoginUser getLoginUser(HttpServletRequest request) {
        LoginUser loginUser = LoginUser.from(request);
        if (loginUser == null) {
            throw new MissingLoginUserException();
        }
        return loginUser;
    }

    /** @return 当前员工数据库主键 */
    public static long getUserId() {
        try {
            return Long.parseLong(getLoginUser().employeeId());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Current employee id is invalid", ex);
        }
    }

    /** @return 当前员工工号 */
    public static String getUsername() {
        return getLoginUser().employeeNo();
    }

    /** @return 当前员工显示名称 */
    public static String getDisplayName() {
        return getLoginUser().displayName();
    }

    /** @return 当前员工主组织标识 */
    public static String getOrganizationId() {
        return getLoginUser().organizationId();
    }

    /** @return 当前登录会话标识 */
    public static String getSessionId() {
        return getLoginUser().sessionId();
    }

    /** @return 当前登录客户端类型 */
    public static String getClientType() {
        return getLoginUser().clientType();
    }

    /**
     * 从指定请求获取当前登录客户端类型。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端类型
     */
    public static String getClientType(HttpServletRequest request) {
        return getLoginUser(request).clientType();
    }

    /**
     * 判断当前请求是否来自指定登录客户端。
     *
     * @param request 当前 HTTP 请求
     * @param expectedClientType 期望的客户端类型
     * @return 客户端类型匹配时返回 {@code true}
     */
    public static boolean isClientType(
            HttpServletRequest request, String expectedClientType) {
        return getClientType(request).equals(expectedClientType);
    }

    /** @return 当前目标服务的权限集合 */
    public static Set<String> getPermissions() {
        return getLoginUser().permissions();
    }

    /**
     * 判断当前员工是否具有指定角色。
     *
     * @param roleCode 稳定角色编码
     * @return 具有角色时返回 {@code true}
     */
    public static boolean hasRole(String roleCode) {
        return getLoginUser().roles().contains(roleCode);
    }

    /**
     * 判断当前员工是否具有当前服务中的指定权限。
     *
     * @param permissionCode 稳定权限编码
     * @return 具有权限时返回 {@code true}
     */
    public static boolean hasPermission(String permissionCode) {
        return getLoginUser().permissions().contains(permissionCode);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new MissingLoginUserException();
    }

    /**
     * 当前执行线程缺少已验证登录用户上下文。
     */
    public static final class MissingLoginUserException extends RuntimeException {
    }
}
