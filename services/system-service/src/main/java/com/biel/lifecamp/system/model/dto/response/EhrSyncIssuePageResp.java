package com.biel.lifecamp.system.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * EHR 同步问题游标分页响应。
 *
 * @param items 当前页问题
 * @param page 下一页信息
 * @author Biel Life Camp Team
 * @since 2026-08-04
 */
@Schema(name = "EhrSyncIssuePage", description = "EHR 同步问题分页结果")
public record EhrSyncIssuePageResp(
        List<EhrSyncIssueResp> items,
        CursorPage page) {

    /**
     * 问题列表游标信息。
     *
     * @param nextCursor 下一页起始游标
     * @param hasMore 是否还有下一页
     */
    public record CursorPage(Long nextCursor, boolean hasMore) {
    }
}
