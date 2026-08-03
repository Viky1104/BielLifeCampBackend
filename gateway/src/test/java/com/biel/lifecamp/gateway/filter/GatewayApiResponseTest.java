package com.biel.lifecamp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway 本地错误响应契约测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class GatewayApiResponseTest {
    /**
     * 验证 Gateway 在转发前产生的错误也使用平台统一响应结构。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void serializesUnifiedGatewayError() throws Exception {
        var response =
                new GatewayAuthenticationFilter.GatewayApiResponse<Void>(
                        "AUTH_TOKEN_INVALID", "Request authentication failed", null);

        String json = JsonMapper.builder().build().writeValueAsString(response);

        assertThat(json).isEqualTo(
                "{\"code\":\"AUTH_TOKEN_INVALID\","
                        + "\"errorMsg\":\"Request authentication failed\",\"data\":null}");
    }
}
