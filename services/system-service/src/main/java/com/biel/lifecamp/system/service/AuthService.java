package com.biel.lifecamp.system.service;

import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.ResolvedSessionContextDTO;
import com.biel.lifecamp.system.model.dto.TokenPairDTO;

/**
 * 认证、会话和实时授权业务服务。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public interface AuthService {
    /**
     * 使用服务端校验后的微信身份登录。
     *
     * @param loginCode 微信一次性登录凭证
     * @param phoneCode 尚未绑定员工时使用的首次手机号授权凭证
     * @return 新签发的访问凭证和刷新凭证
     */
    TokenPairDTO login(String loginCode, String phoneCode);

    /**
     * 使用本地密码登录管理后台。
     *
     * @param employeeNo 管理员工号
     * @param password 原始密码，仅用于本次哈希校验
     * @param sourceIp 请求来源地址
     * @return 新签发的访问令牌与刷新令牌
     */
    TokenPairDTO adminLogin(String employeeNo, String password, String sourceIp);

    /**
     * 轮换有效刷新令牌并延长所属会话。
     *
     * @param rawRefreshToken 原始刷新令牌
     * @return 轮换后的访问凭证和刷新凭证
     */
    TokenPairDTO refresh(String rawRefreshToken);

    /**
     * 撤销一个已认证会话。
     *
     * @param sessionId 会话标识
     */
    void logout(String sessionId);

    /**
     * 撤销员工的全部有效会话。
     *
     * @param employeeId 员工标识
     */
    void logoutAll(long employeeId);

    /**
     * 在网关签发内部身份前校验会话，并刷新会话权限版本与活跃时间。
     *
     * @param employeeId 访问令牌中的员工标识
     * @param sessionId 访问令牌中的会话标识
     * @param tokenAuthzVersion 访问令牌中的权限版本
     * @param targetService 接收内部身份的目标服务
     * @return 面向目标服务的实时授权信息
     */
    ResolvedSessionContextDTO resolveSessionContext(long employeeId, String sessionId,
                                                    long tokenAuthzVersion,
                                                    String targetService);

    /**
     * 查询员工在指定服务中的实时授权信息。
     *
     * @param employeeId 员工标识
     * @param targetService 目标服务
     * @return 面向目标服务的实时授权信息
     */
    AuthorizationSnapshotDTO current(long employeeId, String targetService);

    /**
     * 使用恒定时间比较校验共享网关服务凭证。
     *
     * @param supplied 调用方提交的网关服务凭证
     * @return 认证已启用且凭证有效时返回 {@code true}
     */
    boolean validGatewayToken(String supplied);
}
