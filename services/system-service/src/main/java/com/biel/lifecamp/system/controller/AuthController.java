package com.biel.lifecamp.system.controller;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.config.SystemOpenApiConfiguration;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.ResolvedSessionContextDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.biel.lifecamp.system.model.dto.TokenPairDTO;
import com.biel.lifecamp.system.model.dto.request.RefreshTokenReq;
import com.biel.lifecamp.system.model.dto.request.AdminLoginReq;
import com.biel.lifecamp.system.model.dto.request.SessionContextReq;
import com.biel.lifecamp.system.model.dto.request.WechatLoginReq;
import com.biel.lifecamp.system.model.dto.response.CurrentSubjectResp;
import com.biel.lifecamp.system.model.dto.response.SessionContextResp;
import com.biel.lifecamp.system.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证、会话和当前登录员工相关的 HTTP 接口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@RestController
@RequestMapping
@Tag(name = "认证与会话")
public final class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 使用微信小程序凭证登录；首次登录还需提供手机号授权凭证完成员工绑定。
     *
     * @param request 微信登录请求
     * @return 新签发的访问令牌与刷新令牌
     */
    @Operation(
            operationId = "loginWithWechat",
            summary = "微信小程序登录",
            description = "服务端使用 loginCode 换取 OpenID。已有有效绑定时直接登录；"
                    + "首次绑定时必须额外提供 phoneCode，由服务端换取已验证手机号后匹配"
                    + "最近一次成功 EHR 人员投影。客户端不得提交 OpenID 或手机号明文。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "登录成功并签发访问令牌和刷新令牌",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "请求字段校验失败",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "微信登录凭证无效",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "首次绑定信息不足、员工状态禁止登录或人员未匹配",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "EHR 人员或微信绑定状态存在冲突",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "认证未启用、EHR 初始同步未完成或微信依赖不可用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/system/v1/auth/wechat/login")
    ApiResponse<TokenPairDTO> login(@Valid @RequestBody WechatLoginReq request) {
        return ApiResponse.success(
                authService.login(request.loginCode(), request.phoneCode()));
    }

    /**
     * 使用工号和本地密码登录管理后台。
     *
     * @param request 登录请求
     * @param servletRequest 当前 HTTP 请求
     * @return 新签发的访问令牌与刷新令牌
     */
    @Operation(
            operationId = "loginAdminWithPassword",
            summary = "管理后台密码登录",
            description = "仅在 platform.auth.admin-password.enabled=true 时开放。"
                    + "账号不存在与密码错误统一返回 AUTH_INVALID_CREDENTIALS。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "登录成功并签发后台会话令牌",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "请求字段校验失败",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "凭据无效或达到登录失败阈值",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "密码登录关闭或限流依赖不可用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/system/v1/auth/admin/login")
    ApiResponse<TokenPairDTO> adminLogin(
            @Valid @RequestBody AdminLoginReq request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.adminLogin(
                request.employeeNo(), request.password(), servletRequest.getRemoteAddr()));
    }

    /**
     * 轮换刷新令牌并延长所属会话。
     *
     * @param request 刷新令牌请求
     * @return 轮换后的访问令牌与刷新令牌
     */
    @Operation(
            operationId = "refreshAccessToken",
            summary = "轮换访问令牌",
            description = "使用仍有效的刷新令牌原子签发下一代访问令牌和刷新令牌。"
                    + "旧刷新令牌立即失效；检测到重放时撤销整个令牌族及所属会话。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "令牌轮换成功",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "请求字段校验失败",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "刷新令牌无效、已重放或会话已撤销",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "员工已离职或账号已冻结",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "认证功能未启用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/system/v1/auth/token/refresh")
    ApiResponse<TokenPairDTO> refresh(@Valid @RequestBody RefreshTokenReq request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    /**
     * 撤销当前登录会话。
     *
     * @param request 携带可信内部身份的请求
     * @return 无响应体
     */
    @Operation(
            operationId = "logoutCurrentSession",
            summary = "退出当前会话",
            description = "撤销当前访问令牌所属会话及其有效刷新令牌。重复撤销按成功处理。")
    @SecurityRequirement(name = SystemOpenApiConfiguration.EXTERNAL_BEARER)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "当前会话已撤销",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/system/v1/auth/logout")
    ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(IdentityContext.require(request).sessionId());
        return ApiResponse.success(null);
    }

    /**
     * 撤销当前员工的全部有效会话。
     *
     * @param request 携带可信内部身份的请求
     * @return 无响应体
     */
    @Operation(
            operationId = "logoutAllSessions",
            summary = "退出全部会话",
            description = "撤销当前员工在所有客户端上的有效会话及刷新令牌。")
    @SecurityRequirement(name = SystemOpenApiConfiguration.EXTERNAL_BEARER)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "当前员工的全部会话已撤销",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/api/system/v1/auth/logout-all")
    ApiResponse<Void> logoutAll(HttpServletRequest request) {
        authService.logoutAll(Long.parseLong(IdentityContext.require(request).employeeId()));
        return ApiResponse.success(null);
    }

    /**
     * 查询当前登录员工及其在系统服务中的实时权限。
     *
     * @param request 携带可信内部身份的请求
     * @return 当前员工身份与授权信息
     */
    @Operation(
            operationId = "getCurrentSubject",
            summary = "查询当前员工身份",
            description = "返回当前登录员工及其在 system-service 中实时生效的角色、权限、"
                    + "数据范围和授权版本。")
    @SecurityRequirement(name = SystemOpenApiConfiguration.EXTERNAL_BEARER)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "当前员工身份和授权摘要",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "员工已离职或账号已冻结",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/api/system/v1/me")
    ApiResponse<CurrentSubjectResp> me(HttpServletRequest request) {
        IdentityContext identity = IdentityContext.require(request);
        AuthorizationSnapshotDTO snapshot = authService.current(
                Long.parseLong(identity.employeeId()), "system-service");
        EmployeeDTO employee = snapshot.employee();
        return ApiResponse.success(new CurrentSubjectResp(
                Long.toString(employee.id()),
                employee.employeeNo(),
                employee.displayName(),
                employee.organizationIdValue(),
                snapshot.roles(),
                snapshot.permissions(),
                snapshot.dataScopes(),
                employee.authzVersion()));
    }

    /**
     * 向可信网关返回会话的实时授权上下文。
     *
     * @param serviceToken 网关服务凭证
     * @param request 会话上下文请求
     * @return 面向目标服务的实时身份与权限
     */
    @Hidden
    @PostMapping("/internal/system/v1/auth/session-context")
    ApiResponse<SessionContextResp> sessionContext(
            @RequestHeader(value = "X-Gateway-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody SessionContextReq request) {
        if (!authService.validGatewayToken(serviceToken)) {
            // 内部接口必须同时校验服务身份，不能仅依赖网络边界。
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_SERVICE_IDENTITY_INVALID",
                    "Gateway service identity rejected");
        }
        ResolvedSessionContextDTO resolved = authService.resolveSessionContext(
                request.employeeId(), request.sessionId(),
                request.authzVersion(), request.targetService());
        AuthorizationSnapshotDTO snapshot = resolved.authorization();
        EmployeeDTO employee = snapshot.employee();
        return ApiResponse.success(new SessionContextResp(
                Long.toString(employee.id()),
                employee.organizationIdValue(),
                request.sessionId(),
                resolved.clientType(),
                employee.authzVersion(),
                snapshot.roles(),
                snapshot.permissions(),
                snapshot.dataScopes(),
                resolved.authenticationMethods()));
    }
}
