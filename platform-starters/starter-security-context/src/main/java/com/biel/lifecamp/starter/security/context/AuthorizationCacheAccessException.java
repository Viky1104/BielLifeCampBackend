package com.biel.lifecamp.starter.security.context;

/**
 * Redis 授权缓存访问失败。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public final class AuthorizationCacheAccessException extends RuntimeException {
    public AuthorizationCacheAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
