package com.biel.lifecamp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 内部身份请求头清理过滤器测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
class InternalHeaderSanitizingFilterTest {
    /**
     * 验证客户端伪造的大小写混合内部请求头均被移除，同时保留普通请求头。
     */
    @Test
    void removesEveryClientSuppliedInternalHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/system/v1/me")
                .header("X-Internal-Identity", "forged")
                .header("x-internal-future-field", "forged")
                .header("X-Request-Id", "request-1").build());
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        new InternalHeaderSanitizingFilter().filter(exchange, filtered -> {
            forwarded.set(filtered.getRequest());
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getHeaders().headerNames())
                .noneMatch(name -> name.toLowerCase().startsWith("x-internal-"));
        assertThat(forwarded.get().getHeaders().getFirst("X-Request-Id")).isEqualTo("request-1");
    }
}
