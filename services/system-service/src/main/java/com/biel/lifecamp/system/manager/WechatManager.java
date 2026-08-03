package com.biel.lifecamp.system.manager;

/**
 * 封装服务端对微信身份接口的调用。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public interface WechatManager {
    /**
     * 使用登录凭证换取微信身份。
     *
     * @param loginCode 微信一次性登录凭证
     * @return 已由微信平台校验的身份
     */
    WechatSession exchangeLoginCode(String loginCode);

    /**
     * 使用手机号授权凭证换取已验证手机号。
     *
     * @param phoneCode 微信一次性手机号授权凭证
     * @return 已验证手机号
     */
    String exchangePhoneCode(String phoneCode);

    /**
     * 微信平台返回的已验证身份。
     *
     * @param openId 当前小程序范围内的 OpenID
     * @param unionId 可用时返回的跨应用 UnionID
     */
    record WechatSession(String openId, String unionId) {
    }
}
