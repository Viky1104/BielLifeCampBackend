package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.system.common.exception.AuthSessionCacheAccessException;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.system.config.properties.AuthSessionCacheProperties;
import com.biel.lifecamp.system.model.dto.AuthSessionCacheDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 在线会话的读取、滑动续期和精确撤销管理器。
 *
 * <p>缓存不存在属于正常读穿场景；连接、认证和反序列化异常会转换为明确技术失败，
 * 不能伪装成缓存未命中后绕过 Redis 故障。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Component
public final class AuthSessionCacheManager {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[a-zA-Z0-9:_-]{3,100}");
    private static final Duration MINIMUM_TOUCH_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_REDIS_TOUCH_INTERVAL = Duration.ofMinutes(30);
    private static final Duration MAXIMUM_DATABASE_TOUCH_INTERVAL = Duration.ofHours(1);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthSessionCacheProperties properties;
    private final String keyPrefix;

    public AuthSessionCacheManager(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            AuthSessionCacheProperties properties,
            AuthorizationCacheProperties authorizationProperties) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyPrefix = requirePrefix(properties.getKeyPrefix());
        validateIntervals(properties);
        if (properties.isEnabled() != authorizationProperties.isEnabled()) {
            throw new IllegalStateException(
                    "Redis session and authorization cache must be enabled together");
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 查询在线会话。
     *
     * @param sessionId 会话标识
     * @return 缓存命中的在线会话
     */
    public Optional<AuthSessionCacheDTO> find(String sessionId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        String key = key(sessionId);
        try {
            String json = requiredTemplate().opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AuthSessionCacheDTO.class));
        } catch (Exception ex) {
            removeCorruptedValue(key);
            throw new AuthSessionCacheAccessException("Unable to read auth session cache", ex);
        }
    }

    /**
     * 保存在线会话，TTL 不超过数据库会话剩余有效期。
     *
     * @param session 在线会话
     * @param now 当前时间
     */
    public void save(AuthSessionCacheDTO session, Instant now) {
        if (!isEnabled()) {
            return;
        }
        Duration ttl = Duration.between(
                now, minimum(session.absoluteExpiresAt(), session.idleExpiresAt()));
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Session remaining TTL must be positive");
        }
        try {
            requiredTemplate().opsForValue().set(
                    key(session.sessionId()), objectMapper.writeValueAsString(session), ttl);
        } catch (Exception ex) {
            throw new AuthSessionCacheAccessException("Unable to save auth session cache", ex);
        }
    }

    /**
     * 精确删除一个在线会话。
     *
     * @param sessionId 会话标识
     */
    public void deleteRequired(String sessionId) {
        deleteRequired(List.of(sessionId));
    }

    /**
     * 精确批量删除在线会话，不使用通配扫描。
     *
     * @param sessionIds 会话标识集合
     */
    public void deleteRequired(Collection<String> sessionIds) {
        if (!isEnabled() || sessionIds.isEmpty()) {
            return;
        }
        List<String> keys = sessionIds.stream().map(this::key).toList();
        try {
            requiredTemplate().delete(keys);
        } catch (RuntimeException ex) {
            throw new AuthSessionCacheAccessException("Unable to delete auth session cache", ex);
        }
    }

    public boolean shouldTouchRedis(AuthSessionCacheDTO session, Instant now) {
        return !now.isBefore(
                session.lastRedisTouchAt().plus(properties.getRedisTouchInterval()));
    }

    public boolean shouldTouchDatabase(AuthSessionCacheDTO session, Instant now) {
        return !now.isBefore(
                session.lastDatabaseTouchAt().plus(properties.getDatabaseTouchInterval()));
    }

    private String key(String sessionId) {
        String canonicalSessionId;
        try {
            canonicalSessionId = UUID.fromString(sessionId).toString();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("sessionId is invalid", ex);
        }
        if (!canonicalSessionId.equals(sessionId.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("sessionId is invalid");
        }
        return keyPrefix + ":{" + canonicalSessionId + "}";
    }

    private StringRedisTemplate requiredTemplate() {
        if (redisTemplate == null) {
            throw new IllegalStateException("StringRedisTemplate is required");
        }
        return redisTemplate;
    }

    private String requirePrefix(String value) {
        if (value == null || !PREFIX_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException("Auth session Redis key prefix is invalid");
        }
        return value;
    }

    private void validateIntervals(AuthSessionCacheProperties value) {
        requireInterval(
                value.getRedisTouchInterval(),
                MAXIMUM_REDIS_TOUCH_INTERVAL,
                "Redis touch interval is invalid");
        requireInterval(
                value.getDatabaseTouchInterval(),
                MAXIMUM_DATABASE_TOUCH_INTERVAL,
                "Database touch interval is invalid");
        if (value.getDatabaseTouchInterval().compareTo(value.getRedisTouchInterval()) < 0) {
            throw new IllegalStateException(
                    "Database touch interval must not be shorter than Redis touch interval");
        }
    }

    private void requireInterval(Duration value, Duration maximum, String message) {
        if (value == null || value.compareTo(MINIMUM_TOUCH_INTERVAL) < 0
                || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(message);
        }
    }

    private Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private void removeCorruptedValue(String key) {
        try {
            requiredTemplate().delete(key);
        } catch (RuntimeException ignored) {
            // 原始读取异常具有更高诊断价值，清理失败不覆盖其根因链。
        }
    }
}
