package com.biel.lifecamp.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 平台统一响应契约测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class ApiResponseTest {
    /**
     * 验证成功响应固定使用成功码、空错误消息和业务数据。
     */
    @Test
    void createsSuccessfulResponse() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.code()).isEqualTo("0");
        assertThat(response.errorMsg()).isEmpty();
        assertThat(response.data()).isEqualTo("payload");
    }

    /**
     * 验证失败响应保留稳定错误码和错误消息，不返回业务数据。
     */
    @Test
    void createsFailedResponse() {
        ApiResponse<Void> response =
                ApiResponse.failure("COMMON_VALIDATION_FAILED", "Request validation failed");

        assertThat(response.code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(response.errorMsg()).isEqualTo("Request validation failed");
        assertThat(response.data()).isNull();
    }

    /**
     * 验证 JSON 外层字段严格使用前后端约定的三个名称。
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void serializesUnifiedJsonFieldNames() throws Exception {
        JsonMapper objectMapper = JsonMapper.builder().build();
        String successJson =
                objectMapper.writeValueAsString(ApiResponse.success("payload"));
        String failureJson = objectMapper.writeValueAsString(
                ApiResponse.failure("COMMON_INTERNAL_ERROR", "Internal server error"));

        assertThat(successJson)
                .isEqualTo("{\"code\":\"0\",\"errorMsg\":\"\",\"data\":\"payload\"}");
        assertThat(failureJson).isEqualTo(
                "{\"code\":\"COMMON_INTERNAL_ERROR\","
                        + "\"errorMsg\":\"Internal server error\",\"data\":null}");
    }
}
