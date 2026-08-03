package com.biel.lifecamp.starter.task;

import java.time.Instant;

/**
 * 定时任务执行租约，用于约束同一任务的并发执行和重试。
 *
 * @param taskId 任务实例标识
 * @param taskType 任务类型
 * @param idempotencyKey 幂等键
 * @param attempt 当前尝试次数
 * @param leaseExpiresAt 租约到期时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record TaskLease(String taskId, String taskType, String idempotencyKey,
                        int attempt, Instant leaseExpiresAt) {
}
