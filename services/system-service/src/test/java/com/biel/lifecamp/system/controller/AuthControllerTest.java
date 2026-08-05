package com.biel.lifecamp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.model.dto.AuthorizationSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EmployeeDTO;
import com.biel.lifecamp.system.model.dto.response.CurrentSubjectResp;
import com.biel.lifecamp.system.service.AuthService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 当前登录员工接口测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
class AuthControllerTest {
    /**
     * 验证组织主数据尚未映射不会阻塞当前员工信息查询。
     */
    @Test
    void unresolvedOrganizationIsReturnedAsZero() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        when(authService.current(1001L, "system-service"))
                .thenReturn(new AuthorizationSnapshotDTO(
                        new EmployeeDTO(1001L, "E1001", "Test Employee", null,
                                "ACTIVE", "ACTIVE", 1L),
                        List.of("EMPLOYEE"), List.of(), List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IdentityContext.REQUEST_ATTRIBUTE, new IdentityContext(
                "1001", "0", "11111111-1111-1111-1111-111111111111",
                "MINI_PROGRAM", 1L, Set.of("EMPLOYEE"), Set.of(),
                List.of(), Set.of("wechat")));

        ApiResponse<CurrentSubjectResp> response = controller.me(request);

        assertThat(response.data().organizationId()).isEqualTo("0");
    }
}
