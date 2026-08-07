package com.biel.lifecamp.system.manager.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.biel.lifecamp.system.common.exception.ProfileStorageAccessException;
import com.biel.lifecamp.system.config.properties.ProfileStorageProperties;
import com.biel.lifecamp.system.manager.ProfileObjectStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Date;

/**
 * Alibaba Cloud OSS implementation for private avatar objects.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public final class OssProfileObjectStorage implements ProfileObjectStorage {
    private final OSS ossClient;
    private final ProfileStorageProperties properties;
    private final Clock clock;

    public OssProfileObjectStorage(
            OSS ossClient, ProfileStorageProperties properties, Clock clock) {
        this.ossClient = ossClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        metadata.setCacheControl("private, max-age=3600");
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            ossClient.putObject(properties.getBucket(), objectKey, input, metadata);
        } catch (RuntimeException exception) {
            throw new ProfileStorageAccessException("Avatar upload failed", exception);
        }
    }

    @Override
    public String signedUrl(String objectKey) {
        Date expiration = Date.from(clock.instant().plus(properties.getSignedUrlTtl()));
        try {
            return ossClient.generatePresignedUrl(
                    properties.getBucket(), objectKey, expiration).toExternalForm();
        } catch (RuntimeException exception) {
            throw new ProfileStorageAccessException("Avatar URL signing failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(properties.getBucket(), objectKey);
        } catch (RuntimeException exception) {
            throw new ProfileStorageAccessException("Avatar deletion failed", exception);
        }
    }
}
