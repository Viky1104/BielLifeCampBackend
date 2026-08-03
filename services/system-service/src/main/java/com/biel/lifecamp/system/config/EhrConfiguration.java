package com.biel.lifecamp.system.config;

import com.biel.lifecamp.system.config.properties.AuthProperties;
import com.biel.lifecamp.system.config.properties.EhrProperties;
import com.biel.lifecamp.system.manager.EhrEmployeeManager;
import com.biel.lifecamp.system.manager.impl.EhrEmployeeManagerImpl;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * EHR 人员接口客户端配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EhrProperties.class)
public class EhrConfiguration {
    private static final int MAX_PAGE_CONCURRENCY = 16;
    private static final int MAX_PERSISTENCE_BATCH_SIZE = 1000;

    /**
     * 创建 EHR 分页专用的有界线程池。
     *
     * <p>线程数和队列容量都受 pageConcurrency 限制。同步编排层还会采用滑动窗口，
     * 确保单次运行最多只提交 pageConcurrency 个未汇总页面。核心线程空闲后回收，
     * 应用关闭时最多等待一个 EHR 读取超时周期。</p>
     *
     * @param properties EHR 分页及超时配置
     * @return 仅供 EHR 分页拉取使用的任务执行器
     */
    @Bean(name = "ehrPageTaskExecutor")
    ThreadPoolTaskExecutor ehrPageTaskExecutor(EhrProperties properties) {
        /*
         * disabled 状态也会创建惰性线程池，但不会启动工作线程。这里用 1 兜底只是为了让
         * Spring 能完成 Bean 构造；EHR 启用时 validate 会拒绝非法并发配置。
         */
        int concurrency = Math.max(1, properties.getPageConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(concurrency);
        executor.setKeepAliveSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("ehr-page-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        Duration readTimeout = properties.getReadTimeout();
        executor.setAwaitTerminationMillis(readTimeout == null
                ? Duration.ofSeconds(30).toMillis()
                : Math.max(1_000L, readTimeout.toMillis()));
        return executor;
    }

    @Bean
    EhrEmployeeManager ehrEmployeeManager(EhrProperties properties,
                                          AuthProperties authProperties,
                                          ObjectMapper objectMapper,
                                          @Qualifier("ehrPageTaskExecutor")
                                          ThreadPoolTaskExecutor pageTaskExecutor) {
        if (!properties.isEnabled()) {
            return () -> {
                throw new IllegalStateException("EHR synchronization is disabled");
            };
        }
        validate(properties, authProperties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .build();
        return new EhrEmployeeManagerImpl(
                properties, restClient, objectMapper,
                pageTaskExecutor.getThreadPoolExecutor());
    }

    private void validate(EhrProperties properties, AuthProperties authProperties) {
        if (!StringUtils.hasText(properties.getUrl())
                || !StringUtils.hasText(properties.getAuth())
                || !StringUtils.hasText(properties.getSourceSystem())
                || !StringUtils.hasText(properties.getTargetSystem())
                || !StringUtils.hasText(properties.getServiceName())
                || !StringUtils.hasText(properties.getRouteId())) {
            throw new IllegalStateException(
                    "Enabled EHR synchronization requires complete ESB configuration");
        }
        if (properties.getPageSize() < 1 || properties.getMaxPages() < 1) {
            throw new IllegalStateException(
                    "EHR_PAGE_SIZE and EHR_MAX_PAGES must be positive");
        }
        if (properties.getPageConcurrency() < 1
                || properties.getPageConcurrency() > MAX_PAGE_CONCURRENCY) {
            throw new IllegalStateException(
                    "EHR_PAGE_CONCURRENCY must be between 1 and "
                            + MAX_PAGE_CONCURRENCY);
        }
        if (properties.getMaxRecords() < 1
                || properties.getPageSize() > properties.getMaxRecords()) {
            throw new IllegalStateException(
                    "EHR_MAX_RECORDS must be positive and not below EHR_PAGE_SIZE");
        }
        if (properties.getPersistenceBatchSize() < 1
                || properties.getPersistenceBatchSize()
                > MAX_PERSISTENCE_BATCH_SIZE) {
            throw new IllegalStateException(
                    "EHR_PERSISTENCE_BATCH_SIZE must be between 1 and "
                            + MAX_PERSISTENCE_BATCH_SIZE);
        }
        if (!StringUtils.hasText(authProperties.getIdentifierPepper())
                || authProperties.getIdentifierPepper().length() < 32) {
            throw new IllegalStateException(
                    "Enabled EHR synchronization requires AUTH_IDENTIFIER_PEPPER"
                    + " with at least 32 characters");
        }
    }
}
