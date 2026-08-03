package com.biel.lifecamp.starter.web.exception;

import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.starter.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 将参数校验异常和未处理异常转换为平台统一错误响应。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation() {
        return response(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED",
                "Request validation failed");
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiResponse<Void>> invalidRequest() {
        return response(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED",
                "Request validation failed");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> resourceNotFound() {
        return response(HttpStatus.NOT_FOUND, "COMMON_RESOURCE_NOT_FOUND",
                "Resource not found");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(
            Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure, requestId={}",
                requestId(request), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR",
                "Internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> response(
            HttpStatus status, String code, String errorMsg) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(code, errorMsg));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null
                ? request.getHeader(RequestIdFilter.HEADER) : value.toString();
    }
}
