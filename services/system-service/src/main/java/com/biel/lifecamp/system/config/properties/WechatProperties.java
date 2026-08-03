package com.biel.lifecamp.system.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序服务端集成配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@ConfigurationProperties("platform.wechat")
public class WechatProperties {
    /** 微信小程序标识。 */
    private String appId;
    /** 微信小程序密钥。 */
    private String appSecret;
    /** 微信开放接口基础地址。 */
    private String apiBaseUrl = "https://api.weixin.qq.com";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }
}
