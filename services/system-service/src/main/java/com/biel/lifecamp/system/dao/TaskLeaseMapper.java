package com.biel.lifecamp.system.dao;

import com.biel.lifecamp.starter.task.TaskLease;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据库任务租约 MyBatis 访问接口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Mapper
public interface TaskLeaseMapper {
    /**
     * 当任务类型尚无租约时插入运行租约。
     */
    int insertLeaseIfAbsent(@Param("taskType") String taskType,
                            @Param("taskId") String taskId,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("leaseExpiresAt") Instant leaseExpiresAt,
                            @Param("now") Instant now);

    /**
     * 原子接管已经过期的任务租约。
     */
    int takeExpiredLease(@Param("taskType") String taskType,
                         @Param("taskId") String taskId,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("leaseExpiresAt") Instant leaseExpiresAt,
                         @Param("now") Instant now);

    /**
     * 查询指定任务类型的当前租约。
     */
    TaskLease selectLease(String taskType);

    /**
     * 将当前租约标记为成功。
     */
    int markSucceeded(@Param("lease") TaskLease lease, @Param("now") Instant now);

    /**
     * 将当前租约标记为等待重试。
     */
    int markRetry(@Param("lease") TaskLease lease, @Param("failureDigest") String failureDigest,
                  @Param("leaseExpiresAt") Instant leaseExpiresAt,
                  @Param("now") Instant now);

    /**
     * 将当前租约标记为人工处理失败。
     */
    int markFailedManual(@Param("lease") TaskLease lease,
                         @Param("failureDigest") String failureDigest,
                         @Param("now") Instant now);
}
