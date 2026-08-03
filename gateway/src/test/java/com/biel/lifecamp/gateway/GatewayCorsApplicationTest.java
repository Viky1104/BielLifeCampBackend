package com.biel.lifecamp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 验证跨域预检请求由 Gateway 统一处理，不进入认证和下游转发链路。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.sentinel.enabled=false",
        "platform.gateway-auth.enabled=false", "platform.api-docs.enabled=false"})
class GatewayCorsApplicationTest {
    private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private Environment environment;

    /**
     * 白名单来源的预检请求应在 Gateway 直接成功并返回允许的请求头。
     */
    @Test
    void allowsConfiguredFrontendPreflightRequest() {
        webTestClient().options().uri("/api/system/v1/employees")
                .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "authorization,content-type,idempotency-key,x-request-id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONTEND_ORIGIN)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> org.assertj.core.api.Assertions.assertThat(value)
                                .contains(HttpMethod.POST.name()))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> org.assertj.core.api.Assertions.assertThat(value)
                                .containsIgnoringCase(HttpHeaders.AUTHORIZATION)
                                .containsIgnoringCase("Idempotency-Key"));
    }

    /**
     * 未列入白名单的来源必须在任何下游调用之前被拒绝。
     */
    @Test
    void rejectsUnknownFrontendOrigin() {
        webTestClient().options().uri("/api/system/v1/employees")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpMethod.GET.name())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:"
                        + environment.getRequiredProperty("local.server.port"))
                .build();
    }
}
