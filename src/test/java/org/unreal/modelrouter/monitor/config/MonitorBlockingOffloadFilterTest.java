package org.unreal.modelrouter.monitor.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * #854：监控管理端点的同步处理器必须整体卸载到 boundedElastic。
 *
 * <p>本测试直接驱动过滤器，用「处理器里记录当前线程名」的方式判断线程模型，
 * 不依赖 WebTestClient 的服务器实现细节。</p>
 */
@DisplayName("monitor: 同步管理端点卸载到 boundedElastic（#854）")
class MonitorBlockingOffloadFilterTest {

    private final MonitorBlockingOffloadFilter filter = new MonitorBlockingOffloadFilter();

    /** 记录执行链路的线程名，模拟控制器方法体所在线程。 */
    private static WebFilterChain recordingChain(final AtomicReference<String> threadName) {
        return exchange -> Mono.defer(() -> {
            threadName.set(Thread.currentThread().getName());
            return Mono.empty();
        });
    }

    @Test
    @DisplayName("854-001 白名单路径在 boundedElastic 上执行")
    void whitelistedPathRunsOnBoundedElastic() {
        final AtomicReference<String> threadName = new AtomicReference<>();
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/exceptions").build());

        filter.filter(exchange, recordingChain(threadName)).block();

        assertTrue(threadName.get() != null && threadName.get().startsWith("boundedElastic"),
                "管理端点的处理器必须在 boundedElastic 上跑，实际线程: " + threadName.get());
    }

    @Test
    @DisplayName("854-002 业务路径仍在调用线程上执行（不整体改写线程模型）")
    void businessPathKeepsCallerThread() {
        final AtomicReference<String> threadName = new AtomicReference<>();
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());

        filter.filter(exchange, recordingChain(threadName)).block();

        final String thread = threadName.get();
        assertFalse(thread != null && thread.startsWith("boundedElastic"),
                "非白名单路径不应被卸载，实际线程: " + thread);
    }

    @Test
    @DisplayName("854-003 前缀带分隔符边界，相近路径不会被误命中")
    void prefixBoundaryIsRespected() {
        assertTrue(filter.matchesOffloadPrefix("/api/exceptions"));
        assertTrue(filter.matchesOffloadPrefix("/api/exceptions/cleanup"));
        assertFalse(filter.matchesOffloadPrefix("/api/exceptions-extra"),
                "/api/exceptions-extra 不是 /api/exceptions 的子路径，不能被卸载");
        assertFalse(filter.matchesOffloadPrefix("/api/monitoring/quota-extra"));
        assertFalse(filter.matchesOffloadPrefix("/api/monitoring"));
    }

    @Test
    @DisplayName("854-004 清单覆盖全部同步监控控制器（防新增控制器漏登记）")
    void everySyncMonitorControllerIsCovered() throws IOException {
        final List<String> controllerFiles = List.of(
                "src/main/java/org/unreal/modelrouter/monitor/callhistory/ApiCallHistoryController.java",
                "src/main/java/org/unreal/modelrouter/monitor/callhistory/controller/CallHistoryConfigController.java",
                "src/main/java/org/unreal/modelrouter/monitor/controller/ExceptionManagementController.java",
                "src/main/java/org/unreal/modelrouter/monitor/controller/QuotaMonitoringController.java",
                "src/main/java/org/unreal/modelrouter/monitor/controller/TracingSecurityController.java",
                "src/main/java/org/unreal/modelrouter/monitor/monitoring/collector/MetricsManagementController.java",
                "src/main/java/org/unreal/modelrouter/monitor/monitoring/registry/MetricRegistrationController.java");

        final Pattern mapping = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
        final List<String> missing = new ArrayList<>();
        int scanned = 0;
        for (final String file : controllerFiles) {
            final Path path = Paths.get(file);
            assertTrue(Files.exists(path), "控制器文件不存在（路径变更后请同步本用例）: " + file);
            final String source = Files.readString(path, StandardCharsets.UTF_8);
            final Matcher matcher = mapping.matcher(source);
            assertTrue(matcher.find(), "没找到 @RequestMapping 前缀: " + file);
            final String prefix = matcher.group(1);
            scanned++;
            if (!MonitorBlockingOffloadFilter.OFFLOAD_PREFIXES.contains(prefix)) {
                missing.add(prefix + " (" + file + ")");
            }
        }

        assertEquals(controllerFiles.size(), scanned, "必须扫完所有登记文件，否则用例空转");
        assertTrue(missing.isEmpty(),
                "这些同步监控控制器没有登记到卸载清单，仍会阻塞事件循环: " + missing);
    }
}
