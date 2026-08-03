package com.biel.lifecamp.starter.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 版本化授权缓存配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@ConfigurationProperties("platform.security-context.authorization-cache")
public class AuthorizationCacheProperties {
    /** 是否启用 Redis 授权快照校验。 */
    private boolean enabled;
    /** 版本化授权快照键前缀。 */
    private String keyPrefix = "biel:security:authorization:v1";
    /** 员工当前权限版本键前缀。 */
    private String versionKeyPrefix = "biel:security:authz-version:v1";
    /** 授权快照有效期。 */
    private Duration ttl = Duration.ofMinutes(15);
    /** 当前权限版本有效期。 */
    private Duration versionTtl = Duration.ofMinutes(5);

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

    public String getVersionKeyPrefix() {
        return versionKeyPrefix;
    }

    public void setVersionKeyPrefix(String versionKeyPrefix) {
        this.versionKeyPrefix = versionKeyPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getVersionTtl() {
        return versionTtl;
    }

    public void setVersionTtl(Duration versionTtl) {
        this.versionTtl = versionTtl;
    }
}
