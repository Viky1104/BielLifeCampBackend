package com.biel.lifecamp.starter.security.redis;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheAccessException;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 使用显式 JSON 序列化保存版本化授权和当前权限版本。
 *
 * <p>权限版本进入缓存键，旧请求不能覆盖新版本授权；员工标识作为 Redis Cluster
 * 哈希标签，同一员工的版本与授权键落在同一槽位。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public final class RedisAuthorizationCacheStore implements AuthorizationCacheStore {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[a-zA-Z0-9:_-]{3,100}");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("[a-z0-9-]{2,64}");
    private static final Duration MINIMUM_AUTHORIZATION_TTL = Duration.ofMinutes(1);
    private static final Duration MAXIMUM_AUTHORIZATION_TTL = Duration.ofHours(1);
    private static final Duration MINIMUM_VERSION_TTL = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_VERSION_TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final String versionKeyPrefix;
    private final Duration ttl;
    private final Duration versionTtl;

    public RedisAuthorizationCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AuthorizationCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = requirePrefix(properties.getKeyPrefix());
        this.versionKeyPrefix = requirePrefix(properties.getVersionKeyPrefix());
        this.ttl = requireTtl(
                properties.getTtl(), MINIMUM_AUTHORIZATION_TTL,
                MAXIMUM_AUTHORIZATION_TTL, "Authorization cache TTL is invalid");
        this.versionTtl = requireTtl(
                properties.getVersionTtl(), MINIMUM_VERSION_TTL,
                MAXIMUM_VERSION_TTL, "Authorization version TTL is invalid");
    }

    @Override
    public void save(CachedAuthorization authorization) {
        String key = authorizationKey(
                authorization.employeeId(),
                authorization.targetService(),
                authorization.authzVersion());
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(authorization), ttl);
        } catch (Exception ex) {
            throw new AuthorizationCacheAccessException(
                    "Unable to save authorization cache", ex);
        }
    }

    @Override
    public Optional<CachedAuthorization> find(
            String employeeId, String targetService, long authzVersion) {
        String key = authorizationKey(employeeId, targetService, authzVersion);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedAuthorization.class));
        } catch (Exception ex) {
            removeCorruptedValue(key);
            throw new AuthorizationCacheAccessException(
                    "Unable to read authorization cache", ex);
        }
    }

    @Override
    public void saveCurrentVersion(String employeeId, long authzVersion) {
        if (authzVersion < 0) {
            throw new IllegalArgumentException("authzVersion is invalid");
        }
        try {
            redisTemplate.opsForValue().set(
                    versionKey(employeeId), Long.toString(authzVersion), versionTtl);
        } catch (RuntimeException ex) {
            throw new AuthorizationCacheAccessException(
                    "Unable to save authorization version", ex);
        }
    }

    @Override
    public OptionalLong findCurrentVersion(String employeeId) {
        String key = versionKey(employeeId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? OptionalLong.empty()
                    : OptionalLong.of(Long.parseLong(value));
        } catch (RuntimeException ex) {
            removeCorruptedValue(key);
            throw new AuthorizationCacheAccessException(
                    "Unable to read authorization version", ex);
        }
    }

    private String authorizationKey(
            String employeeId, String targetService, long authzVersion) {
        String canonicalEmployeeId = requireEmployeeId(employeeId);
        if (targetService == null || !SERVICE_PATTERN.matcher(targetService).matches()) {
            throw new IllegalArgumentException("targetService is invalid");
        }
        if (authzVersion < 0) {
            throw new IllegalArgumentException("authzVersion is invalid");
        }
        return keyPrefix + ":{" + canonicalEmployeeId + "}:"
                + targetService + ":" + authzVersion;
    }

    private String versionKey(String employeeId) {
        return versionKeyPrefix + ":{" + requireEmployeeId(employeeId) + "}";
    }

    private String requireEmployeeId(String value) {
        try {
            long employeeId = Long.parseLong(value);
            if (employeeId <= 0 || !Long.toString(employeeId).equals(value)) {
                throw new IllegalArgumentException("employeeId is invalid");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("employeeId is invalid", ex);
        }
    }

    private String requirePrefix(String value) {
        if (value == null || !PREFIX_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException("Authorization Redis key prefix is invalid");
        }
        return value;
    }

    private Duration requireTtl(
            Duration value, Duration minimum, Duration maximum, String message) {
        if (value == null || value.compareTo(minimum) < 0
                || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private void removeCorruptedValue(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
            // 原始读取异常具有更高诊断价值，清理失败不覆盖其根因链。
        }
    }
}
