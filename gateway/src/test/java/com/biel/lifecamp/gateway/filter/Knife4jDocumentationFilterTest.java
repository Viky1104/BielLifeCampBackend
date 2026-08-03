package com.biel.lifecamp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.gateway.config.ApiDocsProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Knife4j 文档访问开关与 Basic 认证过滤器测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class Knife4jDocumentationFilterTest {
    /**
     * 文档关闭时，即使静态资源存在也必须对外表现为资源不存在。
     */
    @Test
    void returnsNotFoundWhenDocumentationIsDisabled() {
        ApiDocsProperties properties = new ApiDocsProperties();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/doc.html"));

        new Knife4jDocumentationFilter(properties, JsonMapper.builder().build())
                .filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"COMMON_RESOURCE_NOT_FOUND\"")
                .contains("\"data\":null");
    }

    /**
     * 文档启用且 Basic 认证失败时，应返回认证挑战而不是继续访问资源。
     */
    @Test
    void rejectsInvalidBasicCredentials() {
        ApiDocsProperties properties = securedProperties();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v3/api-docs/swagger-config")
                        .header(HttpHeaders.AUTHORIZATION, "Basic invalid"));

        new Knife4jDocumentationFilter(properties, JsonMapper.builder().build())
                .filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("Basic realm=");
    }

    /**
     * 正确凭据只能放行文档请求，认证值不会被记录或写入响应。
     */
    @Test
    void acceptsValidBasicCredentials() {
        ApiDocsProperties properties = securedProperties();
        String token = Base64.getEncoder().encodeToString(
                "api-docs:long-test-password".getBytes(StandardCharsets.UTF_8));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/doc.html")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + token));
        AtomicBoolean invoked = new AtomicBoolean();

        new Knife4jDocumentationFilter(properties, JsonMapper.builder().build())
                .filter(exchange, ignored -> {
                    invoked.set(true);
                    return Mono.empty();
                }).block();

        assertThat(invoked).isTrue();
    }

    /**
     * Nacos 在应用启动后开启文档时，过滤器必须使用刷新后的开关和凭据。
     */
    @Test
    void acceptsCredentialsConfiguredAfterFilterInitialization() {
        ApiDocsProperties properties = new ApiDocsProperties();
        Knife4jDocumentationFilter filter = new Knife4jDocumentationFilter(
                properties, JsonMapper.builder().build());
        properties.setEnabled(true);
        properties.setBasicEnabled(true);
        properties.setUsername("api-docs");
        properties.setPassword("long-test-password");
        String token = Base64.getEncoder().encodeToString(
                "api-docs:long-test-password".getBytes(StandardCharsets.UTF_8));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/doc.html")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + token));
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
    }

    /**
     * 开启访问保护却未配置强凭据时必须启动失败，避免文档被意外公开。
     */
    @Test
    void rejectsMissingSecuredDocumentationCredentials() {
        ApiDocsProperties properties = new ApiDocsProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new Knife4jDocumentationFilter(
                properties, JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KNIFE4J_BASIC_USERNAME");
    }

    private ApiDocsProperties securedProperties() {
        ApiDocsProperties properties = new ApiDocsProperties();
        properties.setEnabled(true);
        properties.setBasicEnabled(true);
        properties.setUsername("api-docs");
        properties.setPassword("long-test-password");
        return properties;
    }
}
