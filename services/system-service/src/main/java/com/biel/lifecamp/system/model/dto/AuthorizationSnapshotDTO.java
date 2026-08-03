package com.biel.lifecamp.system.model.dto;

import java.util.List;

/**
 * 面向单一目标服务的员工实时授权快照。
 *
 * @param employee 员工身份
 * @param roles 当前生效的角色编码
 * @param permissions 当前生效的权限编码
 * @param dataScopes 当前生效的数据范围
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record AuthorizationSnapshotDTO(EmployeeDTO employee, List<String> roles,
                                       List<String> permissions, List<DataScopeDTO> dataScopes) {
}
