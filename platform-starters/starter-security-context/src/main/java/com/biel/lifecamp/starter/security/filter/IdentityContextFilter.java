package com.biel.lifecamp.starter.security.filter;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.security.InternalHeaders;
import com.biel.lifecamp.starter.security.LoginUser;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.starter.security.config.IdentityContextProperties;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheAccessException;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 校验内部身份 JWS，并向当前请求暴露不可变身份上下文。
 *
 * <p>该过滤器只信任网关签名内容，不读取客户端直接提交的用户或权限请求头。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class IdentityContextFilter extends OncePerRequestFilter {
    private final IdentityContextProperties properties;
    private final AuthorizationCacheProperties cacheProperties;
    private final AuthorizationCacheStore authorizationCacheStore;
    private final JwtDecoder decoder;
    private final ObjectMapper objectMapper;

    public IdentityContextFilter(IdentityContextProperties properties, JwtDecoder decoder,
                                 ObjectMapper objectMapper) {
        this(properties, new AuthorizationCacheProperties(), null, decoder, objectMapper);
    }

    public IdentityContextFilter(
            IdentityContextProperties properties,
            AuthorizationCacheProperties cacheProperties,
            AuthorizationCacheStore authorizationCacheStore,
            JwtDecoder decoder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.cacheProperties = cacheProperties;
        this.authorizationCacheStore = authorizationCacheStore;
        this.decoder = decoder;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验内部身份令牌并将解析结果写入请求域。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 过滤链处理失败时抛出
     * @throws IOException 错误响应写入失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String compactJws = request.getHeader(InternalHeaders.IDENTITY);
        if (compactJws == null || compactJws.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!properties.isEnabled() || decoder == null) {
            unauthorized(response, "AUTH_INTERNAL_IDENTITY_DISABLED");
            return;
        }
        try {
            Jwt jwt = decoder.decode(compactJws);
            IdentityContext identity = toContext(jwt);
            request.setAttribute(IdentityContext.REQUEST_ATTRIBUTE, identity);
            if (!loadLoginUser(request, response, identity)) {
                return;
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response, "AUTH_INTERNAL_IDENTITY_INVALID");
        } catch (AuthorizationCacheAccessException ex) {
            unavailable(response, "AUTH_LOGIN_CONTEXT_UNAVAILABLE");
        }
    }

    private boolean loadLoginUser(HttpServletRequest request, HttpServletResponse response,
                                  IdentityContext identity) throws IOException {
        if (!cacheProperties.isEnabled()) {
            return true;
        }
        if (authorizationCacheStore == null) {
            unavailable(response, "AUTH_LOGIN_CONTEXT_UNAVAILABLE");
            return false;
        }
        CachedAuthorization authorization = authorizationCacheStore.find(
                        identity.employeeId(),
                        properties.getAudience(),
                        identity.authzVersion())
                .orElse(null);
        if (authorization == null) {
            unavailable(response, "AUTH_LOGIN_CONTEXT_MISSING");
            return false;
        }
        if (!authorization.matches(identity, properties.getAudience())) {
            unauthorized(response, "AUTH_LOGIN_CONTEXT_INVALID");
            return false;
        }
        request.setAttribute(
                LoginUser.REQUEST_ATTRIBUTE, authorization.toLoginUser(identity));
        return true;
    }

    private IdentityContext toContext(Jwt jwt) {
        return new IdentityContext(jwt.getSubject(), requiredString(jwt, "org_id"),
                requiredString(jwt, "sid"), requiredString(jwt, "client_type"),
                requiredLong(jwt, "authz_ver"),
                stringSet(jwt.getClaim("role_codes")), stringSet(jwt.getClaim("permissions")),
                scopes(jwt.getClaim("data_scopes")), stringSet(jwt.getClaim("amr")));
    }

    private String requiredString(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing claim " + name);
        }
        return value;
    }

    private long requiredLong(Jwt jwt, String name) {
        Number value = jwt.getClaim(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing claim " + name);
        }
        return value.longValue();
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
    }

    private List<IdentityContext.DataScope> scopes(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        // 忽略非对象类型的数据范围，避免将结构异常的声明扩散到业务授权逻辑。
        return values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(scope -> new IdentityContext.DataScope(String.valueOf(scope.get("type")),
                        scope.get("value") == null ? null : String.valueOf(scope.get("value"))))
                .toList();
    }

    private void unauthorized(HttpServletResponse response, String code) throws IOException {
        error(response, HttpServletResponse.SC_UNAUTHORIZED, code,
                "Internal identity verification failed");
    }

    private void unavailable(HttpServletResponse response, String code) throws IOException {
        error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, code,
                "Login user context unavailable");
    }

    private void error(HttpServletResponse response, int status, String code,
                       String errorMsg) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new FilterApiResponse<>(
                code, errorMsg, null));
    }

    private record FilterApiResponse<T>(String code, String errorMsg, T data) {
    }
}
