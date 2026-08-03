package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;

/**
 * 正式 EHR 人员接口访问边界。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public interface EhrEmployeeManager {
    /**
     * 获取全部在职人员快照。
     *
     * @return EHR 声明数量和全部分页人员
     */
    EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot();
}
