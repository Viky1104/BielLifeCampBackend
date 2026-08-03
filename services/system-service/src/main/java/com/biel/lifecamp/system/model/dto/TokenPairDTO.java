package com.biel.lifecamp.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 登录或令牌轮换后返回的访问凭证与刷新凭证。
 *
 * @param tokenType 访问令牌认证方案
 * @param accessToken 已签名的访问令牌
 * @param accessExpiresIn 访问令牌有效秒数
 * @param refreshToken 仅向客户端返回一次的原始刷新令牌
 * @param refreshExpiresAt 刷新令牌到期时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Schema(name = "TokenPair", description = "登录或刷新成功后签发的访问令牌和刷新令牌")
public record TokenPairDTO(
        @Schema(description = "Authorization 请求头使用的认证方案",
                example = "Bearer")
        String tokenType,
        @Schema(description = "短时访问 JWT", format = "password",
                example = "eyJhbGciOiJSUzI1NiJ9.example.signature")
        String accessToken,
        @Schema(description = "访问令牌剩余有效秒数", example = "900")
        Long accessExpiresIn,
        @Schema(description = "本次仅返回一次的原始刷新令牌", format = "password",
                example = "refresh-token-value")
        String refreshToken,
        @Schema(description = "刷新令牌绝对到期时间",
                example = "2026-08-30T08:00:00Z")
        Instant refreshExpiresAt) {
}
