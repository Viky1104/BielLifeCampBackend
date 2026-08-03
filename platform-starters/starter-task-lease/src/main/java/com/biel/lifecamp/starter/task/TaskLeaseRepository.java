package com.biel.lifecamp.starter.task;

import java.time.Duration;
import java.util.Optional;

/**
 * 定时任务租约存储契约。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public interface TaskLeaseRepository {
    /**
     * 尝试获取指定任务类型的执行租约。
     *
     * @param taskType 任务类型
     * @param leaseDuration 租约有效期
     * @return 获取成功时返回租约，否则返回空
     */
    Optional<TaskLease> tryAcquire(String taskType, Duration leaseDuration);

    /**
     * 将租约对应任务标记为成功。
     *
     * @param lease 当前执行租约
     */
    void markSucceeded(TaskLease lease);

    /**
     * 记录失败原因并安排延迟重试。
     *
     * @param lease 当前执行租约
     * @param failure 失败原因
     * @param delay 重试延迟
     */
    void markRetry(TaskLease lease, Throwable failure, Duration delay);

    /**
     * 将任务标记为需要人工处理的最终失败。
     *
     * @param lease 当前执行租约
     * @param failure 失败原因
     */
    void markFailedManual(TaskLease lease, Throwable failure);
}
