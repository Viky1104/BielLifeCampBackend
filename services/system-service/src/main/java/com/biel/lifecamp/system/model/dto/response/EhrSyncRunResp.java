package com.biel.lifecamp.system.model.dto.response;

import com.biel.lifecamp.system.model.dto.EhrSyncRunDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * EHR 人员全量同步运行响应。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Schema(name = "EhrSyncRun", description = "一次 EHR 人员全量同步运行及处理统计")
public record EhrSyncRunResp(
        @Schema(description = "同步运行数据库主键的十进制字符串",
                example = "1900000000000000001")
        String id,
        @Schema(description = "同步运行类型",
                allowableValues = "FULL_RECONCILIATION",
                example = "FULL_RECONCILIATION")
        String runType,
        @Schema(description = "触发方式",
                allowableValues = {"MANUAL", "SCHEDULED", "RETRY"},
                example = "MANUAL")
        String triggerType,
        @Schema(description = "运行状态",
                allowableValues = {"PENDING", "RUNNING", "SUCCEEDED",
                        "PARTIAL_SUCCEEDED", "FAILED"},
                example = "SUCCEEDED")
        String status,
        @Schema(description = "从 EHR 拉取的人员数量", example = "12000",
                minimum = "0")
        long fetchedCount,
        @Schema(description = "本次新增人员数量", example = "120",
                minimum = "0")
        long insertedCount,
        @Schema(description = "本次更新人员数量", example = "11800",
                minimum = "0")
        long updatedCount,
        @Schema(description = "本次标记离职的人员数量", example = "80",
                minimum = "0")
        long resignedCount,
        @Schema(description = "本次初始化普通角色的人员数量", example = "120",
                minimum = "0")
        long roleInitializedCount,
        @Schema(description = "单人数据问题数量；问题仅写系统日志", example = "2",
                minimum = "0")
        long issueCount,
        @Schema(description = "运行失败码；成功时为 null",
                example = "EHR_SYNC_TECHNICAL_FAILURE")
        String failureCode,
        @Schema(description = "不包含人员敏感信息的失败摘要；成功时为 null",
                example = "EHR synchronization failed")
        String failureDigest,
        @Schema(description = "同步开始时间，RFC 3339 UTC",
                example = "2026-07-31T08:00:00Z")
        Instant startedAt,
        @Schema(description = "同步完成或失败时间，运行中为 null",
                example = "2026-07-31T08:03:30Z")
        Instant completedAt,
        @Schema(description = "同步运行记录创建时间，RFC 3339 UTC",
                example = "2026-07-31T08:00:00Z")
        Instant createdAt) {

    /**
     * 将内部同步运行记录转换为对外契约。
     *
     * @param run 内部运行记录
     * @return 对外同步运行响应
     */
    public static EhrSyncRunResp from(EhrSyncRunDTO run) {
        return new EhrSyncRunResp(
                String.valueOf(run.id()), run.runType(), run.triggerType(), run.status(),
                valueOrZero(run.fetchedCount()), valueOrZero(run.insertedCount()),
                valueOrZero(run.updatedCount()), valueOrZero(run.resignedCount()),
                valueOrZero(run.roleInitializedCount()), valueOrZero(run.issueCount()),
                run.failureCode(), run.failureDigest(), run.startedAt(), run.completedAt(),
                run.createdAt());
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
