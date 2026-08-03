package com.biel.lifecamp.system.dao;

import org.apache.ibatis.annotations.Mapper;

/**
 * 用于准备可重复认证测试数据并执行断言的 MyBatis 数据访问接口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Mapper
public interface AuthTestMapper {
    /** 删除认证操作审计数据。 */
    void deleteOperationAudits();

    /** 删除刷新令牌数据。 */
    void deleteRefreshTokens();

    /** 删除用户会话数据。 */
    void deleteUserSessions();

    /** 删除角色分配数据。 */
    void deleteRoleAssignments();

    /** 删除外部身份绑定数据。 */
    void deleteExternalIdentities();

    /** 删除本地密码凭据。 */
    void deleteLocalCredentials();

    /** 删除员工数据。 */
    void deleteEmployees();

    /** 将 EHR 首次同步标记为完成。 */
    void completeInitialEhrSync();

    /**
     * 插入员工测试数据。
     *
     * @param employee 员工测试数据
     */
    void insertEmployee(EmployeeSeed employee);

    /**
     * 为员工写入测试密码凭据。
     *
     * @param employeeId 员工主键
     * @param passwordHash 带算法标识的密码哈希
     */
    void insertLocalCredential(long employeeId, String passwordHash);

    /**
     * 查询最近创建会话的客户端类型。
     *
     * @return 客户端类型
     */
    String selectLatestSessionClientType();

    /**
     * 查询最近创建会话的认证方式。
     *
     * @return 认证方式
     */
    String selectLatestSessionAuthMethod();

    /**
     * 统计外部身份绑定数量。
     *
     * @return 外部身份绑定数量
     */
    int countExternalIdentities();

    /**
     * 统计已持久化 OpenID 密文的测试身份。
     *
     * @return 已持久化密文的身份数量
     */
    int countEncryptedProviderSubjects();

    /**
     * 统计指定操作类型的审计记录数量。
     *
     * @param action 操作类型
     * @return 匹配的审计记录数量
     */
    int countOperationAuditsByAction(String action);

    /**
     * 统计已撤销会话数量。
     *
     * @return 已撤销会话数量
     */
    int countRevokedSessions();

    /**
     * 统计员工数量。
     *
     * @return 员工数量
     */
    int countEmployees();

    /**
     * 通过测试数据访问层持久化的员工测试数据。
     *
     * @param id 员工标识
     * @param ehrPersonId EHR 权威人员标识
     * @param employeeNo 员工编号
     * @param displayName 员工显示名称
     * @param mobileHash 经保护的手机号摘要
     * @param organizationId 主组织标识
     */
    record EmployeeSeed(Long id, String ehrPersonId, String employeeNo,
                        String displayName, String mobileHash, Long organizationId) {
    }

}
