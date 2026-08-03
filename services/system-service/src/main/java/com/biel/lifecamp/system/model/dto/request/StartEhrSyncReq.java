package com.biel.lifecamp.system.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 人工触发 EHR 全量同步请求。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Schema(name = "StartEhrSyncRequest", description = "人工触发 EHR 人员全量同步请求")
public record StartEhrSyncReq(
        @Schema(description = "同步运行类型；当前仅支持完整对账",
                allowableValues = "FULL_RECONCILIATION",
                example = "FULL_RECONCILIATION")
        @NotBlank @Pattern(regexp = "FULL_RECONCILIATION") String runType,
        @Schema(description = "人工执行原因，用于操作审计",
                example = "验证首次人员全量同步配置")
        @NotBlank @Size(min = 5, max = 500) String reason,
        @Schema(description = "高风险操作二次确认产生的一次性票据",
                format = "password", example = "confirmation-token-value")
        @NotBlank @Size(min = 16, max = 512) String confirmationToken) {
}
