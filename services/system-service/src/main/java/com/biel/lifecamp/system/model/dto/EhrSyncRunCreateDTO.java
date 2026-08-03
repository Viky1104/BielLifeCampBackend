package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 新建 EHR 同步运行记录参数。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrSyncRunCreateDTO(Long id, String idempotencyKey, String triggerType,
                                  Instant createdAt) {
}
