package com.biel.lifecamp.system.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import org.junit.jupiter.api.Test;

/**
 * 外部标识摘要测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
class SecretHashingTest {

    /**
     * 验证认证关闭但显式配置摘要密钥时仍使用该稳定密钥。
     */
    @Test
    void usesConfiguredIdentifierPepperWhenAuthenticationIsDisabled() {
        SecretHashing first = new SecretHashing(properties(
                "identifier-pepper-A-12345678901234567890"));
        SecretHashing second = new SecretHashing(properties(
                "identifier-pepper-B-12345678901234567890"));

        assertThat(first.identifier("mobile", "+8613800138000"))
                .isNotEqualTo(second.identifier("mobile", "+8613800138000"));
    }

    private AuthProperties properties(String identifierPepper) {
        AuthProperties properties = new AuthProperties();
        properties.setEnabled(false);
        properties.setIdentifierPepper(identifierPepper);
        return properties;
    }
}
