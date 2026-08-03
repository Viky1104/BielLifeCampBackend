package com.biel.lifecamp.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.biel.lifecamp.gateway.controller.Knife4jDocumentationController;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.loadbalancer.cache.CaffeineBasedLoadBalancerCacheManager;
import org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheManager;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ApplicationContext;

/**
 * 统一网关应用上下文测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.sentinel.enabled=false",
        "platform.gateway-auth.enabled=false", "platform.api-docs.enabled=false"})
class GatewayApplicationTest {
    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HttpClientProperties httpClientProperties;

    @Autowired
    private GlobalCorsProperties globalCorsProperties;

    @Autowired
    private LoadBalancerClientFactory loadBalancerClientFactory;

    /**
     * 验证应用在隔离外部依赖的测试配置下能够正常启动。
     */
    @Test
    void exposesOnlyExplicitArchitectureRoutes() {
        Map<String, URI> routeTargets = routeLocator.getRoutes()
                .collectMap(route -> route.getId(), route -> route.getUri()).block();

        assertThat(routeTargets).hasSize(19)
                .containsEntry("system-service-ehr-sync",
                        URI.create("lb://system-service"))
                .containsEntry("system-service", URI.create("lb://system-service"))
                .containsEntry("communication-service",
                        URI.create("lb://communication-service"))
                .containsEntry("workbench-service", URI.create("lb://workbench-service"))
                .containsEntry("points-service", URI.create("lb://points-service"))
                .containsEntry("activity-service", URI.create("lb://activity-service"))
                .containsEntry("community-service", URI.create("lb://community-service"))
                .containsEntry("mall-service", URI.create("lb://mall-service"))
                .containsEntry("life-service", URI.create("lb://life-service"))
                .containsEntry("order-view-service", URI.create("lb://order-view-service"));
        Map<String, Object> ehrSyncMetadata = routeLocator.getRoutes()
                .filter(route -> "system-service-ehr-sync".equals(route.getId()))
                .single().block().getMetadata();
        assertThat(ehrSyncMetadata).containsKey(RESPONSE_TIMEOUT_ATTR);
        assertThat(ehrSyncMetadata.get(RESPONSE_TIMEOUT_ATTR)).isInstanceOf(Number.class);
        assertThat(((Number) ehrSyncMetadata.get(RESPONSE_TIMEOUT_ATTR)).longValue())
                .isEqualTo(Duration.ofMinutes(10).toMillis());
    }

    /**
     * 验证转发超时、Caffeine 实例缓存和轮询负载均衡均按生产配置装配。
     */
    @Test
    void bindsForwardingAndLoadBalancingConfiguration() {
        assertThat(httpClientProperties.getConnectTimeout()).isEqualTo(3000);
        assertThat(httpClientProperties.getResponseTimeout())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(httpClientProperties.isCompression()).isTrue();
        assertThat(globalCorsProperties.getCorsConfigurations()).containsKey("/**");
        assertThat(loadBalancerClientFactory.getInstance(
                "system-service", LoadBalancerCacheManager.class))
                .isInstanceOf(CaffeineBasedLoadBalancerCacheManager.class);
        assertThat(loadBalancerClientFactory.getInstance(
                "system-service", ReactorServiceInstanceLoadBalancer.class))
                .isInstanceOf(RoundRobinLoadBalancer.class);
    }

    /**
     * 文档关闭时仍预注册聚合端点，由网关过滤器负责拒绝请求。
     *
     * <p>这样 Nacos 动态开启文档后不需要重新创建 Spring Bean，避免配置页面返回 404。</p>
     */
    @Test
    void keepsDocumentationEndpointReadyWhenApiDocsAreDisabled() {
        assertThat(applicationContext.getBeansOfType(
                Knife4jDocumentationController.class)).hasSize(1);
    }

    /**
     * 验证 Gateway 从 starter-observability 加载共享 Logback 配置。
     */
    @Test
    void usesSharedLogbackConfiguration() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(LoggerContext.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("CONSOLE")).isNotNull();
        assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("FILE")).isNull();
        assertThat(context.getLogger("com.alibaba.nacos").getEffectiveLevel())
                .isEqualTo(Level.WARN);
    }
}
