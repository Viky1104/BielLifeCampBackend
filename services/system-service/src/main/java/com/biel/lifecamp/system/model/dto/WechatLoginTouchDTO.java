package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 更新微信身份最近成功登录时间所需的参数。
 *
 * @param appId 微信小程序标识
 * @param subjectHash 经保护的 OpenID 摘要
 * @param now 成功登录时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record WechatLoginTouchDTO(String appId, String subjectHash, Instant now) {
}
