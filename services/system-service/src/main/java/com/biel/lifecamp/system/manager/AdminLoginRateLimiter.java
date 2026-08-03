package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.config.properties.AdminPasswordProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 管理员密码登录的账号和来源地址双维度 Redis 限流器。
 *
 * <p>Redis 仅保存不可逆标识摘要和短期失败次数，不保存工号、密码或令牌。
 * 限流启用时 Redis 故障按失败关闭处理，避免依赖故障绕过暴力破解保护。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Component
public final class AdminLoginRateLimiter {
    private static final String KEY_PREFIX = "biel:security:admin-login:v1:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """, Long.class);
    private final AdminPasswordProperties properties;
    private final SecretHashing secretHashing;
    private final StringRedisTemplate redisTemplate;

    public AdminLoginRateLimiter(AdminPasswordProperties properties,
                                 SecretHashing secretHashing,
                                 StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.secretHashing = secretHashing;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 在执行昂贵密码哈希校验前检查两个维度的失败次数。
     *
     * @param employeeNo 登录工号
     * @param sourceIp 请求来源地址
     */
    public void checkAllowed(String employeeNo, String sourceIp) {
        if (!properties.isRateLimitEnabled()) {
            return;
        }
        try {
            if (count(accountKey(employeeNo)) >= properties.getMaxAttempts()
                    || count(ipKey(sourceIp)) >= properties.getMaxAttempts()) {
                throw AuthException.unauthorized("AUTH_LOGIN_RATE_LIMITED");
            }
        } catch (DataAccessException ex) {
            throw AuthException.unavailable("AUTH_LOGIN_RATE_LIMIT_UNAVAILABLE");
        }
    }

    /**
     * 记录一次失败认证。两个键分别执行原子递增和首次过期时间设置。
     *
     * @param employeeNo 登录工号
     * @param sourceIp 请求来源地址
     */
    public void recordFailure(String employeeNo, String sourceIp) {
        if (!properties.isRateLimitEnabled()) {
            return;
        }
        long seconds = Math.max(1, properties.getWindow().toSeconds());
        try {
            redisTemplate.execute(
                    INCREMENT_SCRIPT, List.of(accountKey(employeeNo)), Long.toString(seconds));
            redisTemplate.execute(
                    INCREMENT_SCRIPT, List.of(ipKey(sourceIp)), Long.toString(seconds));
        } catch (DataAccessException ex) {
            throw AuthException.unavailable("AUTH_LOGIN_RATE_LIMIT_UNAVAILABLE");
        }
    }

    /**
     * 登录成功后清理账号维度失败计数；来源地址计数保留以约束批量账号探测。
     *
     * @param employeeNo 已成功认证的工号
     */
    public void clearAccount(String employeeNo) {
        if (!properties.isRateLimitEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(accountKey(employeeNo));
        } catch (DataAccessException ex) {
            throw AuthException.unavailable("AUTH_LOGIN_RATE_LIMIT_UNAVAILABLE");
        }
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw AuthException.unavailable("AUTH_LOGIN_RATE_LIMIT_UNAVAILABLE");
        }
    }

    private String accountKey(String employeeNo) {
        String canonical = employeeNo == null
                ? "" : employeeNo.trim().toUpperCase(Locale.ROOT);
        return KEY_PREFIX + "account:" + secretHashing.identifier("admin-account", canonical);
    }

    private String ipKey(String sourceIp) {
        String canonical = sourceIp == null ? "unknown" : sourceIp.trim();
        return KEY_PREFIX + "ip:" + secretHashing.identifier("admin-ip", canonical);
    }
}

