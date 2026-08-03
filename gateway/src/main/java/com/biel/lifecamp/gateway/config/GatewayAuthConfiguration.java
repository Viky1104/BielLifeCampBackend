package com.biel.lifecamp.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册类型安全的网关认证配置属性。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayAuthConfiguration {
}
