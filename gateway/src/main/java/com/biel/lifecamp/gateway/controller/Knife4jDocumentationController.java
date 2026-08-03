package com.biel.lifecamp.gateway.controller;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向 Knife4j UI 提供兼容 Spring Boot 4 的 OpenAPI 分组配置。
 *
 * <p>端点始终随 Gateway 注册，是否允许访问由文档过滤器根据 Nacos 开关动态控制。
 * 这里只声明经过平台审核的服务，避免 Nacos 中其他内部服务被自动暴露到文档入口。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@RestController
public final class Knife4jDocumentationController {
    private static final String CONFIG_URL = "/v3/api-docs/swagger-config";

    /**
     * 返回 Knife4j/Swagger UI 能识别的多服务文档清单。
     *
     * @return 平台服务分组及其网关代理地址
     */
    @GetMapping(value = CONFIG_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    SwaggerConfig swaggerConfig() {
        return new SwaggerConfig(CONFIG_URL, "/webjars/oauth/oauth2.html", List.of(
                new SwaggerUrl("身份与人员服务", "/openapi/system-service/v3/api-docs"),
                new SwaggerUrl("消息服务", "/openapi/communication-service/v3/api-docs"),
                new SwaggerUrl("工作台服务", "/openapi/workbench-service/v3/api-docs"),
                new SwaggerUrl("积分服务", "/openapi/points-service/v3/api-docs"),
                new SwaggerUrl("活动服务", "/openapi/activity-service/v3/api-docs"),
                new SwaggerUrl("社区服务", "/openapi/community-service/v3/api-docs"),
                new SwaggerUrl("商城服务", "/openapi/mall-service/v3/api-docs"),
                new SwaggerUrl("生活服务", "/openapi/life-service/v3/api-docs"),
                new SwaggerUrl("订单视图服务", "/openapi/order-view-service/v3/api-docs")
        ), "");
    }

    record SwaggerConfig(String configUrl, String oauth2RedirectUrl,
                         List<SwaggerUrl> urls, String validatorUrl) {
    }

    record SwaggerUrl(String name, String url) {
    }
}
