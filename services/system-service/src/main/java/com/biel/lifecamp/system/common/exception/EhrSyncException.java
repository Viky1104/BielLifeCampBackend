package com.biel.lifecamp.system.common.exception;

/**
 * EHR 同步无法安全继续时抛出的稳定业务异常。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public final class EhrSyncException extends RuntimeException {
    private final String code;

    /**
     * 创建同步异常。
     *
     * @param code 稳定失败码
     * @param message 不包含个人敏感信息的错误摘要
     */
    public EhrSyncException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建带原始技术原因的同步异常。
     *
     * <p>对外仍只暴露脱敏后的稳定错误摘要，原始异常仅用于服务端日志保留根因链。</p>
     *
     * @param code 稳定失败码
     * @param message 不包含个人敏感信息的错误摘要
     * @param cause 原始技术异常
     */
    public EhrSyncException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 返回稳定失败码。
     *
     * @return 稳定失败码
     */
    public String code() {
        return code;
    }
}
