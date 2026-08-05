package com.biel.lifecamp.system.manager.impl;

import com.biel.lifecamp.system.common.exception.EhrSyncException;
import com.biel.lifecamp.system.config.properties.EhrProperties;
import com.biel.lifecamp.system.manager.EhrEmployeeManager;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSourceDTO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 使用正式 ESB 契约并发拉取 EHR 全部在职人员。
 *
 * <p>第一页同步获取全量分页元数据；后续页面通过有界线程池和固定大小滑动窗口并发请求。
 * 页面仍按页码顺序汇总，确保重复身份校验等后续逻辑具有稳定顺序。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
public final class EhrEmployeeManagerImpl implements EhrEmployeeManager {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EhrEmployeeManagerImpl.class);
    private final EhrProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService pageTaskExecutor;

    /**
     * 创建 EHR 人员接口访问实现。
     *
     * @param properties EHR 接口、分页、并发和内存保护配置
     * @param restClient 已配置连接和读取超时的 HTTP 客户端
     * @param objectMapper JSON 解析器
     * @param pageTaskExecutor EHR 分页专用有界任务执行器
     */
    public EhrEmployeeManagerImpl(EhrProperties properties, RestClient restClient,
                                  ObjectMapper objectMapper,
                                  ExecutorService pageTaskExecutor) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.pageTaskExecutor = pageTaskExecutor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot() {
        return fetchActiveEmployeeSnapshot(-1L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EhrEmployeeSnapshotDTO fetchActiveEmployeeSnapshot(long runId) {
        long startedNanos = System.nanoTime();
        LOGGER.info(
                "event=ehr_snapshot_fetch_started runId={} pageSize={} maxPages={} "
                        + "pageConcurrency={} maxRecords={} thread={}",
                runId, properties.getPageSize(), properties.getMaxPages(),
                properties.getPageConcurrency(), properties.getMaxRecords(), threadName());

        /*
         * 第一页必须同步获取。只有取得可信的总页数和总人数后，才能决定后续任务窗口，
         * 也才能在创建全量集合前执行人数上限检查。
         */
        Page firstPage = fetchPage(runId, 1);
        validateFirstPage(firstPage);
        LOGGER.info(
                "event=ehr_page_progress runId={} completedPages=1 totalPages={} "
                        + "remainingPages={} accumulatedRecords={} totalRecords={} "
                        + "remainingRecords={} progressPercent={} thread={}",
                runId, firstPage.totalPages(), remaining(1, firstPage.totalPages()),
                firstPage.employees().size(), firstPage.totalRecords(),
                remaining(firstPage.employees().size(), firstPage.totalRecords()),
                progressPercent(1, firstPage.totalPages()), threadName());

        /*
         * maxRecords 已通过校验，因此此处容量转换不会溢出。集合只保存最终来源 DTO，
         * 每页响应正文、JSON 树和页面临时引用数组都可在汇总后回收。
         */
        List<EhrEmployeeSourceDTO> employees =
                new ArrayList<>(Math.toIntExact(firstPage.totalRecords()));
        employees.addAll(firstPage.employees());
        fetchRemainingPages(runId, firstPage, employees);

        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos);
        LOGGER.info(
                "event=ehr_snapshot_fetch_completed runId={} actualRecords={} totalPages={} "
                        + "pageConcurrency={} durationMs={} thread={}",
                runId, employees.size(), firstPage.totalPages(),
                properties.getPageConcurrency(), durationMs, threadName());
        return new EhrEmployeeSnapshotDTO(
                firstPage.totalRecords(), firstPage.totalPages(), employees);
    }

    /**
     * 校验第一页返回的全量规模和单页边界。
     *
     * <p>该方法必须在创建最终 ArrayList 之前调用。maxRecords 是针对堆内存的硬保护，
     * maxPages 和 pageSize 则用于拒绝不可能成立的分页元数据。</p>
     *
     * @param firstPage 第一页数据及全量分页元数据
     */
    private void validateFirstPage(Page firstPage) {
        if (firstPage.totalPages() < 1
                || firstPage.totalPages() > properties.getMaxPages()) {
            throw new EhrSyncException("EHR_INVALID_PAGE_COUNT",
                    "EHR returned an invalid total page count");
        }
        if (firstPage.totalRecords() > properties.getMaxRecords()) {
            throw new EhrSyncException("EHR_RECORD_LIMIT_EXCEEDED",
                    "EHR declared record count exceeds the configured safety limit");
        }
        long maximumRecordsByPages =
                (long) firstPage.totalPages() * properties.getPageSize();
        if (firstPage.totalRecords() > maximumRecordsByPages) {
            throw new EhrSyncException("EHR_RESPONSE_INVALID",
                    "EHR paging metadata cannot contain the declared record count");
        }
        validatePageSize(1, firstPage);
    }

    /**
     * 使用固定大小滑动窗口并发拉取后续页面，并按页码顺序合并到最终集合。
     *
     * <p>本方法不会一次提交 totalPages 个任务。初始只提交 pageConcurrency 个页面，
     * 每按顺序汇总一页才补交下一页，因此线程池队列、Future 和待汇总页面始终有界。
     * 任一页面失败时取消窗口内剩余任务，禁止继续放大对 EHR 的请求流量。</p>
     *
     * @param firstPage 第一页及稳定分页元数据
     * @param employees 最终来源人员集合
     */
    private void fetchRemainingPages(
            long runId, Page firstPage, List<EhrEmployeeSourceDTO> employees) {
        int nextPageNo = 2;
        Deque<PendingPage> pendingPages =
                new ArrayDeque<>(properties.getPageConcurrency());
        try {
            while (nextPageNo <= firstPage.totalPages()
                    && pendingPages.size() < properties.getPageConcurrency()) {
                pendingPages.addLast(submitPage(runId, nextPageNo));
                nextPageNo++;
            }

            /*
             * Future 按页码入队并按页码出队。即使后面的页面先完成，也只在固定窗口内
             * 暂存，既保留稳定顺序，也不会因为某个慢页导致所有后续页面堆积在内存中。
             */
            while (!pendingPages.isEmpty()) {
                PendingPage pendingPage = pendingPages.removeFirst();
                Page page = awaitPage(pendingPage);
                validatePageConsistency(firstPage, pendingPage.pageNo(), page);
                employees.addAll(page.employees());
                LOGGER.info(
                        "event=ehr_page_progress runId={} completedPages={} totalPages={} "
                                + "remainingPages={} pageRecords={} accumulatedRecords={} "
                                + "totalRecords={} remainingRecords={} progressPercent={} "
                                + "pendingPages={} thread={}",
                        runId, pendingPage.pageNo(), firstPage.totalPages(),
                        remaining(pendingPage.pageNo(), firstPage.totalPages()),
                        page.employees().size(), employees.size(), firstPage.totalRecords(),
                        remaining(employees.size(), firstPage.totalRecords()),
                        progressPercent(pendingPage.pageNo(), firstPage.totalPages()),
                        pendingPages.size(), threadName());

                if (nextPageNo <= firstPage.totalPages()) {
                    pendingPages.addLast(submitPage(runId, nextPageNo));
                    nextPageNo++;
                }
            }
        } catch (RuntimeException | Error exception) {
            cancelPendingPages(runId, pendingPages);
            throw exception;
        }
    }

    /**
     * 提交一个分页请求。线程池饱和时使用拒绝策略，不在同步调用线程中退化执行。
     *
     * @param pageNo 页码
     * @return 带页码的待完成任务
     */
    private PendingPage submitPage(long runId, int pageNo) {
        Future<Page> future = pageTaskExecutor.submit(() -> fetchPage(runId, pageNo));
        return new PendingPage(pageNo, future);
    }

    /**
     * 等待指定页完成，并保留 Java 线程中断语义。
     *
     * @param pendingPage 待完成分页
     * @return 已解析页面
     */
    private Page awaitPage(PendingPage pendingPage) {
        try {
            return pendingPage.future().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EhrSyncException("EHR_PAGE_FETCH_INTERRUPTED",
                    "EHR page fetch was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new EhrSyncException("EHR_PAGE_FETCH_FAILED",
                    "EHR page fetch failed", cause);
        }
    }

    /**
     * 校验并发返回页面仍属于第一页声明的同一个全量分页视图。
     *
     * @param firstPage 第一页元数据
     * @param pageNo 当前页码
     * @param page 当前页数据
     */
    private void validatePageConsistency(Page firstPage, int pageNo, Page page) {
        if (page.totalPages() != firstPage.totalPages()
                || page.totalRecords() != firstPage.totalRecords()) {
            throw new EhrSyncException("EHR_PAGING_CHANGED",
                    "EHR paging metadata changed while fetching the snapshot");
        }
        validatePageSize(pageNo, page);
    }

    /**
     * 限制每页实际记录数，避免 EHR 忽略 pageSize 后扩大单页内存占用。
     *
     * @param pageNo 页码，仅用于脱敏日志定位
     * @param page 当前页
     */
    private void validatePageSize(int pageNo, Page page) {
        if (page.employees().size() > properties.getPageSize()) {
            throw new EhrSyncException("EHR_PAGE_SIZE_EXCEEDED",
                    "EHR page contains more records than requested");
        }
        LOGGER.debug("EHR 人员快照分页拉取完成，pageNo={}，pageRecords={}",
                pageNo, page.employees().size());
    }

    /**
     * 取消异常窗口中尚未汇总的分页任务。
     *
     * @param pendingPages 尚未汇总的分页任务
     */
    private void cancelPendingPages(long runId, Deque<PendingPage> pendingPages) {
        int cancelledCount = 0;
        for (PendingPage pendingPage : pendingPages) {
            if (pendingPage.future().cancel(true)) {
                cancelledCount++;
            }
        }
        pendingPages.clear();
        if (cancelledCount > 0) {
            LOGGER.warn(
                    "event=ehr_page_window_cancelled runId={} cancelledPages={} thread={}",
                    runId, cancelledCount, threadName());
        }
    }

    /**
     * 拉取单页人员数据。
     *
     * <p>requestId 仅用于 ESB 链路追踪；日志不记录认证头、手机号或响应正文。</p>
     *
     * @param runId 同步运行标识
     * @param pageNo 从 1 开始的页码
     * @return 已解析的分页数据
     */
    private Page fetchPage(long runId, int pageNo) {
        String requestId = UUID.randomUUID().toString();
        long startedNanos = System.nanoTime();
        LOGGER.info(
                "event=ehr_page_fetch_started runId={} pageNo={} requestId={} thread={}",
                runId, pageNo, requestId, threadName());
        try {
            byte[] body = restClient.get()
                    .uri(builder -> builder
                            .queryParam("ts", properties.getFullSince())
                            .queryParam("pageSize", properties.getPageSize())
                            .queryParam("pageNo", pageNo)
                            .queryParam("state", "Y")
                            .build())
                    .header("sourceSystem", properties.getSourceSystem())
                    .header("targetSystem", properties.getTargetSystem())
                    .header("serviceName", properties.getServiceName())
                    .header("requestId", requestId)
                    .header("routeId", properties.getRouteId())
                    .header("EsbAuth", properties.getAuth())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(byte[].class);
            Page page = parsePage(body);
            LOGGER.info(
                    "event=ehr_page_fetch_completed runId={} pageNo={} pageRecords={} "
                            + "totalPages={} durationMs={} requestId={} thread={}",
                    runId, pageNo, page.employees().size(), page.totalPages(),
                    elapsedMillis(startedNanos), requestId, threadName());
            return page;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=ehr_page_fetch_failed runId={} pageNo={} durationMs={} "
                            + "requestId={} failureType={} thread={}",
                    runId, pageNo, elapsedMillis(startedNanos), requestId,
                    exception.getClass().getSimpleName(), threadName());
            throw exception;
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private int progressPercent(int completed, int total) {
        return total < 1 ? 0 : Math.min(100, (int) ((long) completed * 100 / total));
    }

    private long remaining(long completed, long total) {
        return Math.max(0, total - completed);
    }

    private String threadName() {
        return Thread.currentThread().getName();
    }

    /**
     * 解析并校验 EHR 单页响应的最小契约。
     *
     * @param body EHR 原始响应字节，仅在内存中解析且禁止写入日志
     * @return 分页数据
     * @throws EhrSyncException 响应码、分页元数据或 JSON 结构不合法时抛出
     */
    private Page parsePage(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"0".equals(text(root, "code"))) {
                throw new EhrSyncException("EHR_RESPONSE_REJECTED",
                        "EHR rejected the personnel snapshot request");
            }
            JsonNode data = root.path("data");
            JsonNode rows = data.path("data");
            long totalRecords = data.path("totalRecords").asLong(-1);
            int totalPages = data.path("totalPages").asInt(-1);
            if (!rows.isArray() || totalRecords < 0 || totalPages < 0) {
                throw new EhrSyncException("EHR_RESPONSE_INVALID",
                        "EHR returned an invalid personnel response");
            }
            List<EhrEmployeeSourceDTO> employees = new ArrayList<>(rows.size());
            for (JsonNode row : rows) {
                employees.add(mapEmployee(row));
            }

            /*
             * Page 是实现内部对象，后续只读取。直接保留 ArrayList 可避免每页再复制一次
             * 引用数组；页面汇总后该临时数组即可被垃圾回收。
             */
            return new Page(totalRecords, totalPages, employees);
        } catch (EhrSyncException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EhrSyncException("EHR_RESPONSE_INVALID",
                    "EHR personnel response could not be parsed", exception);
        }
    }

    /**
     * 将 EHR 字段名映射为隔离的来源 DTO。
     *
     * <p>此处不做业务标准化，确保字段校验集中在快照校验器中并可独立测试。</p>
     *
     * @param row EHR 单个人员节点
     * @return EHR 原始语义人员对象
     */
    private EhrEmployeeSourceDTO mapEmployee(JsonNode row) {
        return new EhrEmployeeSourceDTO(
                text(row, "pkPsndoc"), text(row, "code"), text(row, "name"),
                text(row, "sex"), text(row, "birthdate"), text(row, "mobile"),
                text(row, "glbdef8"), text(row, "pkDeptCode"),
                text(row, "pkDeptName"), text(row, "jobglbdef21code"),
                firstText(row, "jobglbdef21name", "jobglbdef21"),
                text(row, "jobglbdef29"), text(row, "jobglbdef27"),
                text(row, "titletechpost"), text(row, "pkJobcode"),
                text(row, "pkJobname"), text(row, "pkPostCode"),
                text(row, "pkPostName"), text(row, "begindate"),
                text(row, "enddate"), text(row, "modifiedtime"),
                text(row, "creationtime"));
    }

    /**
     * 兼容 EHR 职级名称的新旧字段名。
     *
     * @param node 人员节点
     * @param first 首选字段名
     * @param second 兼容字段名
     * @return 第一个非空字段值
     */
    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null ? text(node, second) : value;
    }

    /**
     * 读取并清理字符串字段。
     *
     * @param node JSON 节点
     * @param field 字段名
     * @return 去除首尾空白后的字段值，缺失或空白时返回 {@code null}
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text.trim();
    }

    /**
     * EHR 单页数据及其全量分页元数据。
     *
     * @param totalRecords EHR 声明的全量记录数
     * @param totalPages EHR 声明的总页数
     * @param employees 当前页人员数据
     */
    private record Page(long totalRecords, int totalPages,
                        List<EhrEmployeeSourceDTO> employees) {
    }

    /**
     * 滑动窗口中的单个分页任务。
     *
     * @param pageNo 页码
     * @param future 分页异步结果
     */
    private record PendingPage(int pageNo, Future<Page> future) {
    }
}
