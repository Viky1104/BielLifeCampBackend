package com.biel.lifecamp.system.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * 系统服务 OpenAPI 元数据和外部认证方案。
 *
 * <p>这里只描述已实现并通过 Gateway 暴露的 HTTP 契约。尚未实现的员工、组织和完整
 * RBAC 管理接口继续保留在评审契约中，不提前出现在可调试的 Knife4j 文档里。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Biel Life Camp 系统服务 API",
                version = "1.0.0",
                description = "提供微信认证、会话管理、当前员工授权信息和 EHR 人员全量同步能力。"
                        + "所有 JSON 响应统一使用 code、errorMsg、data 三字段结构。"),
        tags = {
                @Tag(name = "认证与会话", description = "微信登录、令牌轮换、会话撤销和当前身份查询"),
                @Tag(name = "EHR 人员同步", description = "人工全量同步及同步运行结果查询")
        })
@SecurityScheme(
        name = SystemOpenApiConfiguration.EXTERNAL_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "system-service 签发、由 Gateway 校验的 Bearer JWT")
public class SystemOpenApiConfiguration {
    /** 外部接口 Bearer JWT 安全方案名称。 */
    public static final String EXTERNAL_BEARER = "ExternalBearer";
}
