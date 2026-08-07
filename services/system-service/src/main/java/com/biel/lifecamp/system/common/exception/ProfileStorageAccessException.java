package com.biel.lifecamp.system.common.exception;

/**
 * Internal marker for private profile object-storage failures.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public final class ProfileStorageAccessException extends RuntimeException {
    /**
     * Creates a storage access failure.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public ProfileStorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a storage access failure without an underlying exception.
     *
     * @param message failure summary
     */
    public ProfileStorageAccessException(String message) {
        super(message);
    }
}
