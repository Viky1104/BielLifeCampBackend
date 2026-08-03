package com.biel.lifecamp.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通过角色分配的数据范围。
 *
 * @param type 数据范围类型
 * @param value 数据范围值
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Schema(name = "DataScope", description = "角色分配后实际生效的数据访问范围")
public record DataScopeDTO(
        @Schema(description = "数据范围类型", example = "OWN_ORG")
        String type,
        @Schema(description = "数据范围值；含义由范围类型决定",
                example = "1900000000000000001")
        String value) {
}
