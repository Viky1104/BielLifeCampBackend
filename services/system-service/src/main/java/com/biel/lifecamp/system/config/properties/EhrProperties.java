package com.biel.lifecamp.system.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 正式 EHR 人员接口配置。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@ConfigurationProperties("platform.ehr")
public class EhrProperties {
    /** 是否启用 EHR 人员同步能力。 */
    private boolean enabled;

    /** EHR 人员查询接口地址。 */
    private String url = "http://esb.biel.com/api/esb/EHR_GetData";

    /** ESB 调用方系统编码。 */
    private String sourceSystem = "WeChat";

    /** ESB 目标系统编码。 */
    private String targetSystem = "EHR-Micro";

    /** ESB 人员查询服务名称。 */
    private String serviceName = "ehr-micro-getpsninfo";

    /** ESB 路由标识。 */
    private String routeId = "HZ";

    /** ESB 认证凭据，禁止写入日志或普通配置文档。 */
    private String auth;

    /** 全量同步使用的最早变更时间。 */
    private String fullSince = "2000-01-01 00:00:00";

    /** EHR 单页人员数量。 */
    private int pageSize = 1000;

    /** 允许拉取的最大页数，用于限制异常分页响应。 */
    private int maxPages = 10000;

    /**
     * EHR 后续分页的最大并发请求数。
     *
     * <p>第一页仍同步获取分页元数据，后续页面使用固定大小线程池拉取。该值同时也是
     * 单次同步允许存在的最大在途页面数，禁止使用无界任务队列。</p>
     */
    private int pageConcurrency = 4;

    /**
     * 单次全量快照允许的最大人员数。
     *
     * <p>客户端在读取第一页分页元数据后、创建全量集合前执行该检查，用于阻止异常
     * totalRecords 触发超大数组分配。生产值应结合 system-service 堆内存评估。</p>
     */
    private int maxRecords = 200000;

    /**
     * 人员投影单次批量写入数量。
     *
     * <p>正常路径使用多值 INSERT/UPSERT 降低数据库往返；批次失败时回滚保存点并降级
     * 为逐人写入，从而继续隔离问题人员。</p>
     */
    private int persistenceBatchSize = 500;

    /** EHR HTTP 建连超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** EHR HTTP 单次读取超时。 */
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getFullSince() {
        return fullSince;
    }

    public void setFullSince(String fullSince) {
        this.fullSince = fullSince;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getPageConcurrency() {
        return pageConcurrency;
    }

    public void setPageConcurrency(int pageConcurrency) {
        this.pageConcurrency = pageConcurrency;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public int getPersistenceBatchSize() {
        return persistenceBatchSize;
    }

    public void setPersistenceBatchSize(int persistenceBatchSize) {
        this.persistenceBatchSize = persistenceBatchSize;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
