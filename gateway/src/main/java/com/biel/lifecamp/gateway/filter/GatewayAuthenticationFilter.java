package com.biel.lifecamp.gateway.filter;

import com.biel.lifecamp.starter.security.InternalHeaders;
import com.biel.lifecamp.starter.security.RsaKeys;
import com.biel.lifecamp.gateway.config.GatewayAuthProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 校验外部访问令牌、获取实时权限，并签发面向下游服务的内部身份上下文。
 *
 * <p>网关只负责认证与可信身份传递，具体接口授权仍由目标服务执行。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Component
public final class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/system/v1/auth/wechat/login",
            "/api/system/v1/auth/admin/login",
            "/api/system/v1/auth/token/refresh");
    private final GatewayAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final JwtDecoder externalDecoder;
    private final JwtEncoder internalEncoder;

    public GatewayAuthenticationFilter(GatewayAuthProperties properties, ResourceLoader loader,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
        if (properties.isEnabled()) {
            validateConfiguration(properties);
            this.externalDecoder = externalDecoder(properties, loader);
            this.internalEncoder = internalEncoder(properties, loader);
        } else {
            this.externalDecoder = null;
            this.internalEncoder = null;
        }
    }

    /**
     * 校验受保护请求并将短期内部身份令牌注入下游请求。
     *
     * @param exchange 当前网关请求
     * @param chain 网关过滤器链
     * @return 异步处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!properties.isEnabled() || !path.startsWith("/api/") || PUBLIC_PATHS.contains(path)
                || exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING");
        }
        Jwt jwt;
        try {
            jwt = externalDecoder.decode(authorization.substring(7));
        } catch (JwtException ex) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID");
        }
        String targetService = targetService(path);
        if (targetService == null) {
            return error(exchange.getResponse(), HttpStatus.NOT_FOUND, "COMMON_ROUTE_NOT_FOUND");
        }
        Number authzVersion = jwt.getClaim("authz_ver");
        String sessionId = jwt.getClaimAsString("sid");
        if (authzVersion == null || !StringUtils.hasText(sessionId)) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID");
        }
        long employeeId;
        try {
            employeeId = Long.parseLong(jwt.getSubject());
        } catch (RuntimeException ex) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID");
        }
        SessionContextRequest request = new SessionContextRequest(employeeId, sessionId,
                authzVersion.longValue(), targetService);
        // 每次请求都向系统服务读取实时权限，避免仅依赖访问令牌中的历史权限快照。
        return authorize(request).flatMap(context -> {
            String internalJws = issueInternalIdentity(context, targetService);
            ServerHttpRequest trusted = exchange.getRequest().mutate()
                    .header(InternalHeaders.IDENTITY, internalJws).build();
            return chain.filter(exchange.mutate().request(trusted).build());
        }).onErrorResume(AuthorizationFailure.class,
                failure -> error(exchange.getResponse(), failure.status(), failure.code()))
          .onErrorResume(ex -> error(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
                  "AUTH_AUTHORIZATION_UNAVAILABLE"));
    }

    private Mono<SessionContext> authorize(SessionContextRequest request) {
        return webClient.post().uri(properties.getSystemAuthorizationUri())
                .header("X-Gateway-Service-Token", properties.getGatewayServiceToken())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(
                                new ParameterizedTypeReference<
                                        GatewayApiResponse<SessionContext>>() {
                                }).flatMap(body -> {
                                    if (!"0".equals(body.code())
                                            || body.data() == null) {
                                        return Mono.error(new AuthorizationFailure(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "AUTH_AUTHORIZATION_UNAVAILABLE"));
                                    }
                                    return Mono.just(body.data());
                                });
                    }
                    HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                    String code = response.statusCode().value() == 409 ? "AUTHZ_STALE"
                            : response.statusCode().value() == 401 ? "AUTH_SESSION_REVOKED"
                            : response.statusCode().value() == 403 ? "AUTH_ACCOUNT_FORBIDDEN"
                            : "AUTH_AUTHORIZATION_UNAVAILABLE";
                    return response.releaseBody().then(Mono.error(new AuthorizationFailure(
                            status == null ? HttpStatus.SERVICE_UNAVAILABLE : status, code)));
                }).timeout(Duration.ofSeconds(3));
    }

    private String issueInternalIdentity(SessionContext context, String targetService) {
        Instant now = Instant.now();
        // 内部身份令牌仅对单一目标服务有效，并限制为 60 秒，降低泄露后的横向移动风险。
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getInternalIssuer())
                .audience(List.of(targetService)).subject(context.employeeId())
                .issuedAt(now).notBefore(now.minusSeconds(2)).expiresAt(now.plusSeconds(60))
                .id(UUID.randomUUID().toString()).claim("org_id", context.organizationId())
                .claim("sid", context.sessionId())
                .claim("client_type", context.clientType())
                .claim("authz_ver", context.authzVersion())
                .claim("role_codes", context.roleCodes()).claim("permissions", context.permissions())
                .claim("data_scopes", context.dataScopes()).claim("amr", context.amr()).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getInternalKeyId()).build();
        return internalEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtDecoder externalDecoder(GatewayAuthProperties properties, ResourceLoader loader) {
        RSAPublicKey publicKey = RsaKeys.readPublicKey(loader, properties.getExternalPublicKeyLocation());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getExternalIssuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.getExternalAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private JwtEncoder internalEncoder(GatewayAuthProperties properties, ResourceLoader loader) {
        RSAPublicKey publicKey = RsaKeys.readPublicKey(loader, properties.getInternalPublicKeyLocation());
        RSAPrivateKey privateKey = RsaKeys.readPrivateKey(loader, properties.getInternalPrivateKeyLocation());
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(properties.getInternalKeyId()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    private void validateConfiguration(GatewayAuthProperties value) {
        URI uri = URI.create(value.getSystemAuthorizationUri());
        // 认证开启时拒绝明文传输服务凭证和实时权限数据。
        if (value.isRequireHttps() && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("SYSTEM_AUTHORIZATION_URI must use HTTPS when Gateway auth is enabled");
        }
        if (!StringUtils.hasText(value.getGatewayServiceToken()) || value.getGatewayServiceToken().length() < 32) {
            throw new IllegalStateException("AUTH_GATEWAY_SERVICE_TOKEN must contain at least 32 characters");
        }
    }

    private String targetService(String path) {
        if (path.startsWith("/api/system/")) {
            return "system-service";
        }
        if (path.startsWith("/api/communications/")) {
            return "communication-service";
        }
        if (path.startsWith("/api/workbench/")) {
            return "workbench-service";
        }
        if (path.startsWith("/api/points/")) {
            return "points-service";
        }
        if (path.startsWith("/api/activities/")) {
            return "activity-service";
        }
        if (path.startsWith("/api/community/")) {
            return "community-service";
        }
        if (path.startsWith("/api/mall/")) {
            return "mall-service";
        }
        if (path.startsWith("/api/life/")) {
            return "life-service";
        }
        if (path.startsWith("/api/order-views/")) {
            return "order-view-service";
        }
        return null;
    }

    private Mono<Void> error(ServerHttpResponse response, HttpStatus status, String code) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                    new GatewayApiResponse<>(
                            code, "Request authentication failed", null));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    record SessionContextRequest(long employeeId, String sessionId, long authzVersion, String targetService) { }
    record GatewayApiResponse<T>(String code, String errorMsg, T data) { }
    record DataScope(String type, String value) { }
    record SessionContext(String employeeId, String organizationId, String sessionId,
                          String clientType, long authzVersion,
                          List<String> roleCodes, List<String> permissions, List<DataScope> dataScopes,
                          List<String> amr) { }
    private static final class AuthorizationFailure extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        private AuthorizationFailure(HttpStatus status, String code) {
            this.status = status;
            this.code = code;
        }

        HttpStatus status() {
            return status;
        }

        String code() {
            return code;
        }
    }
}
