package com.biel.lifecamp.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册类型安全的 Knife4j 文档配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiDocsProperties.class)
public class ApiDocsConfiguration {
}
