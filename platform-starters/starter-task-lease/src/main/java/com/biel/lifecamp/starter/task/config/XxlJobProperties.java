package com.biel.lifecamp.starter.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 类型安全的 XXL-JOB 执行器配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
@ConfigurationProperties("xxl.job")
public class XxlJobProperties {
    /** 是否启用 XXL-JOB 执行器。 */
    private boolean enabled;
    /** 调度中心地址，多个地址使用逗号分隔。 */
    private String adminAddresses;
    /** 执行器访问令牌。 */
    private String accessToken;
    /** 执行器应用名称。 */
    private String appName;
    /** 执行器注册地址。 */
    private String address;
    /** 执行器绑定 IP。 */
    private String ip;
    /** 执行器监听端口。 */
    private int port = 9999;
    /** 执行日志目录。 */
    private String logPath = "logs/xxl-job";
    /** 执行日志保留天数。 */
    private int logRetentionDays = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminAddresses() {
        return adminAddresses;
    }

    public void setAdminAddresses(String value) {
        this.adminAddresses = value;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String value) {
        this.accessToken = value;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String value) {
        this.appName = value;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String value) {
        this.address = value;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String value) {
        this.ip = value;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int value) {
        this.port = value;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String value) {
        this.logPath = value;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(int value) {
        this.logRetentionDays = value;
    }
}
