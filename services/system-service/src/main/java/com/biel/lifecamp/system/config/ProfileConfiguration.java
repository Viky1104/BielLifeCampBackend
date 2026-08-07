package com.biel.lifecamp.system.config;

import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentials;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.common.comm.SignVersion;
import com.biel.lifecamp.system.config.properties.ProfileStorageProperties;
import com.biel.lifecamp.system.manager.ProfileObjectStorage;
import com.biel.lifecamp.system.manager.impl.OssProfileObjectStorage;
import com.biel.lifecamp.system.manager.impl.UnavailableProfileObjectStorage;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Private avatar object-storage configuration.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfileStorageProperties.class)
public class ProfileConfiguration {
    /**
     * Builds an HTTPS, Signature V4 OSS client using Alibaba Cloud's default
     * credential chain. The chain supports environment, OIDC, and instance RAM roles.
     *
     * @param properties OSS settings
     * @return OSS client
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "platform.profile.storage",
            name = "enabled", havingValue = "true")
    OSS profileOssClient(ProfileStorageProperties properties) {
        requireText(properties.getEndpoint(), "platform.profile.storage.endpoint");
        requireText(properties.getRegion(), "platform.profile.storage.region");
        requireText(properties.getBucket(), "platform.profile.storage.bucket");
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setProtocol(Protocol.HTTPS);
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(properties.getEndpoint())
                .credentialsProvider(defaultCredentialsProvider())
                .clientConfiguration(configuration)
                .region(properties.getRegion())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.profile.storage",
            name = "enabled", havingValue = "true")
    ProfileObjectStorage ossProfileObjectStorage(
            OSS profileOssClient, ProfileStorageProperties properties, Clock clock) {
        return new OssProfileObjectStorage(profileOssClient, properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean(ProfileObjectStorage.class)
    ProfileObjectStorage unavailableProfileObjectStorage() {
        return new UnavailableProfileObjectStorage();
    }

    private CredentialsProvider defaultCredentialsProvider() {
        com.aliyun.credentials.Client credentialsClient =
                new com.aliyun.credentials.Client();
        return new CredentialsProvider() {
            @Override
            public void setCredentials(Credentials credentials) {
                // The default credential chain owns refresh and rotation.
            }

            @Override
            public Credentials getCredentials() {
                CredentialModel credential = credentialsClient.getCredential();
                return new DefaultCredentials(
                        credential.getAccessKeyId(),
                        credential.getAccessKeySecret(),
                        credential.getSecurityToken());
            }
        };
    }

    private void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName
                    + " must be configured when profile storage is enabled");
        }
    }
}
