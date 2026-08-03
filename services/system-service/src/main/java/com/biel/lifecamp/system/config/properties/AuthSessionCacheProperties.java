package com.biel.lifecamp.system.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 在线会话缓存配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@ConfigurationProperties("platform.auth.session-cache")
public class AuthSessionCacheProperties {
    /** 是否启用 Redis 在线会话主路径。 */
    private boolean enabled;
    /** 在线会话键前缀。 */
    private String keyPrefix = "biel:auth:session:v1";
    /** 高频访问下 Redis 会话最小改写间隔。 */
    private Duration redisTouchInterval = Duration.ofMinutes(1);
    /** 数据库会话活跃时间最小持久化间隔。 */
    private Duration databaseTouchInterval = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getRedisTouchInterval() {
        return redisTouchInterval;
    }

    public void setRedisTouchInterval(Duration redisTouchInterval) {
        this.redisTouchInterval = redisTouchInterval;
    }

    public Duration getDatabaseTouchInterval() {
        return databaseTouchInterval;
    }

    public void setDatabaseTouchInterval(Duration databaseTouchInterval) {
        this.databaseTouchInterval = databaseTouchInterval;
    }
}
