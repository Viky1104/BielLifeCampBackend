package com.biel.lifecamp.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通信服务启动入口。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@SpringBootApplication
public class CommunicationServiceApplication {
    /**
     * 启动通信服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommunicationServiceApplication.class, args);
    }
}
