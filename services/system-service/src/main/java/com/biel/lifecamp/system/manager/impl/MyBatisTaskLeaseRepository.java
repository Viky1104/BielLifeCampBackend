package com.biel.lifecamp.system.manager.impl;

import com.biel.lifecamp.starter.task.TaskLease;
import com.biel.lifecamp.starter.task.TaskLeaseRepository;
import com.biel.lifecamp.system.dao.TaskLeaseMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 使用 MySQL 唯一行和条件更新实现集群任务租约。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Component
public final class MyBatisTaskLeaseRepository implements TaskLeaseRepository {
    private final TaskLeaseMapper taskLeaseMapper;
    private final Clock clock;

    public MyBatisTaskLeaseRepository(TaskLeaseMapper taskLeaseMapper, Clock clock) {
        this.taskLeaseMapper = taskLeaseMapper;
        this.clock = clock;
    }

    @Override
    public Optional<TaskLease> tryAcquire(String taskType, Duration leaseDuration) {
        Instant now = clock.instant();
        String taskId = UUID.randomUUID().toString();
        int inserted = taskLeaseMapper.insertLeaseIfAbsent(
                taskType, taskId, taskId, now.plus(leaseDuration), now);
        if (inserted == 0) {
            taskLeaseMapper.takeExpiredLease(
                    taskType, taskId, taskId, now.plus(leaseDuration), now);
        }
        TaskLease lease = taskLeaseMapper.selectLease(taskType);
        return taskId.equals(lease.taskId()) ? Optional.of(lease) : Optional.empty();
    }

    @Override
    public void markSucceeded(TaskLease lease) {
        taskLeaseMapper.markSucceeded(lease, clock.instant());
    }

    @Override
    public void markRetry(TaskLease lease, Throwable failure, Duration delay) {
        Instant now = clock.instant();
        taskLeaseMapper.markRetry(lease, failure.getClass().getSimpleName(),
                now.plus(delay), now);
    }

    @Override
    public void markFailedManual(TaskLease lease, Throwable failure) {
        taskLeaseMapper.markFailedManual(
                lease, failure.getClass().getSimpleName(), clock.instant());
    }
}
