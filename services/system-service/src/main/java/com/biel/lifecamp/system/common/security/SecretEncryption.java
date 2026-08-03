package com.biel.lifecamp.system.common.security;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 使用 AES-GCM 加密需要受控恢复的外部身份标识。
 *
 * <p>每次加密生成独立随机 IV，持久化内容按“版本、IV、密文和认证标签”排列。
 * 检索仍使用独立 HMAC 摘要，避免使用可预测密文作为索引。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public final class SecretEncryption {
    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    /**
     * 根据认证配置创建外部身份加密器。
     *
     * @param properties 认证配置
     */
    public SecretEncryption(AuthProperties properties) {
        byte[] keyBytes = properties.isEnabled()
                ? decodeKey(properties.getIdentityEncryptionKey()) : new byte[32];
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密外部身份原文。
     *
     * @param plaintext 外部身份原文
     * @return 包含版本和随机 IV 的密文
     */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                    .put(FORMAT_VERSION).put(iv).put(ciphertext).array();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt external identity", ex);
        }
    }

    /**
     * 解密受保护的外部身份标识。
     *
     * @param protectedValue 版本化密文
     * @return 外部身份原文
     */
    public String decrypt(byte[] protectedValue) {
        if (protectedValue == null) {
            return null;
        }
        if (protectedValue.length <= 1 + IV_LENGTH_BYTES
                || protectedValue[0] != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported external identity ciphertext");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(protectedValue, 1, iv, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[protectedValue.length - 1 - IV_LENGTH_BYTES];
            System.arraycopy(protectedValue, 1 + IV_LENGTH_BYTES,
                    ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decrypt external identity", ex);
        }
    }

    private byte[] decodeKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "AUTH_IDENTITY_ENCRYPTION_KEY must decode to 32 bytes");
            }
            return keyBytes;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "AUTH_IDENTITY_ENCRYPTION_KEY must be valid Base64", ex);
        }
    }
}
