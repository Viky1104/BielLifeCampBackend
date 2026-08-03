package com.biel.lifecamp.system.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.system.config.properties.AuthSessionCacheProperties;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.system.model.dto.AuthSessionCacheDTO;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 在线会话键、TTL、续期节流和精确删除测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class AuthSessionCacheManagerTest {
    private static final String SESSION_ID =
            "11111111-1111-1111-1111-111111111111";

    /**
     * 验证在线会话TTL服从数据库空闲期限并且可以精确撤销。
     *
     * @throws Exception JSON 处理失败时抛出
     */
    @Test
    void storesSessionWithDatabaseBoundedTtlAndDeletesExactKey() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        AuthSessionCacheProperties properties = new AuthSessionCacheProperties();
        properties.setEnabled(true);
        AuthorizationCacheProperties authorizationProperties =
                new AuthorizationCacheProperties();
        authorizationProperties.setEnabled(true);
        JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AuthSessionCacheManager manager =
                new AuthSessionCacheManager(
                        provider, objectMapper, properties, authorizationProperties);
        Instant now = Instant.parse("2026-07-31T08:00:00Z");
        AuthSessionCacheDTO session = session(now);

        manager.save(session, now);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(operations).set(
                eq("biel:auth:session:v1:{" + SESSION_ID + "}"),
                jsonCaptor.capture(), eq(Duration.ofDays(7)));
        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"employeeNo\":\"E1001\"");
        assertThat(json).doesNotContain(
                "accessToken", "refreshToken", "phone", "openId");

        when(operations.get("biel:auth:session:v1:{" + SESSION_ID + "}"))
                .thenReturn(json);
        assertThat(manager.find(SESSION_ID)).contains(session);

        manager.deleteRequired(SESSION_ID);
        verify(redisTemplate).delete(
                java.util.List.of("biel:auth:session:v1:{" + SESSION_ID + "}"));
    }

    /**
     * 验证Redis与数据库活跃时间使用不同节流间隔。
     */
    @Test
    void throttlesRedisAndDatabaseTouchesIndependently() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        AuthSessionCacheProperties properties = new AuthSessionCacheProperties();
        properties.setEnabled(false);
        AuthorizationCacheProperties authorizationProperties =
                new AuthorizationCacheProperties();
        AuthSessionCacheManager manager = new AuthSessionCacheManager(
                provider, JsonMapper.builder().findAndAddModules().build(),
                properties, authorizationProperties);
        Instant now = Instant.parse("2026-07-31T08:00:00Z");
        AuthSessionCacheDTO session = session(now);

        assertThat(manager.shouldTouchRedis(session, now.plusSeconds(59))).isFalse();
        assertThat(manager.shouldTouchRedis(session, now.plusSeconds(60))).isTrue();
        assertThat(manager.shouldTouchDatabase(session, now.plusSeconds(299))).isFalse();
        assertThat(manager.shouldTouchDatabase(session, now.plusSeconds(300))).isTrue();
    }

    private AuthSessionCacheDTO session(Instant now) {
        return new AuthSessionCacheDTO(
                SESSION_ID, "ACTIVE", "MINI_PROGRAM", "WECHAT",
                now.plus(Duration.ofDays(30)),
                now.plus(Duration.ofDays(7)), 3L, 1001L,
                "E1001", "Test Employee", 2001L,
                "ACTIVE", "ACTIVE", 3L, now, now);
    }
}
