package com.biel.lifecamp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.biel.lifecamp.gateway.config.GatewayAuthProperties;
import com.biel.lifecamp.starter.security.RsaKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * 网关外部访问令牌认证过滤器测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
class GatewayAuthenticationFilterTest {
    private final Path keyDirectory = Path.of(
            "target", "test-keys", "gateway-auth-filter");

    /**
     * 验证网关认证失败发生在下游业务过滤链执行之前。
     *
     * @throws Exception 测试密钥写入失败时抛出
     */
    @Test
    void rejectsMissingTokenBeforeInvokingDownstream() throws Exception {
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
                properties(), new DefaultResourceLoader(), JsonMapper.builder().build());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/points/v1/grants").build());
        AtomicBoolean downstreamInvoked = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            downstreamInvoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_TOKEN_MISSING\"");
        assertThat(downstreamInvoked).isFalse();
    }

    /**
     * 验证可触发客户端自动续约的无效令牌错误不会进入下游业务处理。
     *
     * @throws Exception 测试密钥写入失败时抛出
     */
    @Test
    void rejectsInvalidTokenBeforeInvokingDownstream() throws Exception {
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
                properties(), new DefaultResourceLoader(), JsonMapper.builder().build());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/points/v1/grants")
                        .header("Authorization", "Bearer invalid-token")
                        .build());
        AtomicBoolean downstreamInvoked = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            downstreamInvoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"AUTH_TOKEN_INVALID\"");
        assertThat(downstreamInvoked).isFalse();
    }

    private GatewayAuthProperties properties() throws Exception {
        Files.createDirectories(keyDirectory);
        KeyPair external = RsaKeys.ephemeral();
        KeyPair internal = RsaKeys.ephemeral();
        GatewayAuthProperties properties = new GatewayAuthProperties();
        properties.setEnabled(true);
        properties.setExternalIssuer("biel-life-camp");
        properties.setExternalAudience("biel-life-camp-gateway");
        properties.setExternalPublicKeyLocation(writeKey(
                "external-public.pem", "PUBLIC KEY", external.getPublic()));
        properties.setInternalIssuer("biel-life-camp-gateway");
        properties.setInternalKeyId("test-internal-key");
        properties.setInternalPublicKeyLocation(writeKey(
                "internal-public.pem", "PUBLIC KEY", internal.getPublic()));
        properties.setInternalPrivateKeyLocation(writeKey(
                "internal-private.pem", "PRIVATE KEY", internal.getPrivate()));
        properties.setGatewayServiceToken(
                "gateway-service-token-for-filter-tests-1234567890");
        properties.setSystemAuthorizationUri(
                "http://127.0.0.1:8081/internal/system/v1/auth/session-context");
        properties.setRequireHttps(false);
        return properties;
    }

    private String writeKey(String fileName, String type, Key key) throws Exception {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(key.getEncoded());
        String pem = "-----BEGIN " + type + "-----\n"
                + encoded + "\n-----END " + type + "-----\n";
        Path file = keyDirectory.resolve(fileName);
        Files.writeString(file, pem, StandardCharsets.UTF_8);
        return file.toUri().toString();
    }
}
