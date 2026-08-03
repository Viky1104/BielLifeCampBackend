package com.biel.lifecamp.system.model.dto;

/**
 * EHR 员工批量持久化参数。
 *
 * <p>摘要在进入 MyBatis 前计算完成，SQL 只接收需要落库的脱敏值。该对象仅在固定大小
 * 持久化批次中短暂存在，不保存到全量同步结果中。</p>
 *
 * @param employee 已校验的员工投影
 * @param payloadDigest EHR 人员关键字段摘要
 * @param mobileHash 规范化手机号摘要，可为空
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record EhrEmployeePersistItemDTO(
        EhrEmployeeUpsertDTO employee,
        String payloadDigest,
        String mobileHash) {
}
