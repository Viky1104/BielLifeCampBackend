package com.biel.lifecamp.system.model.dto.response;

import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import java.util.List;

/**
 * 仅向可信网关返回的实时身份与授权信息。
 *
 * @param employeeId 员工标识
 * @param organizationId 主组织标识
 * @param sessionId 当前有效会话标识
 * @param clientType 登录客户端类型
 * @param authzVersion 当前权限版本
 * @param roleCodes 当前生效的角色编码
 * @param permissions 目标服务权限编码
 * @param dataScopes 当前生效的数据范围
 * @param amr 认证方式集合
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record SessionContextResp(String employeeId, String organizationId, String sessionId,
                                 String clientType,
                                 long authzVersion, List<String> roleCodes,
                                 List<String> permissions, List<DataScopeDTO> dataScopes,
                                 List<String> amr) {
}
