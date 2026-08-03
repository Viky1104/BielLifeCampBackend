package com.biel.lifecamp.starter.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * 平台统一接口响应。
 *
 * <p>成功响应固定使用 {@code code="0"} 和空错误消息；失败响应使用稳定业务错误码，
 * 业务数据为空。所有前端和服务调用方均只需要处理这一种响应外层结构。</p>
 *
 * @param code 响应码，成功固定为 {@code 0}
 * @param errorMsg 响应消息，成功时为空字符串
 * @param data 业务返回数据，失败时为空
 * @param <T> 业务数据类型
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Schema(description = "平台统一接口响应；成功时 code 为 0、errorMsg 为空字符串，"
        + "失败时 data 为 null")
public record ApiResponse<T>(
        @Schema(description = "响应码：成功固定为字符串 0，失败为稳定业务错误码",
                example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "响应消息：成功为空字符串，失败为可向调用方展示的错误摘要",
                example = "", requiredMode = Schema.RequiredMode.REQUIRED)
        String errorMsg,
        @Schema(description = "业务数据：失败响应固定为 null",
                requiredMode = Schema.RequiredMode.REQUIRED)
        T data) {
    /** 平台成功响应码。 */
    public static final String SUCCESS_CODE = "0";

    /**
     * 保证响应码和响应消息始终存在，避免前端出现多种空值语义。
     */
    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(errorMsg, "errorMsg must not be null");
    }

    /**
     * 创建成功响应。
     *
     * @param data 业务数据，允许为空
     * @param <T> 业务数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "", data);
    }

    /**
     * 创建失败响应。
     *
     * @param code 稳定业务错误码
     * @param errorMsg 面向调用方的错误消息
     * @param <T> 业务数据类型
     * @return 统一失败响应
     */
    public static <T> ApiResponse<T> failure(String code, String errorMsg) {
        if (SUCCESS_CODE.equals(code)) {
            throw new IllegalArgumentException("failure code must not be 0");
        }
        return new ApiResponse<>(code, errorMsg, null);
    }
}
