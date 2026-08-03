package com.biel.lifecamp.system.task;

import com.biel.lifecamp.system.service.EhrSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 每日 EHR 在职人员全量同步 XXL-JOB 处理器。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Component
public final class EhrEmployeeFullSyncJob {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EhrEmployeeFullSyncJob.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final EhrSyncService ehrSyncService;

    /**
     * 创建每日 EHR 人员全量同步任务。
     *
     * @param ehrSyncService EHR 同步服务
     */
    public EhrEmployeeFullSyncJob(EhrSyncService ehrSyncService) {
        this.ehrSyncService = ehrSyncService;
    }

    /**
     * 执行每日全量同步；调度中心配置为每日 02:00。
     */
    @XxlJob("ehrEmployeeFullSyncJob")
    public void execute() {
        long jobLogId = XxlJobHelper.getLogId();

        /*
         * 调度日志标识进入幂等键，使 XXL-JOB 的同一次重试返回已有运行；
         * 不使用随机键，避免网络抖动或调度重投生成重复全量同步。
         */
        String idempotencyKey = "ehr-full-" + LocalDate.now(BUSINESS_ZONE)
                + "-" + jobLogId;
        var run = ehrSyncService.executeFullSync("SCHEDULED", idempotencyKey);
        LOGGER.info("XXL-JOB EHR 人员全量同步处理完成，jobLogId={}，runId={}，status={}",
                jobLogId, run.id(), run.status());
    }
}
