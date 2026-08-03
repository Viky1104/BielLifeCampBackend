package com.biel.lifecamp.starter.web.config;

import com.biel.lifecamp.starter.web.exception.GlobalExceptionHandler;
import com.biel.lifecamp.starter.web.filter.RequestIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动配置请求链路标识和统一 MVC 异常适配器。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@AutoConfiguration
public class WebStarterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
