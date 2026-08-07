package com.biel.lifecamp.system.manager;

/**
 * Private object-storage boundary for user avatars.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public interface ProfileObjectStorage {
    /**
     * Uploads validated avatar bytes.
     *
     * @param objectKey private object key
     * @param content validated bytes
     * @param contentType detected media type
     */
    void upload(String objectKey, byte[] content, String contentType);

    /**
     * Creates a short-lived GET URL.
     *
     * @param objectKey private object key
     * @return signed URL
     */
    String signedUrl(String objectKey);

    /**
     * Deletes an object when it exists.
     *
     * @param objectKey private object key
     */
    void delete(String objectKey);
}
