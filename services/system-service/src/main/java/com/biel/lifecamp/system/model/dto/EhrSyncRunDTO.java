package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * EHR 同步运行结果。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrSyncRunDTO(
        Long id,
        String runType,
        String triggerType,
        String status,
        Long fetchedCount,
        Long insertedCount,
        Long updatedCount,
        Long resignedCount,
        Long roleInitializedCount,
        Long issueCount,
        String failureCode,
        String failureDigest,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {
}
