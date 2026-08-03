package com.biel.lifecamp.starter.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.security.InternalHeaders;
import com.biel.lifecamp.starter.security.LoginUser;
import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.starter.security.config.IdentityContextProperties;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内部身份过滤器错误响应契约测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class IdentityContextFilterTest {
    private static final String SESSION_ID =
            "11111111-1111-1111-1111-111111111111";

    /**
     * 验证身份校验错误同样返回 code、errorMsg 和 data。
     *
     * @throws Exception 过滤器或 JSON 处理失败时抛出
     */
    @Test
    void returnsUnifiedUnauthorizedResponse() throws Exception {
        JsonMapper objectMapper = JsonMapper.builder().build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalHeaders.IDENTITY, "untrusted-jws");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdentityContextFilter filter = new IdentityContextFilter(
                new IdentityContextProperties(), null, objectMapper);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(objectMapper.readTree(response.getContentAsByteArray())
                .toString()).isEqualTo(
                        "{\"code\":\"AUTH_INTERNAL_IDENTITY_DISABLED\","
                                + "\"errorMsg\":\"Internal identity verification failed\","
                                + "\"data\":null}");
    }

    /**
     * 验证内部 JWS 与 Redis 登录用户一致时同时建立两种请求上下文。
     *
     * @throws Exception 过滤器执行失败时抛出
     */
    @Test
    void loadsMatchingRedisLoginUserIntoRequest() throws Exception {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        when(store.find("1001", "system-service", 1L))
                .thenReturn(Optional.of(authorization()));
        MockHttpServletRequest request = requestWithIdentityHeader();
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdentityContextFilter filter = enabledFilter(store);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(IdentityContext.from(request)).isNotNull();
        assertThat(LoginUser.from(request)).isEqualTo(loginUser());
    }

    /**
     * 验证 Redis 上下文丢失时不能只依赖内部 JWS 继续执行。
     *
     * @throws Exception 过滤器执行失败时抛出
     */
    @Test
    void failsClosedWhenRedisLoginUserIsMissing() throws Exception {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        when(store.find("1001", "system-service", 1L))
                .thenReturn(Optional.empty());
        MockHttpServletRequest request = requestWithIdentityHeader();
        MockHttpServletResponse response = new MockHttpServletResponse();

        enabledFilter(store).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"AUTH_LOGIN_CONTEXT_MISSING\"");
    }

    private IdentityContextFilter enabledFilter(AuthorizationCacheStore store) {
        IdentityContextProperties identityProperties = new IdentityContextProperties();
        identityProperties.setEnabled(true);
        identityProperties.setAudience("system-service");
        AuthorizationCacheProperties cacheProperties =
                new AuthorizationCacheProperties();
        cacheProperties.setEnabled(true);
        JwtDecoder decoder = token -> jwt();
        return new IdentityContextFilter(
                identityProperties, cacheProperties, store,
                decoder, JsonMapper.builder().build());
    }

    private MockHttpServletRequest requestWithIdentityHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalHeaders.IDENTITY, "trusted-jws");
        return request;
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.parse("2026-07-31T08:00:00Z");
        return Jwt.withTokenValue("trusted-jws")
                .header("alg", "RS256")
                .issuer("biel-life-camp-gateway")
                .audience(List.of("system-service"))
                .subject("1001")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .claim("org_id", "2001")
                .claim("sid", SESSION_ID)
                .claim("client_type", "MINI_PROGRAM")
                .claim("authz_ver", 1L)
                .claim("role_codes", List.of("EMPLOYEE"))
                .claim("permissions", List.of("system:profile:read"))
                .claim("data_scopes", List.of(Map.of("type", "SELF", "value", "1001")))
                .claim("amr", List.of("wechat"))
                .build();
    }

    private LoginUser loginUser() {
        return authorization().toLoginUser(new IdentityContext(
                "1001", "2001", SESSION_ID, "MINI_PROGRAM", 1L,
                Set.of("EMPLOYEE"), Set.of("system:profile:read"),
                List.of(new IdentityContext.DataScope("SELF", "1001")),
                Set.of("wechat")));
    }

    private CachedAuthorization authorization() {
        return new CachedAuthorization(
                "1001", "E1001", "Test Employee", "2001",
                "system-service", 1L, Set.of("EMPLOYEE"),
                Set.of("system:profile:read"),
                List.of(new IdentityContext.DataScope("SELF", "1001")),
                Instant.parse("2026-07-31T08:00:00Z"));
    }
}
