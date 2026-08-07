package com.biel.lifecamp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.biel.lifecamp.system.dao.ProfileMapper;
import com.biel.lifecamp.system.model.dto.CurrentProfileDTO;
import com.biel.lifecamp.system.model.dto.ProfileAvatarReplacementDTO;
import com.biel.lifecamp.system.service.ProfilePersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway and MyBatis integration tests for profile persistence.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "spring.flyway.enabled=true",
        "xxl.job.enabled=false"
})
@Transactional
class ProfilePersistenceServiceImplIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileMapper profileMapper;
    @Autowired
    private ProfilePersistenceService persistenceService;

    @Test
    void createsAndUpdatesProfileAgainstMigratedSchema() {
        long employeeId = 880001L;
        jdbcTemplate.update("""
                INSERT INTO sys_employee
                    (id, ehr_person_id, employee_no, display_name,
                     primary_org_name, position_name, employment_status,
                     binding_status, account_status, authz_version)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'BOUND', 'ACTIVE', 1)
                """, employeeId, "EHR-880001", "E880001", "Profile Test",
                "信息技术中心", "开发工程师");
        jdbcTemplate.update("""
                INSERT INTO sys_external_identity
                    (id, employee_id, provider_type, provider_tenant,
                     provider_subject_hash, status, created_at, updated_at)
                VALUES (?, ?, 'WECHAT', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 990001L, employeeId, "test-app",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        persistenceService.updateNickname(employeeId, "小营友");
        ProfileAvatarReplacementDTO first = persistenceService.replaceAvatar(
                employeeId, "profiles/avatars/first.png");
        ProfileAvatarReplacementDTO second = persistenceService.replaceAvatar(
                employeeId, "profiles/avatars/second.png");
        CurrentProfileDTO profile = profileMapper.selectCurrentProfile(employeeId);

        assertThat(first.oldObjectKey()).isNull();
        assertThat(second.oldObjectKey()).isEqualTo("profiles/avatars/first.png");
        assertThat(profile.organizationName()).isEqualTo("信息技术中心");
        assertThat(profile.positionName()).isEqualTo("开发工程师");
        assertThat(profile.nickname()).isEqualTo("小营友");
        assertThat(profile.avatarObjectKey()).isEqualTo("profiles/avatars/second.png");
    }
}
