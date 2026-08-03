package com.biel.lifecamp.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.biel.lifecamp.gateway.controller.Knife4jDocumentationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 验证 Knife4j 在当前 Spring Boot 4 和 Spring Cloud Gateway 版本下可以完成自动配置。
 *
 * <p>该测试同时锁定聚合端点与前端静态资源，避免依赖升级后只通过编译但文档入口不可用。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.sentinel.enabled=false",
        "platform.gateway-auth.enabled=false", "platform.api-docs.enabled=true",
        "platform.api-docs.basic-enabled=false"})
class Knife4jGatewayApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private Environment environment;

    /**
     * 确认聚合端点 Bean 与 {@code /doc.html} 静态页面都已装配。
     */
    @Test
    void exposesAggregationEndpointAndUiResource() {
        assertThat(applicationContext.getBeansOfType(
                Knife4jDocumentationController.class)).hasSize(1);
        assertThat(resourceLoader.getResource("classpath:/META-INF/resources/doc.html").exists())
                .isTrue();
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:"
                        + environment.getRequiredProperty("local.server.port"))
                .build();
        webTestClient.get().uri("/doc.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
        webTestClient.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.configUrl").isEqualTo("/v3/api-docs/swagger-config")
                .jsonPath("$.urls.length()").isEqualTo(9)
                .jsonPath("$.urls[0].url")
                .isEqualTo("/openapi/system-service/v3/api-docs");
    }
}
