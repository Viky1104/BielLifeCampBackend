package com.biel.lifecamp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关外部访问令牌校验与内部身份签名配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@ConfigurationProperties("platform.gateway-auth")
public final class GatewayAuthProperties {
    /** 是否启用网关认证。 */
    private boolean enabled;
    /** 外部访问令牌签发方。 */
    private String externalIssuer;
    /** 外部访问令牌受众。 */
    private String externalAudience;
    /** 外部访问令牌验签公钥位置。 */
    private String externalPublicKeyLocation;
    /** 内部身份令牌签发方。 */
    private String internalIssuer;
    /** 内部身份签名密钥标识。 */
    private String internalKeyId;
    /** 内部身份签名私钥位置。 */
    private String internalPrivateKeyLocation;
    /** 内部身份验签公钥位置。 */
    private String internalPublicKeyLocation;
    /** 网关调用系统服务时使用的服务凭证。 */
    private String gatewayServiceToken;
    /** 系统服务实时鉴权接口地址。 */
    private String systemAuthorizationUri;
    /** 是否强制鉴权接口使用 HTTPS。 */
    private boolean requireHttps = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getExternalIssuer() {
        return externalIssuer;
    }

    public void setExternalIssuer(String value) {
        externalIssuer = value;
    }

    public String getExternalAudience() {
        return externalAudience;
    }

    public void setExternalAudience(String value) {
        externalAudience = value;
    }

    public String getExternalPublicKeyLocation() {
        return externalPublicKeyLocation;
    }

    public void setExternalPublicKeyLocation(String value) {
        externalPublicKeyLocation = value;
    }

    public String getInternalIssuer() {
        return internalIssuer;
    }

    public void setInternalIssuer(String value) {
        internalIssuer = value;
    }

    public String getInternalKeyId() {
        return internalKeyId;
    }

    public void setInternalKeyId(String value) {
        internalKeyId = value;
    }

    public String getInternalPrivateKeyLocation() {
        return internalPrivateKeyLocation;
    }

    public void setInternalPrivateKeyLocation(String value) {
        internalPrivateKeyLocation = value;
    }

    public String getInternalPublicKeyLocation() {
        return internalPublicKeyLocation;
    }

    public void setInternalPublicKeyLocation(String value) {
        internalPublicKeyLocation = value;
    }

    public String getGatewayServiceToken() {
        return gatewayServiceToken;
    }

    public void setGatewayServiceToken(String value) {
        gatewayServiceToken = value;
    }

    public String getSystemAuthorizationUri() {
        return systemAuthorizationUri;
    }

    public void setSystemAuthorizationUri(String value) {
        systemAuthorizationUri = value;
    }

    public boolean isRequireHttps() {
        return requireHttps;
    }

    public void setRequireHttps(boolean value) {
        requireHttps = value;
    }
}
