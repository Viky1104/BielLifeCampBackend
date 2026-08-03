package com.biel.lifecamp.system.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理后台本地密码登录请求。
 *
 * @param employeeNo 管理员工号
 * @param password 登录密码
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public record AdminLoginReq(
        @NotBlank @Size(max = 64) String employeeNo,
        @NotBlank @Size(max = 128) String password) {
}

