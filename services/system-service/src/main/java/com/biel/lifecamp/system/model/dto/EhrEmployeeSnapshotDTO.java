package com.biel.lifecamp.system.model.dto;

import java.util.List;

/**
 * 一次 EHR 在职人员全量快照。
 *
 * @param totalRecords EHR 声明的总记录数
 * @param totalPages EHR 声明的总页数
 * @param employees 实际取得的人员记录
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public record EhrEmployeeSnapshotDTO(long totalRecords, int totalPages,
                                     List<EhrEmployeeSourceDTO> employees) {
    public EhrEmployeeSnapshotDTO {
        employees = List.copyOf(employees);
    }
}
