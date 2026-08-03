package com.biel.lifecamp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.common.security.SecretEncryption;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.config.properties.AdminPasswordProperties;
import com.biel.lifecamp.system.config.properties.WechatProperties;
import com.biel.lifecamp.system.dao.IdentityMapper;
import com.biel.lifecamp.system.manager.AuthSessionCacheManager;
import com.biel.lifecamp.system.manager.AdminLoginRateLimiter;
import com.biel.lifecamp.system.manager.AuthTokenManager;
import com.biel.lifecamp.system.manager.AuthorizationCacheManager;
import com.biel.lifecamp.system.manager.WechatManager;
import com.biel.lifecamp.system.model.dto.AuthSessionCacheDTO;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.DataScopeDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.biel.lifecamp.system.model.dto.ResolvedSessionContextDTO;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Redis 在线会话主路径和权限版本变化行为测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class AuthServiceRedisSessionTest {
    private static final String SESSION_ID =
            "11111111-1111-1111-1111-111111111111";
    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    private IdentityMapper identityMapper;
    private AuthSessionCacheManager sessionCacheManager;
    private AuthorizationCacheManager authorizationCacheManager;
    private AuthServiceImpl service;

    /**
     * 建立只关注Redis认证边界的服务实例。
     */
    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setEnabled(true);
        identityMapper = mock(IdentityMapper.class);
        sessionCacheManager = mock(AuthSessionCacheManager.class);
        authorizationCacheManager = mock(AuthorizationCacheManager.class);
        service = new AuthServiceImpl(
                properties,
                new AdminPasswordProperties(),
                new WechatProperties(),
                mock(WechatManager.class),
                mock(SecretHashing.class),
                mock(SecretEncryption.class),
                identityMapper,
                mock(AuthTokenManager.class),
                mock(AdminLoginRateLimiter.class),
                mock(PasswordEncoder.class),
                sessionCacheManager,
                authorizationCacheManager,
                mock(LongIdGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * 验证在线会话、版本和授权全部命中时不查询数据库。
     */
    @Test
    void resolvesProtectedRequestWithoutDatabaseOnFullCacheHit() {
        AuthSessionCacheDTO session = session();
        AuthorizationSnapshotDTO authorization = authorization();
        when(sessionCacheManager.find(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionCacheManager.isEnabled()).thenReturn(true);
        when(authorizationCacheManager.findCurrentVersion(1001L))
                .thenReturn(OptionalLong.of(3L));
        when(authorizationCacheManager.findAuthorization(
                any(EmployeeDTO.class), anyString()))
                .thenReturn(Optional.of(authorization));

        ResolvedSessionContextDTO actual = service.resolveSessionContext(
                1001L, SESSION_ID, 3L, "system-service");

        assertThat(actual.authorization()).isEqualTo(authorization);
        assertThat(actual.clientType()).isEqualTo("MINI_PROGRAM");
        verify(identityMapper, never()).selectSessionEmployee(anyString());
        verify(identityMapper, never()).selectEmployeeById(1001L);
        verify(identityMapper, never()).selectRoleCodes(1001L);
        verify(identityMapper, never()).selectPermissionCodes(1001L, "system-service");
        verify(identityMapper, never()).selectDataScopes(1001L);
    }

    /**
     * 验证权限版本变化只拒绝旧JWT，不删除登录会话。
     */
    @Test
    void stalePermissionVersionRequiresRefreshWithoutDeletingSession() {
        when(sessionCacheManager.find(SESSION_ID))
                .thenReturn(Optional.of(session()));
        when(authorizationCacheManager.findCurrentVersion(1001L))
                .thenReturn(OptionalLong.of(4L));

        assertThatThrownBy(() -> service.resolveSessionContext(
                1001L, SESSION_ID, 3L, "system-service"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTHZ_STALE");
        verify(sessionCacheManager, never()).deleteRequired(SESSION_ID);
    }

    private AuthSessionCacheDTO session() {
        return new AuthSessionCacheDTO(
                SESSION_ID, "ACTIVE", "MINI_PROGRAM", "WECHAT",
                NOW.plus(Duration.ofDays(30)),
                NOW.plus(Duration.ofDays(7)), 3L, 1001L,
                "E1001", "Test Employee", 2001L,
                "ACTIVE", "ACTIVE", 3L, NOW, NOW);
    }

    private AuthorizationSnapshotDTO authorization() {
        return new AuthorizationSnapshotDTO(
                new EmployeeDTO(1001L, "E1001", "Test Employee", 2001L,
                        "ACTIVE", "ACTIVE", 3L),
                List.of("EMPLOYEE"),
                List.of("system:profile:read"),
                List.of(new DataScopeDTO("SELF", "1001")));
    }
}
