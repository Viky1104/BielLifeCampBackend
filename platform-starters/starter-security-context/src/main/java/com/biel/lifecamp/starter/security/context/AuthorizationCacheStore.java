package com.biel.lifecamp.starter.security.context;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 版本化授权快照和员工当前权限版本的存储边界。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
public interface AuthorizationCacheStore {
    /**
     * 保存版本化授权快照。
     *
     * @param authorization 授权快照
     */
    void save(CachedAuthorization authorization);

    /**
     * 查询员工在目标服务中的指定版本授权。
     *
     * @param employeeId 员工标识
     * @param targetService 目标服务
     * @param authzVersion 权限版本
     * @return 命中的授权快照
     */
    Optional<CachedAuthorization> find(
            String employeeId, String targetService, long authzVersion);

    /**
     * 保存员工当前权限版本。
     *
     * @param employeeId 员工标识
     * @param authzVersion 当前权限版本
     */
    void saveCurrentVersion(String employeeId, long authzVersion);

    /**
     * 查询员工当前权限版本。
     *
     * @param employeeId 员工标识
     * @return 当前权限版本
     */
    OptionalLong findCurrentVersion(String employeeId);
}
