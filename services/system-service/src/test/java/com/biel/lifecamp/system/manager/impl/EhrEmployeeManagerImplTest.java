package com.biel.lifecamp.system.manager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.common.exception.EhrSyncException;
import com.biel.lifecamp.system.config.properties.EhrProperties;
import com.biel.lifecamp.system.model.dto.EhrEmployeeSnapshotDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 EHR 分页拉取的并发上限、结果顺序以及内存保护。
 *
 * <p>测试使用本机 HTTP 服务保留真实阻塞 I/O 行为，避免只用 Mock 证明不了分页请求
 * 是否真正并发。测试服务只返回最小脱敏字段，不包含生产人员数据。</p>
 *
 * @author Biel Life Camp Team
 * @since 2026-07-31
 */
class EhrEmployeeManagerImplTest {
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private ThreadPoolTaskExecutor pageExecutor;

    /**
     * 释放测试 HTTP 服务和分页线程池，防止测试完成后遗留非守护线程。
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }
        if (pageExecutor != null) {
            pageExecutor.shutdown();
        }
    }

    /**
     * 后续分页应并发请求，但最终快照仍按页码顺序汇总。
     *
     * <p>第 2～4 页通过门闩互相等待。只有三个请求同时进入服务端，测试才能继续，
     * 因而可以证明实现不是串行请求；服务端同时记录最大在途请求数，验证它不会超过配置值。</p>
     */
    @Test
    void fetchesPagesConcurrentlyWithinBoundAndKeepsPageOrder() throws Exception {
        EhrProperties properties = properties(3, 10);
        CountDownLatch firstWindowStarted = new CountDownLatch(3);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        List<Integer> requestedPages =
                Collections.synchronizedList(new ArrayList<>());
        EhrEmployeeManagerImpl manager = manager(properties, pageNo -> {
            requestedPages.add(pageNo);
            if (pageNo >= 2 && pageNo <= 4) {
                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);
                firstWindowStarted.countDown();
                try {
                    if (!firstWindowStarted.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent page window was not filled");
                    }
                } finally {
                    inFlight.decrementAndGet();
                }
            }
            return page(5, 5, pageNo);
        });

        EhrEmployeeSnapshotDTO snapshot = manager.fetchActiveEmployeeSnapshot();

        assertThat(maxInFlight.get()).isEqualTo(3);
        assertThat(requestedPages).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(snapshot.employees())
                .extracting(employee -> employee.employeeNo())
                .containsExactly("E-1", "E-2", "E-3", "E-4", "E-5");
    }

    /**
     * EHR 声明人数超过内存保护上限时应在第一页后立即失败，禁止继续创建分页任务。
     */
    @Test
    void rejectsSnapshotAboveConfiguredRecordLimitBeforeSchedulingMorePages()
            throws Exception {
        EhrProperties properties = properties(3, 1_000);
        List<Integer> requestedPages =
                Collections.synchronizedList(new ArrayList<>());
        EhrEmployeeManagerImpl manager = manager(properties, pageNo -> {
            requestedPages.add(pageNo);
            return page(1_001, 2, pageNo);
        });

        assertThatThrownBy(manager::fetchActiveEmployeeSnapshot)
                .isInstanceOfSatisfying(EhrSyncException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("EHR_RECORD_LIMIT_EXCEEDED"));
        assertThat(requestedPages).containsExactly(1);
    }

    /**
     * 任一已汇总页面的分页元数据变化时，应取消窗口内剩余任务且不再提交后续页面。
     */
    @Test
    void stopsSubmittingPagesAfterPagingMetadataChanges() throws Exception {
        EhrProperties properties = properties(3, 100);
        List<Integer> requestedPages =
                Collections.synchronizedList(new ArrayList<>());
        EhrEmployeeManagerImpl manager = manager(properties, pageNo -> {
            requestedPages.add(pageNo);
            return page(pageNo == 2 ? 11 : 10, 10, pageNo);
        });

        assertThatThrownBy(manager::fetchActiveEmployeeSnapshot)
                .isInstanceOfSatisfying(EhrSyncException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("EHR_PAGING_CHANGED"));
        assertThat(requestedPages).allMatch(pageNo -> pageNo <= 4);
    }

    private EhrProperties properties(int concurrency, int maxRecords) {
        EhrProperties properties = new EhrProperties();
        properties.setPageSize(1);
        properties.setPageConcurrency(concurrency);
        properties.setMaxRecords(maxRecords);
        return properties;
    }

    /**
     * 创建带有界线程池的待测客户端。
     *
     * @param properties 分页及内存保护配置
     * @param responder 测试分页响应提供器
     * @return 待测 EHR 人员客户端
     */
    private EhrEmployeeManagerImpl manager(
            EhrProperties properties, PageResponder responder) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpExecutor = Executors.newFixedThreadPool(6);
        httpServer.setExecutor(httpExecutor);
        httpServer.createContext("/employees",
                exchange -> respond(exchange, responder));
        httpServer.start();

        pageExecutor = new ThreadPoolTaskExecutor();
        pageExecutor.setCorePoolSize(properties.getPageConcurrency());
        pageExecutor.setMaxPoolSize(properties.getPageConcurrency());
        pageExecutor.setQueueCapacity(properties.getPageConcurrency());
        pageExecutor.setThreadNamePrefix("ehr-page-test-");
        pageExecutor.initialize();

        URI baseUri = URI.create("http://127.0.0.1:"
                + httpServer.getAddress().getPort() + "/employees");
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUri.toString())
                .build();
        return new EhrEmployeeManagerImpl(
                properties, restClient, JsonMapper.builder().build(),
                pageExecutor.getThreadPoolExecutor());
    }

    private void respond(HttpExchange exchange, PageResponder responder)
            throws IOException {
        try {
            int pageNo = queryInt(exchange.getRequestURI(), "pageNo");
            String response = responder.respond(pageNo);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sendFailure(exchange);
        } catch (RuntimeException exception) {
            sendFailure(exchange);
        } finally {
            exchange.close();
        }
    }

    private void sendFailure(HttpExchange exchange) throws IOException {
        byte[] body = "{\"code\":\"TEST_FAILURE\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, body.length);
        exchange.getResponseBody().write(body);
    }

    private int queryInt(URI uri, String name) {
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return Integer.parseInt(pair[1]);
            }
        }
        throw new IllegalArgumentException("Missing query parameter: " + name);
    }

    private String page(long totalRecords, int totalPages, int pageNo) {
        return """
                {
                  "code": "0",
                  "data": {
                    "totalRecords": %d,
                    "totalPages": %d,
                    "data": [{
                      "pkPsndoc": "P-%d",
                      "code": "E-%d",
                      "name": "Employee-%d"
                    }]
                  }
                }
                """.formatted(totalRecords, totalPages, pageNo, pageNo, pageNo);
    }

    @FunctionalInterface
    private interface PageResponder {
        String respond(int pageNo) throws InterruptedException;
    }
}
