package com.biel.lifecamp.system.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * EHR 人员同步运行列表响应。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Schema(name = "EhrSyncRunPage", description = "最近的 EHR 人员同步运行列表")
public record EhrSyncRunPageResp(
        @Schema(description = "同步运行列表，按创建时间倒序")
        List<EhrSyncRunResp> items,
        @Schema(description = "分页游标信息")
        CursorPage page) {

    /**
     * 当前同步运行列表的游标信息。
     *
     * @author Biel Life Camp Team
     * @since 2026-07-29
     */
    @Schema(name = "EhrSyncRunCursorPage", description = "同步运行列表分页信息")
    public record CursorPage(
            @Schema(description = "下一页游标；当前版本固定为 null")
            String nextCursor,
            @Schema(description = "是否还有下一页；当前版本固定为 false",
                    example = "false")
            boolean hasMore) {
    }
}
