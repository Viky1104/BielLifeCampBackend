package com.biel.lifecamp.gateway.filter;

import com.biel.lifecamp.gateway.config.ApiDocsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 控制 Knife4j 页面、聚合配置和下游 OpenAPI 代理的访问边界。
 *
 * <p>文档关闭时统一返回 404；启用 Basic 认证后，所有文档资源必须携带正确凭据。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Component
public final class Knife4jDocumentationFilter implements GlobalFilter, Ordered {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BASIC_REALM = "Basic realm=\"Biel Life Camp API Docs\"";
    private final ApiDocsProperties properties;
    private final ObjectMapper objectMapper;

    public Knife4jDocumentationFilter(ApiDocsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validateConfiguration(properties);
    }

    /**
     * 对文档资源执行开关和 Basic 凭据校验，非文档请求不受影响。
     *
     * @param exchange 当前网关请求
     * @param chain 网关过滤器链
     * @return 异步过滤结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isDocumentationPath(path)) {
            return chain.filter(exchange);
        }
        if (!properties.isEnabled()) {
            return error(exchange.getResponse(), HttpStatus.NOT_FOUND,
                    "COMMON_RESOURCE_NOT_FOUND", "Resource not found");
        }
        if (!properties.isBasicEnabled()) {
            return chain.filter(exchange);
        }
        if (!hasValidCredentials(properties)) {
            return error(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
                    "API_DOCS_CONFIGURATION_INVALID",
                    "API documentation credentials are not configured");
        }
        String authorization = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);
        if (!matchesExpectedAuthorization(authorization)) {
            exchange.getResponse().getHeaders().set(
                    HttpHeaders.WWW_AUTHENTICATE, BASIC_REALM);
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                    "AUTH_BASIC_INVALID", "API documentation authentication failed");
        }
        return chain.filter(exchange);
    }

    private boolean isDocumentationPath(String path) {
        return "/doc.html".equals(path)
                || "/v3/api-docs/swagger-config".equals(path)
                || path.startsWith("/webjars/")
                || path.startsWith("/openapi/");
    }

    private boolean matchesExpectedAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(BASIC_PREFIX)) {
            return false;
        }
        byte[] expectedAuthorization = expectedAuthorization(properties);
        return MessageDigest.isEqual(
                authorization.getBytes(StandardCharsets.UTF_8), expectedAuthorization);
    }

    private byte[] expectedAuthorization(ApiDocsProperties value) {
        String source = value.getUsername() + ":" + value.getPassword();
        String encoded = Base64.getEncoder().encodeToString(
                source.getBytes(StandardCharsets.UTF_8));
        return (BASIC_PREFIX + encoded).getBytes(StandardCharsets.UTF_8);
    }

    private void validateConfiguration(ApiDocsProperties value) {
        if (!value.isEnabled() || !value.isBasicEnabled()) {
            return;
        }
        if (!hasValidUsername(value)) {
            throw new IllegalStateException(
                    "KNIFE4J_BASIC_USERNAME must be configured and must not contain ':'");
        }
        if (!hasValidPassword(value)) {
            throw new IllegalStateException(
                    "KNIFE4J_BASIC_PASSWORD must contain at least 12 characters");
        }
    }

    private boolean hasValidCredentials(ApiDocsProperties value) {
        return hasValidUsername(value) && hasValidPassword(value);
    }

    private boolean hasValidUsername(ApiDocsProperties value) {
        return StringUtils.hasText(value.getUsername())
                && !value.getUsername().contains(":");
    }

    private boolean hasValidPassword(ApiDocsProperties value) {
        return StringUtils.hasText(value.getPassword())
                && value.getPassword().length() >= 12;
    }

    private Mono<Void> error(ServerHttpResponse response, HttpStatus status,
                             String code, String errorMsg) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                    new DocumentationApiResponse<>(code, errorMsg, null));
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(bytes)));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private record DocumentationApiResponse<T>(String code, String errorMsg, T data) {
    }
}
