package com.biel.lifecamp.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一工作台服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class WorkbenchServiceApplication {
    /**
     * 启动统一工作台服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkbenchServiceApplication.class, args);
    }
}
