package com.biel.lifecamp.system.dao;

import com.biel.lifecamp.system.model.dto.AuditRecordDTO;
import com.biel.lifecamp.system.model.dto.AdminCredentialDTO;
import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.biel.lifecamp.system.model.dto.RefreshTokenCreateDTO;
import com.biel.lifecamp.system.model.dto.RefreshTokenDTO;
import com.biel.lifecamp.system.model.dto.SessionCreateDTO;
import com.biel.lifecamp.system.model.dto.SessionEmployeeDTO;
import com.biel.lifecamp.system.model.dto.SessionRefreshDTO;
import com.biel.lifecamp.system.model.dto.SessionRevokeDTO;
import com.biel.lifecamp.system.model.dto.SessionTouchDTO;
import com.biel.lifecamp.system.model.dto.WechatBindingDTO;
import com.biel.lifecamp.system.model.dto.WechatLoginTouchDTO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 身份、授权和会话持久化的 MyBatis 数据访问接口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Mapper
public interface IdentityMapper {
    /**
     * 查询权威 EHR 是否已经产生可用于登录的人员数据。
     *
     * <p>除集成状态外，也兼容已经成功或部分成功、且至少落库一名员工的历史同步运行。</p>
     *
     * @return 已产生可用人员数据时返回 {@code true}
     */
    Boolean selectInitialEhrSyncCompleted();

    /**
     * 按手机号摘要查询在职且账号有效的员工。
     *
     * @param mobileHash 经保护的手机号摘要
     * @return 匹配的有效员工列表
     */
    List<EmployeeDTO> selectActiveEmployeesByMobileHash(String mobileHash);

    /**
     * 通过有效微信绑定关系查询员工。
     *
     * @param appId 微信小程序标识
     * @param subjectHash 经保护的 OpenID 摘要
     * @return 匹配的员工；不存在时返回 {@code null}
     */
    EmployeeDTO selectEmployeeByWechatSubject(@Param("appId") String appId,
                                              @Param("subjectHash") String subjectHash);

    /**
     * 按员工标识查询员工。
     *
     * @param employeeId 员工标识
     * @return 员工信息；不存在时返回 {@code null}
     */
    EmployeeDTO selectEmployeeById(long employeeId);

    /**
     * 按规范化工号查询有效本地密码凭据和员工状态。
     *
     * @param employeeNo 登录工号
     * @return 凭据投影；账号或有效凭据不存在时返回 {@code null}
     */
    AdminCredentialDTO selectAdminCredentialByEmployeeNo(String employeeNo);

    /**
     * 新增微信外部身份绑定。
     *
     * @param binding 绑定信息
     * @return 受影响行数
     */
    int insertWechatIdentity(WechatBindingDTO binding);

    /**
     * 将员工标记为已绑定小程序身份。
     *
     * @param employeeId 员工标识
     * @param now 更新时间
     * @return 受影响行数
     */
    int updateEmployeeBindingStatus(@Param("employeeId") long employeeId,
                                    @Param("now") Instant now);

    /**
     * 新增员工默认角色及本人数据范围。
     *
     * @param binding 绑定信息与默认范围值
     * @return 受影响行数
     */
    int insertDefaultRoleAssignment(WechatBindingDTO binding);

    /**
     * 更新微信身份最近成功登录时间。
     *
     * @param touch 登录更新时间
     * @return 受影响行数
     */
    int updateWechatLogin(WechatLoginTouchDTO touch);

    /**
     * 查询员工当前生效的角色编码。
     *
     * @param employeeId 员工标识
     * @return 排序后的角色编码
     */
    List<String> selectRoleCodes(long employeeId);

    /**
     * 查询员工在目标服务中生效的权限编码。
     *
     * @param employeeId 员工标识
     * @param targetService 目标服务
     * @return 排序后的权限编码
     */
    List<String> selectPermissionCodes(@Param("employeeId") long employeeId,
                                       @Param("targetService") String targetService);

    /**
     * 查询员工当前生效的数据范围。
     *
     * @param employeeId 员工标识
     * @return 排序后的数据范围
     */
    List<DataScopeDTO> selectDataScopes(long employeeId);

    /**
     * 新增认证会话。
     *
     * @param session 会话信息
     * @return 受影响行数
     */
    int insertSession(SessionCreateDTO session);

    /**
     * 新增刷新令牌记录。
     *
     * @param refreshToken 刷新令牌信息
     * @return 受影响行数
     */
    int insertRefreshToken(RefreshTokenCreateDTO refreshToken);

    /**
     * 按摘要查询并锁定刷新令牌记录。
     *
     * @param tokenHash 经保护的令牌摘要
     * @return 被锁定的令牌；不存在时返回 {@code null}
     */
    RefreshTokenDTO selectRefreshTokenForUpdate(String tokenHash);

    /**
     * 查询并锁定会话与员工关联记录。
     *
     * @param sessionId 会话标识
     * @return 被锁定的会话；不存在时返回 {@code null}
     */
    SessionEmployeeDTO selectSessionEmployeeForUpdate(String sessionId);

    /**
     * 查询会话与员工关联记录。
     *
     * @param sessionId 会话标识
     * @return 会话信息；不存在时返回 {@code null}
     */
    SessionEmployeeDTO selectSessionEmployee(String sessionId);

    /**
     * 将有效刷新令牌原子标记为已使用。
     *
     * @param id 刷新令牌标识
     * @param now 使用时间
     * @return 受影响行数；为零表示令牌已被并发使用或已失效
     */
    int updateRefreshTokenConsumed(@Param("id") String id, @Param("now") Instant now);

    /**
     * 在令牌轮换后刷新会话权限版本和活跃时间。
     *
     * @param session 会话刷新信息
     * @return 受影响行数
     */
    int updateSessionAfterRefresh(SessionRefreshDTO session);

    /**
     * 更新会话最后活跃时间和空闲到期时间。
     *
     * @param session 会话活跃信息
     * @return 受影响行数
     */
    int updateSessionLastSeen(SessionTouchDTO session);

    /**
     * 撤销同一轮换家族中的全部刷新令牌。
     *
     * @param familyId 轮换家族标识
     * @return 受影响行数
     */
    int revokeRefreshTokenFamily(String familyId);

    /**
     * 撤销会话。
     *
     * @param session 会话撤销信息
     * @return 受影响行数
     */
    int revokeSession(SessionRevokeDTO session);

    /**
     * 撤销指定会话的全部刷新令牌。
     *
     * @param sessionId 会话标识
     * @return 受影响行数
     */
    int revokeRefreshTokensBySessionId(String sessionId);

    /**
     * 查询员工的全部有效会话标识。
     *
     * @param employeeId 员工标识
     * @return 有效会话标识列表
     */
    List<String> selectActiveSessionIds(long employeeId);

    /**
     * 新增认证操作审计记录。
     *
     * @param audit 审计信息
     * @return 受影响行数
     */
    int insertOperationAudit(AuditRecordDTO audit);
}
