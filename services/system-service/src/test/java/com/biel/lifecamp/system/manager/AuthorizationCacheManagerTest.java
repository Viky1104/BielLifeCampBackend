package com.biel.lifecamp.system.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.starter.security.CachedAuthorization;
import com.biel.lifecamp.starter.security.config.AuthorizationCacheProperties;
import com.biel.lifecamp.starter.security.context.AuthorizationCacheStore;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * system-service 版本化授权缓存适配测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class AuthorizationCacheManagerTest {
    /**
     * 验证授权快照不绑定会话，并单独发布当前权限版本。
     */
    @Test
    void publishesSharedVersionedAuthorization() {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuthorizationCacheStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        properties.setEnabled(true);
        AuthorizationCacheManager manager =
                new AuthorizationCacheManager(provider, properties);
        Instant now = Instant.parse("2026-07-31T08:00:00Z");
        AuthorizationSnapshotDTO snapshot = snapshot();

        manager.saveAuthorization(snapshot, "system-service", now);
        manager.saveCurrentVersion(1001L, 3L);

        ArgumentCaptor<CachedAuthorization> captor =
                ArgumentCaptor.forClass(CachedAuthorization.class);
        verify(store).save(captor.capture());
        verify(store).saveCurrentVersion("1001", 3L);
        CachedAuthorization cached = captor.getValue();
        assertThat(cached.employeeId()).isEqualTo("1001");
        assertThat(cached.targetService()).isEqualTo("system-service");
        assertThat(cached.authzVersion()).isEqualTo(3L);
        assertThat(cached.permissions()).containsExactly("system:profile:read");
    }

    /**
     * 验证命中版本化授权后不需要重新查询角色权限。
     */
    @Test
    void readsCachedAuthorizationAndCurrentVersion() {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuthorizationCacheStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        properties.setEnabled(true);
        AuthorizationCacheManager manager =
                new AuthorizationCacheManager(provider, properties);
        AuthorizationSnapshotDTO snapshot = snapshot();
        when(store.find("1001", "system-service", 3L))
                .thenReturn(Optional.of(new CachedAuthorization(
                        "1001", "E1001", "Test Employee", "2001",
                        "system-service", 3L,
                        java.util.Set.of("EMPLOYEE"),
                        java.util.Set.of("system:profile:read"),
                        List.of(new com.biel.lifecamp.starter.security.IdentityContext.DataScope(
                                "SELF", "1001")),
                        Instant.parse("2026-07-31T08:00:00Z"))));
        when(store.findCurrentVersion("1001"))
                .thenReturn(OptionalLong.of(3L));

        assertThat(manager.findAuthorization(
                snapshot.employee(), "system-service")).isPresent();
        assertThat(manager.findCurrentVersion(1001L)).hasValue(3L);
    }

    /**
     * 验证组织主数据尚未映射时，授权缓存使用 0 表示未解析组织。
     */
    @Test
    void savesUnresolvedOrganizationAsZero() {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuthorizationCacheStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        properties.setEnabled(true);
        AuthorizationCacheManager manager =
                new AuthorizationCacheManager(provider, properties);

        manager.saveAuthorization(
                snapshot(null), "system-service", Instant.parse("2026-08-05T08:00:00Z"));

        ArgumentCaptor<CachedAuthorization> captor =
                ArgumentCaptor.forClass(CachedAuthorization.class);
        verify(store).save(captor.capture());
        assertThat(captor.getValue().organizationId()).isEqualTo("0");
    }

    /**
     * 验证组织主数据尚未映射时，仍能命中以 0 标识组织的授权缓存。
     */
    @Test
    void readsCachedAuthorizationForUnresolvedOrganization() {
        AuthorizationCacheStore store = mock(AuthorizationCacheStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuthorizationCacheStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        properties.setEnabled(true);
        AuthorizationCacheManager manager =
                new AuthorizationCacheManager(provider, properties);
        AuthorizationSnapshotDTO snapshot = snapshot(null);
        when(store.find("1001", "system-service", 3L))
                .thenReturn(Optional.of(new CachedAuthorization(
                        "1001", "E1001", "Test Employee", "0",
                        "system-service", 3L,
                        java.util.Set.of("EMPLOYEE"),
                        java.util.Set.of("system:profile:read"),
                        List.of(new com.biel.lifecamp.starter.security.IdentityContext.DataScope(
                                "SELF", "1001")),
                        Instant.parse("2026-08-05T08:00:00Z"))));

        assertThat(manager.findAuthorization(
                snapshot.employee(), "system-service")).isPresent();
    }

    private AuthorizationSnapshotDTO snapshot() {
        return snapshot(2001L);
    }

    private AuthorizationSnapshotDTO snapshot(Long organizationId) {
        return new AuthorizationSnapshotDTO(
                new EmployeeDTO(1001L, "E1001", "Test Employee", organizationId,
                        "ACTIVE", "ACTIVE", 3L),
                List.of("EMPLOYEE"),
                List.of("system:profile:read"),
                List.of(new DataScopeDTO("SELF", "1001")));
    }
}
