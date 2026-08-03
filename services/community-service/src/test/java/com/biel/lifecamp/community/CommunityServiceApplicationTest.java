package com.biel.lifecamp.community;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 社区服务应用上下文测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.sentinel.enabled=false",
        "spring.flyway.enabled=false", "xxl.job.enabled=false"})
class CommunityServiceApplicationTest {
    /**
     * 验证应用在隔离外部依赖的测试配置下能够正常启动。
     */
    @Test
    void contextLoads() {
    }
}
