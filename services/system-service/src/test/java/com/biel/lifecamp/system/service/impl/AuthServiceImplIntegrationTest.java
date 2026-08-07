package com.biel.lifecamp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.common.exception.AuthException;
import com.biel.lifecamp.system.common.security.SecretHashing;
import com.biel.lifecamp.system.manager.WechatManager;
import com.biel.lifecamp.system.dao.AuthTestMapper;
import com.biel.lifecamp.system.dao.AuthTestMapper.EmployeeSeed;
import com.biel.lifecamp.system.model.dto.TokenPairDTO;
import com.biel.lifecamp.system.service.AuthService;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 认证服务与 MyBatis 持久化的集成测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.sentinel.enabled=false", "spring.flyway.enabled=true", "xxl.job.enabled=false",
        "platform.auth.enabled=true", "platform.auth.allow-ephemeral-keys=true",
        "platform.auth.key-id=test-key",
        "platform.auth.identifier-pepper=identifier-pepper-for-tests-1234567890",
        "platform.auth.identity-encryption-key=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=",
        "platform.auth.token-pepper=refresh-token-pepper-for-tests-1234567890",
        "platform.auth.gateway-service-token=gateway-service-token-for-tests-1234567890",
        "platform.auth.admin-password.enabled=true",
        "platform.auth.admin-password.rate-limit-enabled=false",
        "platform.wechat.app-id=test-app-id"
})
@Import(AuthServiceImplIntegrationTest.WechatStubConfiguration.class)
class AuthServiceImplIntegrationTest {
    @Autowired
    private AuthService authService;
    @Autowired
    private SecretHashing secretHashing;
    @Autowired
    private AuthTestMapper authTestMapper;
    @Autowired
    private StubWechatManager wechatManager;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * 清理测试数据并准备完成 EHR 同步的有效员工。
     */
    @BeforeEach
    void setUp() {
        authTestMapper.deleteOperationAudits();
        authTestMapper.deleteRefreshTokens();
        authTestMapper.deleteUserSessions();
        authTestMapper.deleteRoleAssignments();
        authTestMapper.deleteExternalIdentities();
        authTestMapper.deleteLocalCredentials();
        authTestMapper.deleteEmployees();
        authTestMapper.deleteAuthTestSyncRun();
        authTestMapper.completeInitialEhrSync();
        authTestMapper.insertEmployee(new EmployeeSeed(
                1001L, "ehr-1001", "E1001", "Test Employee",
                secretHashing.identifier("mobile", "+8613800000000"), 2001L));
        wechatManager.openId = "openid-1001";
        wechatManager.phone = "+8613800000000";
    }

    /**
     * 验证本地密码登录建立后台客户端会话，刷新后仍保留同一客户端类型。
     */
    @Test
    void adminPasswordLoginCreatesAndRefreshesAdminWebSession() {
        authTestMapper.insertLocalCredential(1001L, passwordEncoder.encode("Admin#Pass123"));

        TokenPairDTO login = authService.adminLogin(
                "E1001", "Admin#Pass123", "127.0.0.1");
        assertThat(authTestMapper.selectLatestSessionClientType()).isEqualTo("ADMIN_WEB");
        assertThat(authTestMapper.selectLatestSessionAuthMethod()).isEqualTo("PASSWORD");

        TokenPairDTO refreshed = authService.refresh(login.refreshToken());
        assertThat(refreshed.accessToken()).isNotEqualTo(login.accessToken());
        assertThat(authTestMapper.selectLatestSessionClientType()).isEqualTo("ADMIN_WEB");
    }

    /**
     * 验证账号不存在和密码错误对外统一返回无效凭据，避免账号枚举。
     */
    @Test
    void adminPasswordLoginUsesGenericFailureForUnknownAccountAndWrongPassword() {
        authTestMapper.insertLocalCredential(1001L, passwordEncoder.encode("Admin#Pass123"));

        assertThatThrownBy(() -> authService.adminLogin(
                "UNKNOWN", "Admin#Pass123", "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThatThrownBy(() -> authService.adminLogin(
                "E1001", "wrong-password", "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    /**
     * 验证首次手机号匹配完成绑定，后续可仅凭 OpenID 自动登录。
     */
    @Test
    void firstPhoneLoginBindsAndLaterOpenIdLoginDoesNotNeedPhone() {
        TokenPairDTO first = authService.login("login-code", "phone-code-123456");
        assertThat(first.tokenType()).isEqualTo("Bearer");
        assertThat(first.accessExpiresIn()).isEqualTo(900L);
        assertThat(first.accessToken()).hasSizeGreaterThan(100);
        assertThat(first.refreshToken()).hasSizeGreaterThan(32);
        assertThat(authTestMapper.countExternalIdentities()).isOne();
        assertThat(authTestMapper.countEncryptedProviderSubjects()).isOne();
        assertThat(authTestMapper.countOperationAuditsByAction("WECHAT_BIND")).isOne();

        TokenPairDTO automatic = authService.login("another-login-code", null);
        assertThat(automatic.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(authTestMapper.countExternalIdentities()).isOne();
    }

    /**
     * 验证历史部分成功运行已经产生可用员工时，即使集成状态行未更新也允许登录。
     */
    @Test
    void partialEhrSyncWithProjectedEmployeeAllowsWechatLogin() {
        authTestMapper.resetInitialEhrSync();
        authTestMapper.insertPartialEhrSyncRun();

        TokenPairDTO login = authService.login(
                "login-code", "phone-code-123456");

        assertThat(login.accessToken()).isNotBlank();
        assertThat(authTestMapper.countExternalIdentities()).isOne();
    }

    /**
     * 验证刷新令牌单次轮换，并在重放旧令牌时撤销整个会话。
     */
    @Test
    void refreshRotatesAndReplayRevokesSession() {
        TokenPairDTO login = authService.login("login-code", "phone-code-123456");
        TokenPairDTO rotated = authService.refresh(login.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_REFRESH_REPLAYED");
        assertThat(authTestMapper.countRevokedSessions()).isOne();
    }

    /**
     * 验证同一代刷新令牌并发轮换时只有一个请求成功，另一个按重放撤销会话。
     *
     * @throws Exception 并发任务未能在测试时限内完成时抛出
     */
    @Test
    void concurrentRefreshAllowsOnlyOneRotation() throws Exception {
        TokenPairDTO login = authService.login("login-code", "phone-code-123456");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("auth-refresh-test");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        try {
            Future<RefreshAttempt> first = executor.submit(
                    () -> refreshAfterSignal(login.refreshToken(), ready, start));
            Future<RefreshAttempt> second = executor.submit(
                    () -> refreshAfterSignal(login.refreshToken(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RefreshAttempt> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results).filteredOn(RefreshAttempt::succeeded).hasSize(1);
            assertThat(results).extracting(RefreshAttempt::failureCode)
                    .containsExactlyInAnyOrder(null, "AUTH_REFRESH_REPLAYED");
            assertThat(authTestMapper.countRevokedSessions()).isOne();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 验证 EHR 中不存在的手机号不能创建本地员工或身份绑定。
     */
    @Test
    void unknownEhrPhoneCannotCreateUserOrBinding() {
        wechatManager.phone = "+8613900000000";
        assertThatThrownBy(() -> authService.login("login-code", "phone-code-123456"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).code())
                .isEqualTo("AUTH_EHR_EMPLOYEE_NOT_FOUND");
        assertThat(authTestMapper.countExternalIdentities()).isZero();
        assertThat(authTestMapper.countEmployees()).isOne();
    }

    private RefreshAttempt refreshAfterSignal(
            String refreshToken, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent refresh start signal timed out");
            }
            authService.refresh(refreshToken);
            return new RefreshAttempt(true, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent refresh test interrupted", exception);
        } catch (AuthException exception) {
            return new RefreshAttempt(false, exception.code());
        }
    }

    private record RefreshAttempt(boolean succeeded, String failureCode) {
    }

    /**
     * 为集成测试提供可控的微信身份桩实现。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class WechatStubConfiguration {
        @Bean
        @Primary
        StubWechatManager stubWechatManager() {
            return new StubWechatManager();
        }
    }

    /**
     * 返回测试预设微信身份和手机号的桩管理器。
     */
    static final class StubWechatManager implements WechatManager {
        private String openId;
        private String phone;

        @Override
        public WechatSession exchangeLoginCode(String loginCode) {
            return new WechatSession(openId, null);
        }

        @Override
        public String exchangePhoneCode(String phoneCode) {
            return phone;
        }
    }
}
