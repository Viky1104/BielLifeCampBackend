package com.biel.lifecamp.system.model.dto;

/**
 * 人员快照原子生效统计。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrSyncPromotionResultDTO(long fetchedCount, long insertedCount,
                                        long updatedCount, long resignedCount,
                                        long roleInitializedCount, long issueCount) {
}
