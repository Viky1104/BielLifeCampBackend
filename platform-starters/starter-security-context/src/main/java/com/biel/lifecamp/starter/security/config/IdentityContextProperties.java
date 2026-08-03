package com.biel.lifecamp.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 校验网关签发内部身份令牌所需的配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@ConfigurationProperties("platform.security-context")
public class IdentityContextProperties {
    /** 是否启用内部身份校验。 */
    private boolean enabled;
    /** 允许的内部身份令牌签发方。 */
    private String issuer = "biel-life-camp-gateway";
    /** 当前服务对应的令牌受众。 */
    private String audience;
    /** 内部身份令牌验签公钥位置。 */
    private String publicKeyLocation;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }
}
