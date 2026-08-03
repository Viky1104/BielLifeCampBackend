package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.starter.security.RsaKeys;
import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * 根据实时授权快照创建已签名访问令牌。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class AuthTokenManager {
    private final AuthProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;

    public AuthTokenManager(AuthProperties properties, Clock clock, ResourceLoader loader) {
        this.properties = properties;
        this.clock = clock;
        this.encoder = properties.isEnabled() ? encoder(properties, loader) : null;
    }

    /**
     * 为指定会话签发访问令牌。
     *
     * <p>令牌只携带身份、会话标识和权限版本；具体权限由网关按请求实时查询。</p>
     *
     * @param snapshot 当前员工授权快照
     * @param sessionId 会话标识
     * @param clientType 客户端类型
     * @param authenticationMethod 认证方式
     * @return 已签名访问令牌
     */
    public String issueAccessToken(AuthorizationSnapshotDTO snapshot, String sessionId,
                                   String clientType, String authenticationMethod) {
        if (encoder == null) {
            throw AuthException.unavailable("AUTH_DISABLED");
        }
        Instant now = clock.instant();
        EmployeeDTO employee = snapshot.employee();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .subject(Long.toString(employee.id()))
                .issuedAt(now)
                .notBefore(now.minusSeconds(5))
                .expiresAt(now.plus(properties.getAccessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("authz_ver", employee.authzVersion())
                .claim("client_type", clientType)
                .claim("amr", List.of(authenticationMethod.toLowerCase()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getKeyId())
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtEncoder encoder(AuthProperties properties, ResourceLoader loader) {
        if (!StringUtils.hasText(properties.getKeyId())) {
            throw new IllegalStateException("AUTH_KEY_ID is required");
        }
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (properties.isAllowEphemeralKeys()) {
            // 临时密钥仅供测试环境使用，生产环境必须加载稳定的外部密钥。
            KeyPair pair = RsaKeys.ephemeral();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } else {
            publicKey = RsaKeys.readPublicKey(loader, properties.getPublicKeyLocation());
            privateKey = RsaKeys.readPrivateKey(loader, properties.getPrivateKeyLocation());
        }
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getKeyId())
                .build();
        ImmutableJWKSet<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }
}
