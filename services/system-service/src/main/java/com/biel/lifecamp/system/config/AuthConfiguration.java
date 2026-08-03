package com.biel.lifecamp.system.config;

import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.common.security.SecretEncryption;
import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.config.properties.AdminPasswordProperties;
import com.biel.lifecamp.system.config.properties.AuthSessionCacheProperties;
import com.biel.lifecamp.system.config.properties.WechatProperties;
import com.biel.lifecamp.system.manager.AuthTokenManager;
import com.biel.lifecamp.system.manager.WechatManager;
import com.biel.lifecamp.system.manager.impl.WechatManagerImpl;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * 认证协作组件与外部微信集成配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthProperties.class,
        AdminPasswordProperties.class,
        AuthSessionCacheProperties.class,
        WechatProperties.class
})
public class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    LongIdGenerator longIdGenerator(AuthProperties properties) {
        return new LongIdGenerator(properties.getNodeId());
    }

    @Bean
    SecretHashing secretHashing(AuthProperties properties) {
        return new SecretHashing(properties);
    }

    @Bean
    SecretEncryption secretEncryption(AuthProperties properties) {
        return new SecretEncryption(properties);
    }

    @Bean
    WechatManager wechatManager(WechatProperties properties, ObjectMapper objectMapper) {
        return new WechatManagerImpl(properties, objectMapper);
    }

    @Bean
    AuthTokenManager authTokenManager(AuthProperties properties, Clock clock,
                                      ResourceLoader resourceLoader) {
        return new AuthTokenManager(properties, clock, resourceLoader);
    }

    /**
     * 使用带算法前缀的 BCrypt 哈希，便于未来平滑升级密码算法。
     *
     * @return 本地管理员密码编码器
     */
    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return new DelegatingPasswordEncoder(
                "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
