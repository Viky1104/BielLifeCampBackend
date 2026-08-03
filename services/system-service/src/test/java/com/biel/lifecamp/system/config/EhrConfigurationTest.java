package com.biel.lifecamp.system.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.config.properties.EhrProperties;
import org.junit.jupiter.api.Test;

/**
 * EHR 客户端启动配置测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-30
 */
class EhrConfigurationTest {

    /**
     * 验证 EHR 同步启用时拒绝缺失的人员标识摘要密钥。
     */
    @Test
    void rejectsEnabledEhrSyncWithoutIdentifierPepper() {
        EhrProperties ehrProperties = new EhrProperties();
        ehrProperties.setEnabled(true);
        ehrProperties.setAuth("esb-auth-for-test");
        AuthProperties authProperties = new AuthProperties();

        assertThatThrownBy(() -> new EhrConfiguration().ehrEmployeeManager(
                ehrProperties, authProperties, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_IDENTIFIER_PEPPER");
    }

    /**
     * 验证 EHR 同步启用时拒绝零并发，防止线程池配置退化为不可运行状态。
     */
    @Test
    void rejectsEnabledEhrSyncWithoutPositivePageConcurrency() {
        EhrProperties ehrProperties = validEhrProperties();
        ehrProperties.setPageConcurrency(0);
        AuthProperties authProperties = validAuthProperties();

        assertThatThrownBy(() -> new EhrConfiguration().ehrEmployeeManager(
                ehrProperties, authProperties, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EHR_PAGE_CONCURRENCY");
    }

    /**
     * 验证单页人数不能超过全量人数上限，避免错误配置绕过内存保护。
     */
    @Test
    void rejectsPageSizeAboveMaximumRecordCount() {
        EhrProperties ehrProperties = validEhrProperties();
        ehrProperties.setPageSize(1_001);
        ehrProperties.setMaxRecords(1_000);
        AuthProperties authProperties = validAuthProperties();

        assertThatThrownBy(() -> new EhrConfiguration().ehrEmployeeManager(
                ehrProperties, authProperties, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EHR_MAX_RECORDS");
    }

    /**
     * 验证持久化批次不能无限增大，避免 SQL 参数和数据库包体失控。
     */
    @Test
    void rejectsOversizedPersistenceBatch() {
        EhrProperties ehrProperties = validEhrProperties();
        ehrProperties.setPersistenceBatchSize(1_001);
        AuthProperties authProperties = validAuthProperties();

        assertThatThrownBy(() -> new EhrConfiguration().ehrEmployeeManager(
                ehrProperties, authProperties, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EHR_PERSISTENCE_BATCH_SIZE");
    }

    private EhrProperties validEhrProperties() {
        EhrProperties properties = new EhrProperties();
        properties.setEnabled(true);
        properties.setAuth("esb-auth-for-test");
        return properties;
    }

    private AuthProperties validAuthProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setIdentifierPepper(
                "identifier-pepper-for-test-1234567890");
        return properties;
    }
}
