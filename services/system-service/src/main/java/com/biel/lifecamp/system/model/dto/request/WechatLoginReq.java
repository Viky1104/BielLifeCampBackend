package com.biel.lifecamp.system.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 微信小程序登录请求。
 *
 * @param loginCode 微信一次性登录凭证
 * @param phoneCode 首次绑定时使用的一次性手机号授权凭证
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Schema(name = "WechatLoginRequest", description = "微信小程序登录请求。已有有效绑定时"
        + " phoneCode 可省略；首次绑定时必须提供。")
public record WechatLoginReq(
        @Schema(description = "wx.login 返回的一次性登录凭证",
                example = "0a3XExampleLoginCode")
        @NotBlank @Size(max = 512) String loginCode,
        @Schema(description = "手机号快速验证组件返回的一次性授权凭证；"
                + "仅首次员工绑定时使用，客户端不得提交手机号明文",
                example = "0f8YExamplePhoneCode")
        @Size(min = 16, max = 512) String phoneCode) {
}
