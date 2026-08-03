package com.biel.lifecamp.system.manager.impl;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.config.properties.WechatProperties;
import com.biel.lifecamp.system.manager.WechatManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 微信身份管理器的 HTTP 实现。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class WechatManagerImpl implements WechatManager {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final long TOKEN_EXPIRY_SAFETY_SECONDS = 60L;
    private static final long DEFAULT_TOKEN_EXPIRY_SECONDS = 7200L;
    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile AccessToken cachedToken;

    public WechatManagerImpl(WechatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * 使用一次性登录凭证向微信平台换取 OpenID。
     *
     * @param loginCode 微信一次性登录凭证
     * @return 已验证的微信身份
     */
    @Override
    public WechatSession exchangeLoginCode(String loginCode) {
        requireConfiguration();
        String uri = properties.getApiBaseUrl() + "/sns/jscode2session?appid="
                + encode(properties.getAppId()) + "&secret=" + encode(properties.getAppSecret())
                + "&js_code=" + encode(loginCode) + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        JsonNode body = send(request);
        ensureSuccess(body);
        String openId = text(body, "openid");
        if (!StringUtils.hasText(openId)) {
            throw AuthException.unavailable("AUTH_WECHAT_RESPONSE_INVALID");
        }
        return new WechatSession(openId, text(body, "unionid"));
    }

    /**
     * 使用一次性手机号授权凭证向微信平台换取手机号。
     *
     * @param phoneCode 微信一次性手机号授权凭证
     * @return 已验证手机号
     */
    @Override
    public String exchangePhoneCode(String phoneCode) {
        String uri = properties.getApiBaseUrl()
                + "/wxa/business/getuserphonenumber?access_token=" + encode(accessToken());
        JsonNode body = postJson(uri, Map.of("code", phoneCode));
        ensureSuccess(body);
        JsonNode phoneInfo = body.get("phone_info");
        String phone = phoneInfo == null ? null : text(phoneInfo, "purePhoneNumber");
        if (!StringUtils.hasText(phone)) {
            throw AuthException.unavailable("AUTH_WECHAT_PHONE_RESPONSE_INVALID");
        }
        return phone;
    }

    private synchronized String accessToken() {
        requireConfiguration();
        Instant now = Instant.now();
        if (cachedToken != null
                && cachedToken.expiresAt().isAfter(now.plusSeconds(TOKEN_EXPIRY_SAFETY_SECONDS))) {
            return cachedToken.value();
        }
        // 提前一分钟刷新平台令牌，避免请求在传输过程中跨过令牌到期点。
        JsonNode body = postJson(properties.getApiBaseUrl() + "/cgi-bin/stable_token", Map.of(
                "grant_type", "client_credential",
                "appid", properties.getAppId(),
                "secret", properties.getAppSecret(),
                "force_refresh", false));
        ensureSuccess(body);
        String value = text(body, "access_token");
        JsonNode expiresInNode = body.get("expires_in");
        long expiresIn = expiresInNode == null
                ? DEFAULT_TOKEN_EXPIRY_SECONDS : expiresInNode.asLong();
        if (!StringUtils.hasText(value)) {
            throw AuthException.unavailable("AUTH_WECHAT_TOKEN_RESPONSE_INVALID");
        }
        cachedToken = new AccessToken(value, now.plusSeconds(expiresIn));
        return value;
    }

    private JsonNode postJson(String uri, Map<String, ?> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return send(request);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AuthException.unavailable("AUTH_WECHAT_UNAVAILABLE");
        }
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw AuthException.unavailable("AUTH_WECHAT_UNAVAILABLE");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw AuthException.unavailable("AUTH_WECHAT_UNAVAILABLE");
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AuthException.unavailable("AUTH_WECHAT_UNAVAILABLE");
        }
    }

    private void ensureSuccess(JsonNode body) {
        int code = body.path("errcode").asInt(0);
        if (code != 0) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_WECHAT_CODE_INVALID",
                    "WeChat credential rejected");
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getAppSecret())) {
            throw AuthException.unavailable("AUTH_WECHAT_NOT_CONFIGURED");
        }
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
