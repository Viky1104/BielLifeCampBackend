package com.biel.lifecamp.system.common.exception;

/**
 * Redis 在线会话访问失败。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public final class AuthSessionCacheAccessException extends RuntimeException {
    public AuthSessionCacheAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
