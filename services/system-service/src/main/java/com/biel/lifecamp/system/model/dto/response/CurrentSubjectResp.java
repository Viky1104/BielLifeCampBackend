package com.biel.lifecamp.system.model.dto.response;

import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 当前登录员工的身份与授权响应。
 *
 * @param employeeId 员工标识
 * @param employeeNo 员工编号
 * @param displayName 员工显示名称
 * @param organizationId 主组织标识
 * @param roles 当前生效的角色编码
 * @param permissions 系统服务权限编码
 * @param dataScopes 当前生效的数据范围
 * @param authzVersion 当前权限版本
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Schema(name = "CurrentSubject", description = "当前登录员工的身份和实时授权摘要")
public record CurrentSubjectResp(
        @Schema(description = "员工数据库主键的十进制字符串",
                example = "1900000000000000001")
        String employeeId,
        @Schema(description = "EHR 工号", example = "B10001")
        String employeeNo,
        @Schema(description = "员工显示名称", example = "张三")
        String displayName,
        @Schema(description = "主组织数据库主键的十进制字符串",
                example = "1900000000000000100")
        String organizationId,
        @Schema(description = "当前生效的角色编码", example = "[\"EMPLOYEE\"]")
        List<String> roles,
        @Schema(description = "system-service 权限编码",
                example = "[\"system:profile:read\"]")
        List<String> permissions,
        @Schema(description = "当前生效的数据范围")
        List<DataScopeDTO> dataScopes,
        @Schema(description = "权限版本；角色或范围变化时递增", example = "1")
        long authzVersion) {
}
