package com.biel.lifecamp.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * SecurityUtils 当前登录用户读取测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class SecurityUtilsTest {
    /**
     * 验证工具类只读取过滤器写入的可信请求属性。
     */
    @Test
    void readsVerifiedLoginUserFromRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(LoginUser.REQUEST_ATTRIBUTE, loginUser());

        LoginUser actual = SecurityUtils.getLoginUser(request);

        assertThat(actual.employeeId()).isEqualTo("1001");
        assertThat(actual.employeeNo()).isEqualTo("E1001");
        assertThat(SecurityUtils.getClientType(request)).isEqualTo("ADMIN_WEB");
        assertThat(SecurityUtils.isClientType(request, "ADMIN_WEB")).isTrue();
        assertThat(actual.permissions()).containsExactly("system:profile:read");
    }

    /**
     * 验证客户端直接提交请求头不能伪造登录用户。
     */
    @Test
    void rejectsRequestWithoutVerifiedLoginUserAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Employee-Id", "1001");

        assertThatThrownBy(() -> SecurityUtils.getLoginUser(request))
                .isInstanceOf(SecurityUtils.MissingLoginUserException.class);
    }

    private LoginUser loginUser() {
        return new LoginUser(
                "1001", "E1001", "Test Employee", "2001",
                "11111111-1111-1111-1111-111111111111", "ADMIN_WEB",
                "system-service", 1L,
                Set.of("EMPLOYEE"), Set.of("system:profile:read"),
                List.of(new IdentityContext.DataScope("SELF", "1001")),
                Set.of("wechat"), Instant.parse("2026-07-31T08:00:00Z"));
    }
}
