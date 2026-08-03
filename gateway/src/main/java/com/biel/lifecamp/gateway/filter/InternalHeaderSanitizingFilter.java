package com.biel.lifecamp.gateway.filter;

import java.util.ArrayList;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 在认证前移除客户端传入的全部内部身份请求头，防止伪造可信身份。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Component
public final class InternalHeaderSanitizingFilter implements GlobalFilter, Ordered {
    /**
     * 清理所有以 {@code X-Internal-} 开头的外部请求头后继续转发。
     *
     * @param exchange 当前网关请求
     * @param chain 网关过滤器链
     * @return 异步处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate().headers(headers ->
                new ArrayList<>(headers.headerNames()).stream()
                        .filter(name -> name.regionMatches(true, 0, "X-Internal-", 0, 11))
                        .forEach(headers::remove)).build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    /**
     * 保证伪造头清理在其他网关过滤器之前执行。
     *
     * @return 最高过滤优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
