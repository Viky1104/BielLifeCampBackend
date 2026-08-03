package com.biel.lifecamp.system.model.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 在线会话使用的最小员工与会话投影。
 *
 * @param sessionId 会话标识
 * @param sessionStatus 会话状态
 * @param clientType 客户端类型
 * @param authMethod 认证方式
 * @param absoluteExpiresAt 绝对到期时间
 * @param idleExpiresAt 空闲到期时间
 * @param authzVersionAtIssue 会话当前权限版本
 * @param employeeId 员工标识
 * @param employeeNo 员工工号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param employmentStatus 在职状态
 * @param accountStatus 账号状态
 * @param employeeAuthzVersion 员工当前权限版本
 * @param lastRedisTouchAt 最近一次 Redis 续期时间
 * @param lastDatabaseTouchAt 最近一次数据库活跃时间持久化时间
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record AuthSessionCacheDTO(
        String sessionId,
        String sessionStatus,
        String clientType,
        String authMethod,
        Instant absoluteExpiresAt,
        Instant idleExpiresAt,
        long authzVersionAtIssue,
        long employeeId,
        String employeeNo,
        String displayName,
        Long organizationId,
        String employmentStatus,
        String accountStatus,
        long employeeAuthzVersion,
        Instant lastRedisTouchAt,
        Instant lastDatabaseTouchAt) {
    /**
     * 拒绝不完整会话进入在线认证缓存。
     */
    public AuthSessionCacheDTO {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(sessionStatus, "sessionStatus must not be null");
        /*
         * v1 Redis 会话在本次升级前只可能由微信小程序创建。滚动发布期间读取到
         * 缺少新字段的旧缓存时按原有语义补齐，避免要求用户集中重新登录。
         */
        clientType = clientType == null ? "MINI_PROGRAM" : clientType;
        authMethod = authMethod == null ? "WECHAT" : authMethod;
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
        Objects.requireNonNull(idleExpiresAt, "idleExpiresAt must not be null");
        Objects.requireNonNull(employeeNo, "employeeNo must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(employmentStatus, "employmentStatus must not be null");
        Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        Objects.requireNonNull(lastRedisTouchAt, "lastRedisTouchAt must not be null");
        Objects.requireNonNull(lastDatabaseTouchAt, "lastDatabaseTouchAt must not be null");
    }

    /**
     * 从数据库会话投影建立在线缓存。
     *
     * @param session 数据库会话投影
     * @param now 缓存建立时间
     * @return 在线会话缓存
     */
    public static AuthSessionCacheDTO from(SessionEmployeeDTO session, Instant now) {
        return new AuthSessionCacheDTO(
                session.sessionId(), session.sessionStatus(),
                session.clientType(), session.authMethod(),
                session.absoluteExpiresAt(), session.idleExpiresAt(),
                session.authzVersionAtIssue(), session.employeeId(),
                session.employeeNo(), session.displayName(), session.organizationId(),
                session.employmentStatus(), session.accountStatus(),
                session.employeeAuthzVersion(), now, now);
    }

    /**
     * 转换为现有认证业务使用的会话投影。
     *
     * @return 会话与员工投影
     */
    public SessionEmployeeDTO toSessionEmployee() {
        return new SessionEmployeeDTO(
                sessionId, sessionStatus, clientType, authMethod,
                absoluteExpiresAt, idleExpiresAt,
                authzVersionAtIssue, employeeId, employeeNo, displayName,
                organizationId, employmentStatus, accountStatus, employeeAuthzVersion);
    }

    /**
     * 建立滑动续期后的新缓存值。
     *
     * @param newIdleExpiresAt 新空闲到期时间
     * @param now 本次续期时间
     * @param databaseTouched 本次是否已经持久化数据库活跃时间
     * @return 更新后的缓存值
     */
    public AuthSessionCacheDTO touch(
            Instant newIdleExpiresAt, Instant now, boolean databaseTouched) {
        return new AuthSessionCacheDTO(
                sessionId, sessionStatus, clientType, authMethod,
                absoluteExpiresAt, newIdleExpiresAt,
                authzVersionAtIssue, employeeId, employeeNo, displayName,
                organizationId, employmentStatus, accountStatus, employeeAuthzVersion,
                now, databaseTouched ? now : lastDatabaseTouchAt);
    }
}
