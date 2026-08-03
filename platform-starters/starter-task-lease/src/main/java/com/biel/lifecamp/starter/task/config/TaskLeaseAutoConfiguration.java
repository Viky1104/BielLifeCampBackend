package com.biel.lifecamp.starter.task.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 仅在功能开关开启时创建 XXL-JOB 执行器。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@AutoConfiguration
@EnableConfigurationProperties(XxlJobProperties.class)
public class TaskLeaseAutoConfiguration {
    @Bean(destroyMethod = "destroy")
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
    XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getAppName());
        executor.setAddress(properties.getAddress());
        executor.setIp(properties.getIp());
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }
}
