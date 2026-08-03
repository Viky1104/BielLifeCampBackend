package com.biel.lifecamp.starter.security.config;

import com.biel.lifecamp.starter.security.RsaKeys;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import com.biel.lifecamp.starter.security.filter.IdentityContextFilter;
import com.biel.lifecamp.starter.security.redis.RedisAuthorizationCacheStore;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.ObjectMapper;

/**
 * 为 Servlet 应用自动配置内部身份校验，并保留显式启用开关。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
        IdentityContextProperties.class,
        AuthorizationCacheProperties.class
})
public class SecurityContextAutoConfiguration {
    /**
     * 启用后使用 StringRedisTemplate 和显式 JSON 保存版本化授权，禁止 Java 原生序列化。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "platform.security-context.authorization-cache",
            name = "enabled",
            havingValue = "true")
    AuthorizationCacheStore authorizationCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AuthorizationCacheProperties properties) {
        return new RedisAuthorizationCacheStore(redisTemplate, objectMapper, properties);
    }

    @Bean
    IdentityContextFilter identityContextFilter(IdentityContextProperties properties,
                                                AuthorizationCacheProperties cacheProperties,
                                                ObjectProvider<AuthorizationCacheStore> storeProvider,
                                                ResourceLoader resourceLoader,
                                                ObjectMapper objectMapper) {
        JwtDecoder decoder = properties.isEnabled() ? decoder(properties, resourceLoader) : null;
        return new IdentityContextFilter(
                properties, cacheProperties, storeProvider.getIfAvailable(),
                decoder, objectMapper);
    }

    private JwtDecoder decoder(IdentityContextProperties properties, ResourceLoader resourceLoader) {
        RSAPublicKey key = RsaKeys.readPublicKey(resourceLoader, properties.getPublicKeyLocation());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }
}
