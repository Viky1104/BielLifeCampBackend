package com.biel.lifecamp.system.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * 外部身份标识加密测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
class SecretEncryptionTest {

    /**
     * 验证随机 IV 使同一原文产生不同密文且均可恢复。
     */
    @Test
    void encryptsWithRandomIvAndDecrypts() {
        SecretEncryption encryption = new SecretEncryption(propertiesWithKey((byte) 7));

        byte[] first = encryption.encrypt("openid-1001");
        byte[] second = encryption.encrypt("openid-1001");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryption.decrypt(first)).isEqualTo("openid-1001");
        assertThat(encryption.decrypt(second)).isEqualTo("openid-1001");
    }

    /**
     * 验证非 256 位密钥会在启动阶段失败。
     */
    @Test
    void rejectsInvalidKeyLength() {
        AuthProperties properties = new AuthProperties();
        properties.setEnabled(true);
        properties.setIdentityEncryptionKey(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> new SecretEncryption(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private AuthProperties propertiesWithKey(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        AuthProperties properties = new AuthProperties();
        properties.setEnabled(true);
        properties.setIdentityEncryptionKey(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
