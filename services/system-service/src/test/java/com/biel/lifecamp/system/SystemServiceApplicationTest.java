package com.biel.lifecamp.system;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.biel.lifecamp.starter.security.IdentityContext;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 系统支撑服务应用上下文测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.sentinel.enabled=false",
        "spring.flyway.enabled=true", "xxl.job.enabled=false"})
class SystemServiceApplicationTest {
    @Autowired
    private WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    /**
     * 使用真实 MVC 应用上下文构建接口契约测试客户端。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    /**
     * 验证应用在隔离外部依赖的测试配置下能够正常启动。
     */
    @Test
    void contextLoads() {
    }

    /**
     * 验证服务能够输出供 Knife4j 网关聚合的 OpenAPI 3 文档。
     *
     * @throws Exception MVC 请求执行失败时抛出
     */
    @Test
    void exposesOpenApiDocumentForGatewayAggregation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.title").value("Biel Life Camp 系统服务 API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.components.securitySchemes.ExternalBearer.type")
                        .value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.ExternalBearer.scheme")
                        .value("bearer"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/wechat/login'].post.operationId")
                        .value("loginWithWechat"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/wechat/login'].post.summary")
                        .value("微信小程序登录"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/wechat/login'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/token/refresh'].post.responses['401']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/logout'].post.operationId")
                        .value("logoutCurrentSession"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/auth/logout-all'].post.operationId")
                        .value("logoutAllSessions"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/me'].get.operationId")
                        .value("getCurrentSubject"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs'].post.responses['202']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs'].post.operationId")
                        .value("startEhrEmployeeFullSync"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs'].get.operationId")
                        .value("listEhrSyncRuns"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs/{syncRunId}'].get.operationId")
                        .value("getEhrSyncRun"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs/{syncRunId}/issues']"
                                + ".get.operationId")
                        .value("listEhrSyncIssues"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs'].post.parameters[0].name")
                        .value("Idempotency-Key"))
                .andExpect(jsonPath(
                        "$.paths['/api/system/v1/ehr-sync-runs'].post.security[0]"
                                + ".ExternalBearer").isArray())
                .andExpect(jsonPath(
                        "$.components.schemas.WechatLoginRequest.properties.loginCode.description")
                        .isNotEmpty())
                .andExpect(jsonPath(
                        "$.components.schemas.EhrSyncRun.properties.status.description")
                        .isNotEmpty())
                .andExpect(jsonPath(
                        "$.paths['/internal/system/v1/auth/session-context']").doesNotExist());
    }

    /**
     * 验证请求校验失败时仍返回统一的三个响应字段。
     *
     * @throws Exception MVC 请求执行失败时抛出
     */
    @Test
    void validationFailureUsesUnifiedResponse() throws Exception {
        mockMvc.perform(post("/api/system/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorMsg").value("Request validation failed"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 验证资源不存在时不会返回无响应体的 404。
     *
     * @throws Exception MVC 请求执行失败时抛出
     */
    @Test
    void notFoundUsesUnifiedResponse() throws Exception {
        IdentityContext admin = new IdentityContext(
                "1001", "0", "11111111-1111-1111-1111-111111111111",
                "ADMIN_WEB", 1L, Set.of("SUPER_ADMIN"),
                Set.of("system:ehr-sync:read"), List.of(), Set.of("password"));
        mockMvc.perform(get("/api/system/v1/ehr-sync-runs/999")
                        .requestAttr(IdentityContext.REQUEST_ATTRIBUTE, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.errorMsg").value("Resource not found"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
