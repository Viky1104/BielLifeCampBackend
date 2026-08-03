package com.biel.lifecamp.system.service;

import com.biel.lifecamp.system.model.dto.EhrSyncRunDTO;
import java.util.List;

/**
 * EHR 人员全量同步服务。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public interface EhrSyncService {
    /**
     * 执行一次完整 EHR 人员快照同步。
     *
     * @param triggerType 触发类型
     * @param idempotencyKey 幂等键
     * @return 同步运行结果
     */
    EhrSyncRunDTO executeFullSync(String triggerType, String idempotencyKey);

    /**
     * 查询指定同步运行。
     *
     * @param runId 运行标识
     * @return 同步运行，不存在时返回 {@code null}
     */
    EhrSyncRunDTO getRun(long runId);

    /**
     * 查询最近的同步运行。
     *
     * @param limit 返回条数上限
     * @return 同步运行列表
     */
    List<EhrSyncRunDTO> listRuns(int limit);
}
