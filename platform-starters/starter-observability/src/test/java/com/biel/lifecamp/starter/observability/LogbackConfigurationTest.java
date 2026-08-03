package com.biel.lifecamp.starter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证平台共享 Logback 配置的加载、日志级别以及 Log4j 桥接后的文件分流。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
@SpringBootTest(classes = LogbackConfigurationTest.TestApplication.class,
        properties = {"spring.profiles.active=file-logging",
        "spring.application.name=observability-test",
        "logging.file.path=target/test-logs"})
class LogbackConfigurationTest {
    /**
     * 控制台、全量文件和错误文件必须全部装配为 ECS 结构化输出。
     */
    @Test
    void loadsStructuredConsoleAndRollingFileAppenders() {
        LoggerContext context = loggerContext();

        assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("CONSOLE")).isInstanceOf(OutputStreamAppender.class);
        assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("FILE")).isInstanceOf(RollingFileAppender.class);
        assertThat(context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                .getAppender("ERROR_FILE")).isInstanceOf(RollingFileAppender.class);
        assertThat(context.getLogger("com.alibaba.nacos").getEffectiveLevel())
                .isEqualTo(Level.WARN);
        assertThat(context.getLogger("com.biel.lifecamp").getEffectiveLevel())
                .isEqualTo(Level.INFO);
    }

    /**
     * Log4j API 日志必须进入 SLF4J/Logback，错误文件只接收 ERROR 级别事件。
     *
     * @throws Exception 读取测试日志文件失败
     */
    @Test
    void routesLog4jEventsAndSeparatesErrorOutput() throws Exception {
        LoggerContext context = loggerContext();
        String eventSuffix = Long.toString(System.nanoTime());
        String infoEvent = "log4j_bridge_info_" + eventSuffix;
        String errorEvent = "log4j_bridge_error_" + eventSuffix;

        LogManager.getLogger("com.biel.lifecamp.log4j.bridge").info(infoEvent);
        LogManager.getLogger("com.biel.lifecamp.log4j.bridge").error(errorEvent);

        String applicationLog = readAppenderFile(context, "FILE");
        String errorLog = readAppenderFile(context, "ERROR_FILE");
        assertThat(applicationLog)
                .contains("\"service\":{\"name\":\"observability-test\"")
                .contains("\"message\":\"" + infoEvent + "\"")
                .contains("\"message\":\"" + errorEvent + "\"")
                .contains("\"ecs\":{\"version\":\"8.11\"}");
        assertThat(errorLog)
                .contains("\"message\":\"" + errorEvent + "\"")
                .doesNotContain("\"message\":\"" + infoEvent + "\"");
    }

    private LoggerContext loggerContext() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(LoggerContext.class);
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    private String readAppenderFile(LoggerContext context, String appenderName) throws Exception {
        RollingFileAppender<?> appender = (RollingFileAppender<?>) context
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender(appenderName);
        assertThat(appender).isNotNull();
        return Files.readString(Path.of(appender.getFile()), StandardCharsets.UTF_8);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
