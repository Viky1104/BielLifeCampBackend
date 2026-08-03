package com.biel.lifecamp.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统支撑服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class SystemServiceApplication {
    /**
     * 启动系统支撑服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
