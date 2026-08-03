package com.biel.lifecamp.orderview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一订单查询服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class OrderViewServiceApplication {
    /**
     * 启动统一订单查询服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderViewServiceApplication.class, args);
    }
}
