package com.biel.lifecamp.starter.security.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 版本化授权键、TTL 和 JSON 序列化测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class RedisAuthorizationCacheStoreTest {
    /**
     * 验证授权按员工、目标服务和版本隔离，并单独保存当前版本。
     *
     * @throws Exception JSON 处理失败时抛出
     */
    @Test
    void storesVersionedReviewableAuthorizationJson() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        RedisAuthorizationCacheStore store =
                new RedisAuthorizationCacheStore(redisTemplate, objectMapper, properties);
        CachedAuthorization authorization = authorization();

        store.save(authorization);
        store.saveCurrentVersion("1001", 3L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("biel:security:authorization:v1:{1001}:system-service:3"),
                jsonCaptor.capture(), eq(Duration.ofMinutes(15)));
        verify(valueOperations).set(
                "biel:security:authz-version:v1:{1001}",
                "3", Duration.ofMinutes(5));
        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"employeeNo\":\"E1001\"");
        assertThat(json).doesNotContain(
                "accessToken", "refreshToken", "phone", "openId", "sessionId");

        when(valueOperations.get(anyString())).thenReturn(json);
        assertThat(store.find("1001", "system-service", 3L))
                .contains(authorization);
    }

    private CachedAuthorization authorization() {
        return new CachedAuthorization(
                "1001", "E1001", "Test Employee", "2001",
                "system-service", 3L, Set.of("EMPLOYEE"),
                Set.of("system:profile:read"),
                List.of(new IdentityContext.DataScope("SELF", "1001")),
                Instant.parse("2026-07-31T08:00:00Z"));
    }
}
