package com.biel.lifecamp.system.controller;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.config.SystemOpenApiConfiguration;
import com.biel.lifecamp.system.model.dto.request.StartEhrSyncReq;
import com.biel.lifecamp.system.model.dto.response.EhrSyncRunPageResp;
import com.biel.lifecamp.system.model.dto.response.EhrSyncRunResp;
import com.biel.lifecamp.system.model.dto.response.EhrSyncIssuePageResp;
import com.biel.lifecamp.system.model.dto.response.EhrSyncIssueResp;
import com.biel.lifecamp.system.service.EhrSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EHR 人员同步运行管理接口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@RestController
@RequestMapping("/api/system/v1/ehr-sync-runs")
@Tag(name = "EHR 人员同步")
@SecurityRequirement(name = SystemOpenApiConfiguration.EXTERNAL_BEARER)
public final class EhrSyncController {
    private final EhrSyncService ehrSyncService;

    EhrSyncController(EhrSyncService ehrSyncService) {
        this.ehrSyncService = ehrSyncService;
    }

    /**
     * 人工触发一次 EHR 人员全量同步。
     *
     * @param idempotencyKey 幂等键
     * @param request 人工同步请求
     * @return 已受理的同步运行
     */
    @Operation(
            operationId = "startEhrEmployeeFullSync",
            summary = "提交 EHR 人员全量同步",
            description = "创建 PENDING 运行后立即返回，由后台线程完成 EHR 全量拉取、快照校验"
                    + "和本地人员投影生效。客户端应使用返回的运行标识查询进度；同一 "
                    + "Idempotency-Key 重复提交时返回已有运行结果。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "同步已受理并返回 PENDING 运行",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "请求字段、确认票据或同步前置条件无效",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "本实例同步执行队列已满",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "EHR 拉取、校验或本地生效发生技术故障",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping
    ResponseEntity<ApiResponse<EhrSyncRunResp>> start(
            @Parameter(
                    description = "本次人工同步的稳定幂等键，长度 16～128；相同业务请求必须复用",
                    required = true,
                    example = "ehr-full-20260731-001")
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128)
            String idempotencyKey,
            @Valid @RequestBody StartEhrSyncReq request,
            HttpServletRequest servletRequest) {
        requireAdminPermission(servletRequest, "system:ehr-sync:execute");
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(EhrSyncRunResp.from(
                        ehrSyncService.submitFullSync("MANUAL", idempotencyKey))));
    }

    /**
     * 查询最近的 EHR 人员同步运行。
     *
     * @param pageSize 返回条数
     * @return 同步运行列表
     */
    @Operation(
            operationId = "listEhrSyncRuns",
            summary = "查询最近的 EHR 同步运行",
            description = "按创建时间倒序返回最近的同步运行。当前版本暂不返回下一页游标，"
                    + "page.hasMore 固定为 false。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "同步运行列表",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "pageSize 超出 1～100",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    ApiResponse<EhrSyncRunPageResp> list(
            @Parameter(description = "返回的最大运行条数", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            HttpServletRequest servletRequest) {
        requireAdminPermission(servletRequest, "system:ehr-sync:read");
        var items = ehrSyncService.listRuns(pageSize).stream()
                .map(EhrSyncRunResp::from)
                .toList();
        return ApiResponse.success(new EhrSyncRunPageResp(
                items, new EhrSyncRunPageResp.CursorPage(null, false)));
    }

    /**
     * 按运行标识查询 EHR 人员同步结果。
     *
     * @param syncRunId 同步运行标识
     * @return 同步运行，不存在时返回 404
     */
    @Operation(
            operationId = "getEhrSyncRun",
            summary = "查询指定 EHR 同步运行",
            description = "返回指定运行的状态、人员处理统计以及脱敏后的失败摘要。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "同步运行详情",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "同步运行标识格式无效",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "缺少、无效或已撤销的认证信息",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "同步运行不存在",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{syncRunId}")
    ResponseEntity<ApiResponse<EhrSyncRunResp>> get(
            @Parameter(description = "同步运行数据库主键", required = true,
                    example = "1900000000000000001")
            @PathVariable long syncRunId,
            HttpServletRequest servletRequest) {
        requireAdminPermission(servletRequest, "system:ehr-sync:read");
        var run = ehrSyncService.getRun(syncRunId);
        return run == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.failure(
                                "COMMON_RESOURCE_NOT_FOUND", "Resource not found"))
                : ResponseEntity.ok(
                        ApiResponse.success(EhrSyncRunResp.from(run)));
    }

    /**
     * 分页查询指定运行中的人员级问题。
     *
     * @param syncRunId 同步运行标识
     * @param afterId 问题主键游标
     * @param pageSize 当前页大小
     * @param servletRequest 当前请求
     * @return 问题分页，不存在同步运行时返回 404
     */
    @Operation(
            operationId = "listEhrSyncIssues",
            summary = "查询 EHR 同步失败人员",
            description = "按问题主键正序分页返回失败工号、失败阶段、问题编码和脱敏摘要。")
    @GetMapping("/{syncRunId}/issues")
    ResponseEntity<ApiResponse<EhrSyncIssuePageResp>> listIssues(
            @PathVariable long syncRunId,
            @RequestParam(defaultValue = "0") @Min(0) long afterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest servletRequest) {
        requireAdminPermission(servletRequest, "system:ehr-sync:read");
        if (ehrSyncService.getRun(syncRunId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.failure(
                            "COMMON_RESOURCE_NOT_FOUND", "Resource not found"));
        }
        var issues = ehrSyncService.listIssues(
                syncRunId, afterId, pageSize + 1);
        boolean hasMore = issues.size() > pageSize;
        var pageItems = issues.stream()
                .limit(pageSize)
                .map(EhrSyncIssueResp::from)
                .toList();
        Long nextCursor = hasMore && !pageItems.isEmpty()
                ? pageItems.getLast().id() : null;
        return ResponseEntity.ok(ApiResponse.success(
                new EhrSyncIssuePageResp(
                        pageItems,
                        new EhrSyncIssuePageResp.CursorPage(
                                nextCursor, hasMore))));
    }

    /**
     * 管理接口同时校验后台客户端类型与细粒度权限。
     *
     * @param request 当前请求
     * @param permissionCode 所需权限码
     */
    private void requireAdminPermission(
            HttpServletRequest request, String permissionCode) {
        IdentityContext identity = IdentityContext.require(request);
        if (!"ADMIN_WEB".equals(identity.clientType())
                || !identity.permissions().contains(permissionCode)) {
            throw AuthException.forbidden("AUTH_ADMIN_ACCESS_DENIED");
        }
    }
}
