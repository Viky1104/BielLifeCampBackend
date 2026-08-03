package com.biel.lifecamp.starter.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 规范化请求标识，并同步写入响应头和日志上下文。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class RequestIdFilter extends OncePerRequestFilter {
    /** 请求标识请求头名称。 */
    public static final String HEADER = "X-Request-Id";
    /** 请求域中的请求标识属性名。 */
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    /**
     * 复用调用方请求标识；缺失时生成新标识，并保证日志与响应使用同一值。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 过滤链处理失败时抛出
     * @throws IOException 响应写入失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("request_id", requestId)) {
            filterChain.doFilter(request, response);
        }
    }
}
