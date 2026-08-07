package com.biel.lifecamp.system.manager.impl;

import com.biel.lifecamp.system.common.exception.ProfileStorageAccessException;
import com.biel.lifecamp.system.manager.ProfileObjectStorage;

/**
 * Fail-closed avatar storage used when private object storage is disabled.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public final class UnavailableProfileObjectStorage implements ProfileObjectStorage {
    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        throw unavailable();
    }

    @Override
    public String signedUrl(String objectKey) {
        throw unavailable();
    }

    @Override
    public void delete(String objectKey) {
        throw unavailable();
    }

    private ProfileStorageAccessException unavailable() {
        return new ProfileStorageAccessException("Profile object storage is disabled");
    }
}
