package com.biel.lifecamp.system.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable user-profile API failure.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public final class ProfileException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * Creates a profile failure.
     *
     * @param status HTTP status
     * @param code stable error code
     * @param message safe client-facing message
     */
    public ProfileException(HttpStatus status, String code, String message) {
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
     * Creates a semantic validation failure.
     *
     * @param code stable error code
     * @param message safe client-facing message
     * @return HTTP 422 failure
     */
    public static ProfileException invalid(String code, String message) {
        return new ProfileException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    /**
     * Creates an avatar size failure.
     *
     * @return HTTP 413 failure
     */
    public static ProfileException avatarTooLarge() {
        return new ProfileException(HttpStatus.PAYLOAD_TOO_LARGE,
                "PROFILE_AVATAR_TOO_LARGE", "Avatar exceeds the 2 MB limit");
    }

    /**
     * Creates a profile storage dependency failure.
     *
     * @return HTTP 503 failure
     */
    public static ProfileException storageUnavailable() {
        return new ProfileException(HttpStatus.SERVICE_UNAVAILABLE,
                "PROFILE_STORAGE_UNAVAILABLE", "Profile storage unavailable");
    }
}
