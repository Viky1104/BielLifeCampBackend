package com.biel.lifecamp.system.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 可稳定转换为对外错误契约的认证异常。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * 创建认证异常。
     *
     * @param status HTTP 状态
     * @param code 稳定错误码
     * @param message 错误信息
     */
    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /**
     * 创建未认证异常。
     *
     * @param code 稳定错误码
     * @return HTTP 401 认证异常
     */
    public static AuthException unauthorized(String code) {
        return new AuthException(HttpStatus.UNAUTHORIZED, code, "Authentication failed");
    }

    /**
     * 创建禁止访问异常。
     *
     * @param code 稳定错误码
     * @return HTTP 403 认证异常
     */
    public static AuthException forbidden(String code) {
        return new AuthException(HttpStatus.FORBIDDEN, code, "Access denied");
    }

    /**
     * 创建认证状态冲突异常。
     *
     * @param code 稳定错误码
     * @return HTTP 409 认证异常
     */
    public static AuthException conflict(String code) {
        return new AuthException(HttpStatus.CONFLICT, code, "Authentication state conflict");
    }

    /**
     * 创建认证依赖不可用异常。
     *
     * @param code 稳定错误码
     * @return HTTP 503 认证异常
     */
    public static AuthException unavailable(String code) {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "Authentication dependency unavailable");
    }
}
