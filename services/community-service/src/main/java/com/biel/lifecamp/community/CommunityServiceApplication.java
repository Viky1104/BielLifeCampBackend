package com.biel.lifecamp.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 社区服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class CommunityServiceApplication {
    /**
     * 启动社区服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
