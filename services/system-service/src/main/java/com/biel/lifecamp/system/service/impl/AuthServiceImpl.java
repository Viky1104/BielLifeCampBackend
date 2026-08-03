package com.biel.lifecamp.system.service.impl;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.common.security.SecretEncryption;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.config.properties.AdminPasswordProperties;
import com.biel.lifecamp.system.config.properties.WechatProperties;
import com.biel.lifecamp.system.manager.AuthSessionCacheManager;
import com.biel.lifecamp.system.manager.AdminLoginRateLimiter;
import com.biel.lifecamp.system.manager.AuthTokenManager;
import com.biel.lifecamp.system.manager.AuthorizationCacheManager;
import com.biel.lifecamp.system.manager.WechatManager;
import com.biel.lifecamp.system.dao.IdentityMapper;
import com.biel.lifecamp.system.model.dto.AuditRecordDTO;
import com.biel.lifecamp.system.model.dto.AdminCredentialDTO;
import com.biel.lifecamp.system.model.dto.AuthSessionCacheDTO;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.biel.lifecamp.system.model.dto.RefreshTokenCreateDTO;
import com.biel.lifecamp.system.model.dto.RefreshTokenDTO;
import com.biel.lifecamp.system.model.dto.ResolvedSessionContextDTO;
import com.biel.lifecamp.system.model.dto.SessionCreateDTO;
import com.biel.lifecamp.system.model.dto.SessionEmployeeDTO;
import com.biel.lifecamp.system.model.dto.SessionRefreshDTO;
import com.biel.lifecamp.system.model.dto.SessionRevokeDTO;
import com.biel.lifecamp.system.model.dto.SessionTouchDTO;
import com.biel.lifecamp.system.model.dto.TokenPairDTO;
import com.biel.lifecamp.system.model.dto.WechatBindingDTO;
import com.biel.lifecamp.system.model.dto.WechatLoginTouchDTO;
import com.biel.lifecamp.system.service.AuthService;
import com.biel.lifecamp.system.util.PhoneNumberNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证、会话及实时授权业务的默认实现。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final String MINI_PROGRAM = "MINI_PROGRAM";
    private static final String ADMIN_WEB = "ADMIN_WEB";
    private static final String WECHAT = "WECHAT";
    private static final String PASSWORD = "PASSWORD";
    private static final Set<String> TARGET_SERVICES = Set.of(
            "system-service", "communication-service", "workbench-service",
            "points-service", "activity-service", "community-service",
            "mall-service", "life-service", "order-view-service");
    private final AuthProperties authProperties;
    private final AdminPasswordProperties adminPasswordProperties;
    private final WechatProperties wechatProperties;
    private final WechatManager wechatManager;
    private final SecretHashing secretHashing;
    private final SecretEncryption secretEncryption;
    private final IdentityMapper identityMapper;
    private final AuthTokenManager authTokenManager;
    private final AdminLoginRateLimiter adminLoginRateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;
    private final AuthSessionCacheManager authSessionCacheManager;
    private final AuthorizationCacheManager authorizationCacheManager;
    private final LongIdGenerator idGenerator;
    private final Clock clock;

    public AuthServiceImpl(AuthProperties authProperties,
                           AdminPasswordProperties adminPasswordProperties,
                           WechatProperties wechatProperties,
                           WechatManager wechatManager, SecretHashing secretHashing,
                           SecretEncryption secretEncryption,
                           IdentityMapper identityMapper, AuthTokenManager authTokenManager,
                           AdminLoginRateLimiter adminLoginRateLimiter,
                           PasswordEncoder passwordEncoder,
                           AuthSessionCacheManager authSessionCacheManager,
                           AuthorizationCacheManager authorizationCacheManager,
                           LongIdGenerator idGenerator, Clock clock) {
        this.authProperties = authProperties;
        this.adminPasswordProperties = adminPasswordProperties;
        this.wechatProperties = wechatProperties;
        this.wechatManager = wechatManager;
        this.secretHashing = secretHashing;
        this.secretEncryption = secretEncryption;
        this.identityMapper = identityMapper;
        this.authTokenManager = authTokenManager;
        this.adminLoginRateLimiter = adminLoginRateLimiter;
        this.passwordEncoder = passwordEncoder;
        // 未知账号也执行同等成本的 BCrypt 校验，减少通过响应时间枚举账号的风险。
        this.dummyPasswordHash = passwordEncoder.encode("invalid-account-sentinel");
        this.authSessionCacheManager = authSessionCacheManager;
        this.authorizationCacheManager = authorizationCacheManager;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public TokenPairDTO login(String loginCode, String phoneCode) {
        requireEnabled();
        // 用户来源以 EHR 为唯一权威源，首次全量同步完成前禁止建立本地身份绑定。
        if (authProperties.isEhrRequireInitialSync()
                && !Boolean.TRUE.equals(identityMapper.selectInitialEhrSyncCompleted())) {
            throw AuthException.unavailable("AUTH_EHR_INITIAL_SYNC_REQUIRED");
        }
        WechatManager.WechatSession wechatSession = wechatManager.exchangeLoginCode(loginCode);
        String openIdHash = secretHashing.identifier(
                "wechat:" + wechatProperties.getAppId(), wechatSession.openId());
        Instant now = clock.instant();
        EmployeeDTO employee = identityMapper.selectEmployeeByWechatSubject(
                wechatProperties.getAppId(), openIdHash);
        if (employee == null) {
            employee = bindEmployee(phoneCode, wechatSession, openIdHash, now);
        } else {
            validateEmployee(employee);
            identityMapper.updateWechatLogin(new WechatLoginTouchDTO(
                    wechatProperties.getAppId(), openIdHash, now));
        }
        TokenPairDTO pair = createSession(employee, MINI_PROGRAM, WECHAT, now);
        audit(employee.id(), "LOGIN", "SUCCESS", null, now);
        return pair;
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public TokenPairDTO adminLogin(String employeeNo, String password, String sourceIp) {
        requireEnabled();
        if (!adminPasswordProperties.isEnabled()) {
            throw AuthException.unavailable("AUTH_ADMIN_PASSWORD_DISABLED");
        }
        String normalizedEmployeeNo = employeeNo == null ? "" : employeeNo.trim();
        adminLoginRateLimiter.checkAllowed(normalizedEmployeeNo, sourceIp);
        AdminCredentialDTO credential =
                identityMapper.selectAdminCredentialByEmployeeNo(normalizedEmployeeNo);
        String expectedHash = credential == null
                ? dummyPasswordHash : credential.passwordHash();
        if (!passwordEncoder.matches(password, expectedHash)) {
            adminLoginRateLimiter.recordFailure(normalizedEmployeeNo, sourceIp);
            audit(credential == null ? null : credential.employeeId(),
                    "ADMIN_PASSWORD_LOGIN", "FAILURE", "AUTH_INVALID_CREDENTIALS",
                    clock.instant());
            throw AuthException.unauthorized("AUTH_INVALID_CREDENTIALS");
        }
        EmployeeDTO employee = credential.employee();
        validateEmployee(employee);
        adminLoginRateLimiter.clearAccount(normalizedEmployeeNo);
        Instant now = clock.instant();
        TokenPairDTO pair = createSession(employee, ADMIN_WEB, PASSWORD, now);
        audit(employee.id(), "ADMIN_PASSWORD_LOGIN", "SUCCESS", null, now);
        return pair;
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public TokenPairDTO refresh(String rawRefreshToken) {
        requireEnabled();
        Instant now = clock.instant();
        RefreshTokenDTO refreshToken = identityMapper.selectRefreshTokenForUpdate(
                secretHashing.token(rawRefreshToken));
        if (refreshToken == null) {
            throw AuthException.unauthorized("AUTH_REFRESH_INVALID");
        }
        if (!"ACTIVE".equals(refreshToken.status())) {
            // 已消费令牌再次出现视为重放攻击，必须提交整族令牌和会话撤销后再返回失败。
            revokeFamilyAndSession(refreshToken.familyId(), refreshToken.sessionId(),
                    "REFRESH_REPLAY", now);
            audit(null, "REFRESH_REPLAY", "FAILURE", "AUTH_REFRESH_REPLAYED", now);
            throw AuthException.unauthorized("AUTH_REFRESH_REPLAYED");
        }
        /*
         * 先清理旧在线会话，避免刷新事务完成后仍使用旧权限版本。删除失败会让事务
         * 失败关闭；缓存缺失时下个请求可以从数据库重建。
         */
        authSessionCacheManager.deleteRequired(refreshToken.sessionId());
        SessionEmployeeDTO session = identityMapper.selectSessionEmployeeForUpdate(
                refreshToken.sessionId());
        if (session == null) {
            throw AuthException.unauthorized("AUTH_SESSION_REVOKED");
        }
        if (refreshToken.expiresAt().isBefore(now)
                || session.absoluteExpiresAt().isBefore(now)
                || session.idleExpiresAt().isBefore(now)
                || !"ACTIVE".equals(session.sessionStatus())) {
            revokeFamilyAndSession(refreshToken.familyId(), refreshToken.sessionId(),
                    "SESSION_EXPIRED", now);
            throw AuthException.unauthorized("AUTH_SESSION_REVOKED");
        }
        EmployeeDTO employee = session.employee();
        validateEmployee(employee);
        // 行锁与状态更新共同保证同一代刷新令牌只能成功轮换一次。
        identityMapper.updateRefreshTokenConsumed(refreshToken.id(), now);
        String nextRawToken = secretHashing.randomToken();
        String nextTokenId = UUID.randomUUID().toString();
        Instant idleExpiry = minimum(session.absoluteExpiresAt(),
                now.plus(authProperties.getSessionIdleTtl()));
        identityMapper.insertRefreshToken(new RefreshTokenCreateDTO(
                nextTokenId, session.sessionId(), refreshToken.familyId(),
                secretHashing.token(nextRawToken), refreshToken.id(), idleExpiry, now));
        identityMapper.updateSessionAfterRefresh(new SessionRefreshDTO(
                session.sessionId(), employee.authzVersion(), idleExpiry, now));
        TokenPairDTO pair = tokenPair(
                employee, session.sessionId(), session.clientType(), session.authMethod(),
                nextRawToken, idleExpiry);
        audit(employee.id(), "TOKEN_REFRESH", "SUCCESS", null, now);
        return pair;
    }

    @Override
    @Transactional
    public void logout(String sessionId) {
        Instant now = clock.instant();
        SessionEmployeeDTO session = identityMapper.selectSessionEmployee(sessionId);
        Long employeeId = session == null ? null : session.employeeId();
        revokeSession(sessionId, "USER_LOGOUT", now);
        audit(employeeId, "LOGOUT", "SUCCESS", null, now);
    }

    @Override
    @Transactional
    public void logoutAll(long employeeId) {
        Instant now = clock.instant();
        identityMapper.selectActiveSessionIds(employeeId)
                .forEach(sessionId -> revokeSession(sessionId, "USER_LOGOUT_ALL", now));
        audit(employeeId, "LOGOUT_ALL", "SUCCESS", null, now);
    }

    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public ResolvedSessionContextDTO resolveSessionContext(
            long employeeId, String sessionId, long tokenAuthzVersion,
            String targetService) {
        requireEnabled();
        if (!TARGET_SERVICES.contains(targetService)) {
            throw AuthException.forbidden("AUTH_TARGET_SERVICE_INVALID");
        }
        Instant now = clock.instant();
        AuthSessionCacheDTO cachedSession = authSessionCacheManager.find(sessionId)
                .orElse(null);
        SessionEmployeeDTO session = cachedSession == null
                ? identityMapper.selectSessionEmployee(sessionId)
                : cachedSession.toSessionEmployee();
        if (session == null) {
            throw AuthException.unauthorized("AUTH_SESSION_REVOKED");
        }
        EmployeeDTO employee = session.employee();
        if (!sessionId.equals(session.sessionId())
                || employee.id() != employeeId
                || !"ACTIVE".equals(session.sessionStatus())
                || session.absoluteExpiresAt().isBefore(now)
                || session.idleExpiresAt().isBefore(now)) {
            throw AuthException.unauthorized("AUTH_SESSION_REVOKED");
        }
        validateEmployee(employee);
        employee = currentEmployee(employee);
        if (employee.authzVersion() != tokenAuthzVersion) {
            // 权限版本变化时拒绝沿用旧访问令牌，要求客户端通过刷新流程获取新令牌。
            throw AuthException.conflict("AUTHZ_STALE");
        }
        Instant idleExpiry = minimum(session.absoluteExpiresAt(),
                now.plus(authProperties.getSessionIdleTtl()));
        touchSession(session, cachedSession, idleExpiry, now);
        return new ResolvedSessionContextDTO(
                authorization(employee, targetService, now),
                session.clientType(),
                List.of(session.authMethod().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public AuthorizationSnapshotDTO current(long employeeId, String targetService) {
        EmployeeDTO employee = identityMapper.selectEmployeeById(employeeId);
        if (employee == null) {
            throw AuthException.unauthorized("AUTH_EMPLOYEE_NOT_FOUND");
        }
        validateEmployee(employee);
        return authorization(employee, targetService, clock.instant());
    }

    @Override
    public boolean validGatewayToken(String supplied) {
        return authProperties.isEnabled()
                && secretHashing.constantEquals(authProperties.getGatewayServiceToken(), supplied);
    }

    private EmployeeDTO bindEmployee(String phoneCode, WechatManager.WechatSession wechatSession,
                                     String openIdHash, Instant now) {
        if (!StringUtils.hasText(phoneCode)) {
            throw AuthException.forbidden("AUTH_WECHAT_PHONE_REQUIRED");
        }
        String phone = normalizePhone(wechatManager.exchangePhoneCode(phoneCode));
        String mobileHash = secretHashing.identifier("mobile", phone);
        List<EmployeeDTO> matches = identityMapper.selectActiveEmployeesByMobileHash(mobileHash);
        if (matches.isEmpty()) {
            throw AuthException.forbidden("AUTH_EHR_EMPLOYEE_NOT_FOUND");
        }
        if (matches.size() != 1) {
            // 手机号无法唯一定位员工时禁止自动绑定，避免身份串绑。
            throw AuthException.conflict("AUTH_EHR_EMPLOYEE_AMBIGUOUS");
        }
        EmployeeDTO employee = matches.getFirst();
        validateEmployee(employee);
        String unionHash = StringUtils.hasText(wechatSession.unionId())
                ? secretHashing.identifier("wechat-union", wechatSession.unionId()) : null;
        byte[] unionCiphertext = StringUtils.hasText(wechatSession.unionId())
                ? secretEncryption.encrypt(wechatSession.unionId()) : null;
        long identityId = idGenerator.next();
        WechatBindingDTO binding = new WechatBindingDTO(
                identityId, identityId + 1, employee.id(), wechatProperties.getAppId(),
                openIdHash, secretEncryption.encrypt(wechatSession.openId()),
                unionHash, unionCiphertext, mobileHash, Long.toString(employee.id()), now);
        try {
            identityMapper.insertWechatIdentity(binding);
            identityMapper.updateEmployeeBindingStatus(employee.id(), now);
            identityMapper.insertDefaultRoleAssignment(binding);
            audit(employee.id(), "WECHAT_BIND", "SUCCESS", null, now);
        } catch (DuplicateKeyException ex) {
            throw AuthException.conflict("AUTH_WECHAT_BINDING_CONFLICT");
        }
        return employee;
    }

    private AuthorizationSnapshotDTO authorization(
            EmployeeDTO employee, String targetService, Instant now) {
        AuthorizationSnapshotDTO cached = authorizationCacheManager.findAuthorization(
                employee, targetService).orElse(null);
        if (cached != null) {
            return cached;
        }
        AuthorizationSnapshotDTO snapshot = authorizationFromDatabase(employee, targetService);
        authorizationCacheManager.saveAuthorization(snapshot, targetService, now);
        return snapshot;
    }

    private AuthorizationSnapshotDTO authorizationFromDatabase(
            EmployeeDTO employee, String targetService) {
        return new AuthorizationSnapshotDTO(
                employee,
                identityMapper.selectRoleCodes(employee.id()),
                identityMapper.selectPermissionCodes(employee.id(), targetService),
                identityMapper.selectDataScopes(employee.id()));
    }

    private TokenPairDTO createSession(
            EmployeeDTO employee, String clientType, String authMethod, Instant now) {
        String sessionId = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        String refreshId = UUID.randomUUID().toString();
        // 数据库仅保存刷新令牌摘要，原始令牌只在本次响应中交付客户端。
        String rawRefreshToken = secretHashing.randomToken();
        Instant absoluteExpiry = now.plus(authProperties.getSessionAbsoluteTtl());
        Instant idleExpiry = minimum(absoluteExpiry, now.plus(authProperties.getSessionIdleTtl()));
        identityMapper.insertSession(new SessionCreateDTO(
                sessionId, employee.id(), clientType, authMethod, employee.authzVersion(),
                absoluteExpiry, idleExpiry, now));
        identityMapper.insertRefreshToken(new RefreshTokenCreateDTO(
                refreshId, sessionId, familyId, secretHashing.token(rawRefreshToken),
                null, idleExpiry, now));
        return tokenPair(
                employee, sessionId, clientType, authMethod, rawRefreshToken, idleExpiry);
    }

    private TokenPairDTO tokenPair(
            EmployeeDTO employee, String sessionId, String clientType, String authMethod,
            String refreshToken, Instant refreshExpiry) {
        AuthorizationSnapshotDTO snapshot =
                authorizationFromDatabase(employee, "gateway");
        return new TokenPairDTO(
                "Bearer",
                authTokenManager.issueAccessToken(
                        snapshot, sessionId, clientType, authMethod),
                authProperties.getAccessTokenTtl().toSeconds(),
                refreshToken,
                refreshExpiry);
    }

    private void validateEmployee(EmployeeDTO employee) {
        if (!"ACTIVE".equals(employee.employmentStatus())) {
            throw AuthException.forbidden("AUTH_EMPLOYEE_RESIGNED");
        }
        if (!"ACTIVE".equals(employee.accountStatus())) {
            throw AuthException.forbidden("AUTH_ACCOUNT_FROZEN");
        }
    }

    private void requireEnabled() {
        if (!authProperties.isEnabled()) {
            throw AuthException.unavailable("AUTH_DISABLED");
        }
    }

    private String normalizePhone(String value) {
        try {
            return PhoneNumberNormalizer.normalize(value);
        } catch (IllegalArgumentException ex) {
            throw AuthException.forbidden("AUTH_WECHAT_PHONE_INVALID");
        }
    }

    private Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private void revokeFamilyAndSession(String familyId, String sessionId,
                                        String reason, Instant now) {
        identityMapper.revokeRefreshTokenFamily(familyId);
        revokeSession(sessionId, reason, now);
    }

    private void revokeSession(String sessionId, String reason, Instant now) {
        /*
         * 在线会话是普通请求的认证主路径，必须先完成精确删除。删除失败不能伪装
         * 成退出成功，否则数据库虽已撤销但旧缓存仍可能在短时间内被读取。
         */
        authSessionCacheManager.deleteRequired(sessionId);
        identityMapper.revokeSession(new SessionRevokeDTO(sessionId, reason, now));
        identityMapper.revokeRefreshTokensBySessionId(sessionId);
    }

    private EmployeeDTO currentEmployee(EmployeeDTO cachedEmployee) {
        long employeeId = cachedEmployee.id();
        long currentVersion = authorizationCacheManager.findCurrentVersion(employeeId)
                .orElseGet(() -> {
                    EmployeeDTO current = identityMapper.selectEmployeeById(employeeId);
                    if (current == null) {
                        throw AuthException.unauthorized("AUTH_EMPLOYEE_NOT_FOUND");
                    }
                    validateEmployee(current);
                    authorizationCacheManager.saveCurrentVersion(
                            current.id(), current.authzVersion());
                    return current.authzVersion();
                });
        if (currentVersion != cachedEmployee.authzVersion()) {
            return new EmployeeDTO(
                    cachedEmployee.id(), cachedEmployee.employeeNo(),
                    cachedEmployee.displayName(), cachedEmployee.organizationId(),
                    cachedEmployee.employmentStatus(), cachedEmployee.accountStatus(),
                    currentVersion);
        }
        return cachedEmployee;
    }

    private void touchSession(
            SessionEmployeeDTO session,
            AuthSessionCacheDTO cachedSession,
            Instant idleExpiry,
            Instant now) {
        if (!authSessionCacheManager.isEnabled()) {
            identityMapper.updateSessionLastSeen(
                    new SessionTouchDTO(session.sessionId(), idleExpiry, now));
            return;
        }
        if (cachedSession == null) {
            AuthSessionCacheDTO created =
                    AuthSessionCacheDTO.from(session, now).touch(idleExpiry, now, true);
            identityMapper.updateSessionLastSeen(
                    new SessionTouchDTO(session.sessionId(), idleExpiry, now));
            authSessionCacheManager.save(created, now);
            return;
        }
        boolean databaseTouch =
                authSessionCacheManager.shouldTouchDatabase(cachedSession, now);
        boolean redisTouch = databaseTouch
                || authSessionCacheManager.shouldTouchRedis(cachedSession, now);
        if (databaseTouch) {
            identityMapper.updateSessionLastSeen(
                    new SessionTouchDTO(session.sessionId(), idleExpiry, now));
        }
        if (redisTouch) {
            authSessionCacheManager.save(
                    cachedSession.touch(idleExpiry, now, databaseTouch), now);
        }
    }

    private void audit(Long employeeId, String action, String result,
                       String detailCode, Instant now) {
        identityMapper.insertOperationAudit(new AuditRecordDTO(
                idGenerator.next(), employeeId, action, result, detailCode,
                UUID.randomUUID().toString(), now));
    }
}
