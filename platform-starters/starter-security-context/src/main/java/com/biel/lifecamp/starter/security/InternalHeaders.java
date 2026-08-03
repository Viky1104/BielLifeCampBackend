package com.biel.lifecamp.starter.security;

/**
 * 网关与内部服务之间使用的可信请求头常量。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class InternalHeaders {
    /** 网关签发的内部身份令牌请求头。 */
    public static final String IDENTITY = "X-Internal-Identity";

    private InternalHeaders() {
    }
}
