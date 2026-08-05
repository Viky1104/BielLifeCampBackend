package com.biel.lifecamp.system.service;

import com.biel.lifecamp.system.model.dto.EhrEmployeeValidationResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncPromotionResultDTO;

/**
 * EHR 快照在本地数据库中的分批生效边界。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public interface EhrSyncPersistenceService {
    /**
     * 使用独立短事务分批生效已校验的完整人员快照。
     *
     * @param runId 同步运行标识
     * @param validationResult 人员级校验结果
     * @return 本次生效统计
     */
    EhrSyncPromotionResultDTO promote(long runId,
                                      EhrEmployeeValidationResultDTO validationResult);
}
