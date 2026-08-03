package com.biel.lifecamp.system.common.exception;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheAccessException;
import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.controller.AuthController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将认证异常转换为平台统一接口错误响应。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AuthController.class)
final class AuthExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiResponse<Void>> auth(AuthException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(IdentityContext.MissingIdentityContextException.class)
    ResponseEntity<ApiResponse<Void>> missingIdentity() {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INTERNAL_IDENTITY_MISSING",
                "Verified identity required");
    }

    /**
     * Redis 在线会话或授权缓存技术故障必须失败关闭。
     *
     * @return HTTP 503 统一错误响应
     */
    @ExceptionHandler({
            AuthSessionCacheAccessException.class,
            AuthorizationCacheAccessException.class
    })
    ResponseEntity<ApiResponse<Void>> cacheUnavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_AUTHORIZATION_UNAVAILABLE",
                "Authentication dependency unavailable");
    }

    private ResponseEntity<ApiResponse<Void>> response(
            HttpStatus status, String code, String errorMsg) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(code, errorMsg));
    }
}
