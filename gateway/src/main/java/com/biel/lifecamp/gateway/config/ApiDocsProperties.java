package com.biel.lifecamp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knife4j 聚合文档开关及访问保护配置。
 *
 * <p>文档能力默认关闭。ACK 环境启用时必须同时启用 Basic 认证，并通过 Secret 注入凭据。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@ConfigurationProperties("platform.api-docs")
public final class ApiDocsProperties {
    /** 是否开放 Knife4j 页面及下游 OpenAPI 文档代理。 */
    private boolean enabled;
    /** 是否对全部文档资源启用 HTTP Basic 认证。 */
    private boolean basicEnabled = true;
    /** 文档访问用户名，不应写入仓库或 Nacos 明文配置。 */
    private String username;
    /** 文档访问密码，由 Kubernetes Secret 注入。 */
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isBasicEnabled() {
        return basicEnabled;
    }

    public void setBasicEnabled(boolean value) {
        basicEnabled = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String value) {
        password = value;
    }
}
