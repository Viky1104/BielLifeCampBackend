package com.biel.lifecamp.life;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 生活服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class LifeServiceApplication {
    /**
     * 启动生活服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LifeServiceApplication.class, args);
    }
}
