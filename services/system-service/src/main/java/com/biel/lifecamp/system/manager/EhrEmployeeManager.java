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

    /**
     * 拉取一次带同步运行标识的在职人员全量快照。
     *
     * <p>默认实现兼容不需要运行上下文的测试替身；正式实现应覆盖该方法，将运行标识
     * 贯穿分页进度日志。</p>
     *
     * @param runId 同步运行标识
     * @return EHR 在职人员全量快照
     */
    default EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot(long runId) {
        return fetchActiveEmployeeSnapshot();
    }
}
