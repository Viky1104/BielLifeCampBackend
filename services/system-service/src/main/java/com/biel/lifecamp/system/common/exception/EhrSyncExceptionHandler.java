package com.biel.lifecamp.system.common.exception;

import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.controller.EhrSyncController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 EHR 同步业务异常转换为平台统一接口错误响应。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = EhrSyncController.class)
final class EhrSyncExceptionHandler {

    /**
     * 转换 EHR 同步业务异常。
     *
     * @param exception 同步业务异常
     * @param request HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(EhrSyncException.class)
    ResponseEntity<ApiResponse<Void>> ehrSync(EhrSyncException exception) {
        HttpStatus status = switch (exception.code()) {
            case "EHR_SYNC_ALREADY_RUNNING" -> HttpStatus.CONFLICT;
            case "EHR_SYNC_QUEUE_FULL" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(
                ApiResponse.failure(exception.code(), exception.getMessage()));
    }
}
