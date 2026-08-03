package com.biel.lifecamp.system.manager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.config.properties.AdminPasswordProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 管理员密码登录 Redis 失败限流测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class AdminLoginRateLimiterTest {
    private ValueOperations<String, String> values;
    private AdminLoginRateLimiter rateLimiter;

    /**
     * 建立启用状态的双维度限流器。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        AdminPasswordProperties properties = new AdminPasswordProperties();
        properties.setRateLimitEnabled(true);
        properties.setMaxAttempts(5);
        SecretHashing hashing = mock(SecretHashing.class);
        when(hashing.identifier(anyString(), anyString())).thenReturn("safe-digest");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        rateLimiter = new AdminLoginRateLimiter(properties, hashing, redisTemplate);
    }

    /**
     * 达到阈值后必须在密码哈希校验前拒绝请求。
     */
    @Test
    void rejectsLoginWhenFailureThresholdReached() {
        when(values.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> rateLimiter.checkAllowed("admin", "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_LOGIN_RATE_LIMITED");
    }

    /**
     * 限流依赖故障时失败关闭，不能绕过暴力破解保护。
     */
    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(values.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThatThrownBy(() -> rateLimiter.checkAllowed("admin", "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_LOGIN_RATE_LIMIT_UNAVAILABLE");
    }
}

