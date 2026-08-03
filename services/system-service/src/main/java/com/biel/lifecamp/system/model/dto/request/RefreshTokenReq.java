package com.biel.lifecamp.system.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新令牌轮换请求。
 *
 * @param refreshToken 上一次登录或轮换返回的刷新令牌
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Schema(name = "RefreshTokenRequest", description = "刷新令牌轮换请求")
public record RefreshTokenReq(
        @Schema(description = "上一次登录或轮换返回的原始刷新令牌",
                format = "password", example = "refresh-token-value")
        @NotBlank @Size(min = 32, max = 1024) String refreshToken) {
}
