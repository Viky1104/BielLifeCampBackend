package com.biel.lifecamp.system.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理后台本地密码认证配置。
 *
 * <p>该认证方式默认关闭，生产目标方案仍是企业统一身份认证。本配置用于开发、
 * 初始部署和经过审批的应急账号。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@ConfigurationProperties("platform.auth.admin-password")
public class AdminPasswordProperties {
    /** 是否启用管理后台本地密码登录。 */
    private boolean enabled;
    /** 是否启用 Redis 登录失败限流。 */
    private boolean rateLimitEnabled = true;
    /** 单个账号或来源地址在窗口内允许的最大失败次数。 */
    private int maxAttempts = 5;
    /** 登录失败计数窗口。 */
    private Duration window = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}

