package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheAccessException;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * system-service 对版本化授权快照和当前权限版本的访问适配。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@Component
public final class AuthorizationCacheManager {
    private final AuthorizationCacheStore store;
    private final AuthorizationCacheProperties properties;

    public AuthorizationCacheManager(
            ObjectProvider<AuthorizationCacheStore> storeProvider,
            AuthorizationCacheProperties properties) {
        this.store = storeProvider.getIfAvailable();
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 查询指定员工和目标服务的当前版本授权。
     *
     * @param employee 当前员工
     * @param targetService 目标服务
     * @return 命中的授权快照
     */
    public Optional<AuthorizationSnapshotDTO> findAuthorization(
            EmployeeDTO employee, String targetService) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        CachedAuthorization cached = requiredStore().find(
                Long.toString(employee.id()), targetService, employee.authzVersion())
                .orElse(null);
        if (cached == null) {
            return Optional.empty();
        }
        if (!cached.employeeId().equals(Long.toString(employee.id()))
                || !cached.targetService().equals(targetService)
                || cached.authzVersion() != employee.authzVersion()
                || !cached.organizationId().equals(
                        Long.toString(employee.organizationId()))) {
            throw new AuthorizationCacheAccessException(
                    "Authorization cache does not match its lookup scope",
                    new IllegalStateException("Authorization cache scope mismatch"));
        }
        return Optional.of(new AuthorizationSnapshotDTO(
                employee,
                List.copyOf(cached.roles()),
                List.copyOf(cached.permissions()),
                cached.dataScopes().stream()
                        .map(scope -> new DataScopeDTO(scope.type(), scope.value()))
                        .toList()));
    }

    /**
     * 保存目标服务维度的版本化授权。
     *
     * @param snapshot 权威授权快照
     * @param targetService 目标服务
     * @param now 缓存建立时间
     */
    public void saveAuthorization(
            AuthorizationSnapshotDTO snapshot, String targetService, Instant now) {
        if (!isEnabled()) {
            return;
        }
        requiredStore().save(new CachedAuthorization(
                Long.toString(snapshot.employee().id()),
                snapshot.employee().employeeNo(),
                snapshot.employee().displayName(),
                Long.toString(snapshot.employee().organizationId()),
                targetService,
                snapshot.employee().authzVersion(),
                Set.copyOf(snapshot.roles()),
                Set.copyOf(snapshot.permissions()),
                snapshot.dataScopes().stream()
                        .map(scope -> new IdentityContext.DataScope(
                                scope.type(), scope.value()))
                        .toList(),
                now));
    }

    public OptionalLong findCurrentVersion(long employeeId) {
        return isEnabled()
                ? requiredStore().findCurrentVersion(Long.toString(employeeId))
                : OptionalLong.empty();
    }

    /**
     * 发布员工当前权限版本。普通权限变化只更新版本，不删除登录会话。
     *
     * @param employeeId 员工标识
     * @param authzVersion 新权限版本
     */
    public void saveCurrentVersion(long employeeId, long authzVersion) {
        if (isEnabled()) {
            requiredStore().saveCurrentVersion(
                    Long.toString(employeeId), authzVersion);
        }
    }

    private AuthorizationCacheStore requiredStore() {
        if (store == null) {
            throw new IllegalStateException("AuthorizationCacheStore is required");
        }
        return store;
    }
}
