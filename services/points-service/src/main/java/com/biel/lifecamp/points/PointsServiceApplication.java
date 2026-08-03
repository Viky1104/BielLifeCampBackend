package com.biel.lifecamp.points;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 积分服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class PointsServiceApplication {
    /**
     * 启动积分服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PointsServiceApplication.class, args);
    }
}
