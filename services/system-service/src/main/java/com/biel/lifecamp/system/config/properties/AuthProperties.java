package com.biel.lifecamp.system.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证、令牌和会话配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@ConfigurationProperties("platform.auth")
public class AuthProperties {
    /** 是否启用认证功能。 */
    private boolean enabled;
    /** 访问令牌签发方。 */
    private String issuer = "biel-life-camp";
    /** 访问令牌受众。 */
    private String audience = "biel-life-camp-gateway";
    /** 访问令牌签名密钥标识。 */
    private String keyId;
    /** 访问令牌签名私钥位置。 */
    private String privateKeyLocation;
    /** 访问令牌验签公钥位置。 */
    private String publicKeyLocation;
    /** 是否允许使用临时密钥，仅限测试环境。 */
    private boolean allowEphemeralKeys;
    /** 外部身份摘要使用的服务端密钥材料。 */
    private String identifierPepper;
    /** 外部身份可恢复密文使用的 Base64 编码 256 位密钥。 */
    private String identityEncryptionKey;
    /** 刷新令牌摘要使用的服务端密钥材料。 */
    private String tokenPepper;
    /** 是否要求 EHR 首次同步完成后才允许登录。 */
    private boolean ehrRequireInitialSync = true;
    /** 网关调用内部鉴权接口的共享服务凭证。 */
    private String gatewayServiceToken;
    /** 访问令牌有效期。 */
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    /** 会话绝对有效期。 */
    private Duration sessionAbsoluteTtl = Duration.ofDays(30);
    /** 会话空闲有效期。 */
    private Duration sessionIdleTtl = Duration.ofDays(7);
    /** 分布式长整型标识生成器的节点编号。 */
    private int nodeId;

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

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(String value) {
        this.privateKeyLocation = value;
    }

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String value) {
        this.publicKeyLocation = value;
    }

    public boolean isAllowEphemeralKeys() {
        return allowEphemeralKeys;
    }

    public void setAllowEphemeralKeys(boolean value) {
        this.allowEphemeralKeys = value;
    }

    public String getIdentifierPepper() {
        return identifierPepper;
    }

    public void setIdentifierPepper(String value) {
        this.identifierPepper = value;
    }

    public String getIdentityEncryptionKey() {
        return identityEncryptionKey;
    }

    public void setIdentityEncryptionKey(String value) {
        this.identityEncryptionKey = value;
    }

    public String getTokenPepper() {
        return tokenPepper;
    }

    public void setTokenPepper(String value) {
        this.tokenPepper = value;
    }

    public boolean isEhrRequireInitialSync() {
        return ehrRequireInitialSync;
    }

    public void setEhrRequireInitialSync(boolean value) {
        this.ehrRequireInitialSync = value;
    }

    public String getGatewayServiceToken() {
        return gatewayServiceToken;
    }

    public void setGatewayServiceToken(String value) {
        this.gatewayServiceToken = value;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration value) {
        this.accessTokenTtl = value;
    }

    public Duration getSessionAbsoluteTtl() {
        return sessionAbsoluteTtl;
    }

    public void setSessionAbsoluteTtl(Duration value) {
        this.sessionAbsoluteTtl = value;
    }

    public Duration getSessionIdleTtl() {
        return sessionIdleTtl;
    }

    public void setSessionIdleTtl(Duration value) {
        this.sessionIdleTtl = value;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int value) {
        this.nodeId = value;
    }
}
