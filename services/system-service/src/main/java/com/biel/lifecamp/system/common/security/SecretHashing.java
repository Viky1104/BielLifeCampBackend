package com.biel.lifecamp.system.common.security;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/**
 * 在持久化前保护外部身份标识和刷新令牌。
 *
 * <p>不同用途使用独立密钥材料和命名空间，避免摘要在不同业务域之间被关联。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class SecretHashing {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] identifierPepper;
    private final byte[] tokenPepper;

    public SecretHashing(AuthProperties properties) {
        this.identifierPepper = identifierPepper(properties);
        this.tokenPepper = properties.isEnabled()
                ? required(properties.getTokenPepper(), "AUTH_TOKEN_PEPPER") : new byte[32];
    }

    /**
     * 计算带业务命名空间的外部身份摘要。
     *
     * @param namespace 业务命名空间
     * @param value 原始外部标识
     * @return 十六进制 HMAC 摘要
     */
    public String identifier(String namespace, String value) {
        return hmac(identifierPepper, namespace + ":" + value);
    }

    /**
     * 计算刷新令牌摘要。
     *
     * @param value 原始刷新令牌
     * @return 十六进制 HMAC 摘要
     */
    public String token(String value) {
        return hmac(tokenPepper, "refresh:" + value);
    }

    /**
     * 生成高强度随机刷新令牌。
     *
     * @return 无填充的 URL 安全令牌
     */
    public String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 使用恒定时间算法比较敏感字符串，降低时序侧信道风险。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @return 两个非空值相同时返回 {@code true}
     */
    public boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate HMAC", ex);
        }
    }

    private byte[] required(String value, String name) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException(name + " must contain at least 32 characters");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] identifierPepper(AuthProperties properties) {
        if (StringUtils.hasText(properties.getIdentifierPepper())) {
            return required(properties.getIdentifierPepper(), "AUTH_IDENTIFIER_PEPPER");
        }
        return properties.isEnabled()
                ? required(null, "AUTH_IDENTIFIER_PEPPER") : new byte[32];
    }
}
