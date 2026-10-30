package org.unreal.modelrouter.common.config;

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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * #854 / #864：同步实现的管理端点必须整体卸载到 boundedElastic。
 *
 * <p>本测试直接驱动过滤器，用「处理器里记录当前线程名」的方式判断线程模型，
 * 不依赖 WebTestClient 的服务器实现细节；另有规则化扫描用例盯住「新增控制器不会被漏掉」。</p>
 */
@DisplayName("admin: 同步管理端点卸载到 boundedElastic（#854、#864）")
class AdminBlockingOffloadFilterTest {

    private final AdminBlockingOffloadFilter filter = new AdminBlockingOffloadFilter();

    /** 记录执行链路的线程名，模拟控制器方法体所在线程。 */
    private static WebFilterChain recordingChain(final AtomicReference<String> threadName) {
        return exchange -> Mono.defer(() -> {
            threadName.set(Thread.currentThread().getName());
            return Mono.empty();
        });
    }

    private static String threadOfExecution(final AdminBlockingOffloadFilter filter,
                                            final String method,
                                            final String path) {
        final AtomicReference<String> threadName = new AtomicReference<>();
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path).build());
        filter.filter(exchange, recordingChain(threadName)).block();
        return threadName.get();
    }

    @Test
    @DisplayName("864-001 登录端点（bcrypt 校验）在 boundedElastic 上执行")
    void loginRunsOnBoundedElastic() {
        final String thread = threadOfExecution(filter, "POST", "/api/auth/jwt/login");
        assertTrue(thread != null && thread.startsWith("boundedElastic"),
                "登录端点会在处理器里做 bcrypt 校验，必须卸载，实际线程: " + thread);
    }

    @Test
    @DisplayName("864-002 监控与管理端点都在 boundedElastic 上执行")
    void adminEndpointsRunOnBoundedElastic() {
        for (final String path : List.of("/api/exceptions", "/api/call-history", "/api/security/blacklist",
                "/api/config/services", "/api/routing/stats")) {
            final String thread = threadOfExecution(filter, "GET", path);
            assertTrue(thread != null && thread.startsWith("boundedElastic"),
                    path + " 的处理器会同步查库，必须卸载，实际线程: " + thread);
        }
    }

    @Test
    @DisplayName("864-003 AI 代理面保持在调用线程（流式响应不能长期占用弹性线程）")
    void aiProxyFaceKeepsCallerThread() {
        final String thread = threadOfExecution(filter, "POST", "/api/v1/chat/completions");
        assertFalse(thread != null && thread.startsWith("boundedElastic"),
                "流式响应会长期占用线程，AI 代理面不应被整体卸载，实际线程: " + thread);
    }

    @Test
    @DisplayName("864-004 SSE 健康流与 /v1 面不被卸载")
    void sseAndNativeFaceKeepCallerThread() {
        for (final String path : List.of("/api/health-status/stream", "/v1/chat/completions", "/v1/models")) {
            final String thread = threadOfExecution(filter, "GET", path);
            assertFalse(thread != null && thread.startsWith("boundedElastic"),
                    path + " 不应被卸载，实际线程: " + thread);
        }
    }

    @Test
    @DisplayName("864-005 /actuator/metrics 卸载，/actuator/health 不卸载")
    void actuatorMetricsOffloadedButHealthNot() {
        assertTrue(filter.shouldOffload("/actuator/metrics"));
        assertTrue(filter.shouldOffload("/actuator/metrics/jvm.memory.used"));
        assertFalse(filter.shouldOffload("/actuator/health"));
        assertFalse(filter.shouldOffload("/actuator/prometheus"));
    }

    @Test
    @DisplayName("864-006 前缀匹配带分隔符边界")
    void prefixBoundaryIsRespected() {
        assertTrue(filter.shouldOffload("/api"));
        assertTrue(filter.shouldOffload("/api/exceptions"));
        assertFalse(filter.shouldOffload("/apix"));
        assertFalse(filter.shouldOffload("/api-v1/chat"));
        assertFalse(filter.shouldOffload("/api/v1"));
        assertFalse(filter.shouldOffload("/api/health-status"));
        assertFalse(filter.shouldOffload("/api/health-status/stream"));
        assertFalse(filter.shouldOffload(""));
        assertFalse(filter.shouldOffload(null));
    }

    @Test
    @DisplayName("864-007 规则化扫描：/api 下所有控制器都被规则覆盖（新增控制器无需登记）")
    void everyAdminControllerIsCoveredByRule() throws IOException {
        final Path root = Paths.get("src/main/java/org/unreal/modelrouter");
        assertTrue(Files.isDirectory(root), "源码根目录不存在: " + root);

        final Pattern mapping = Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
        final List<String> scanned = new ArrayList<>();
        final List<String> failures = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            for (final Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!source.contains("@RestController")) {
                    continue;
                }
                final Matcher matcher = mapping.matcher(source);
                if (!matcher.find()) {
                    continue;
                }
                final String prefix = matcher.group(1).replaceAll("/+$", "");
                if (!prefix.startsWith("/api")) {
                    continue;
                }
                scanned.add(prefix);
                final boolean selfScheduled = AdminBlockingOffloadFilter.SELF_SCHEDULED_PREFIXES.stream()
                        .anyMatch(p -> AdminBlockingOffloadFilter.matches(prefix, p));
                final boolean expectOffload = !prefix.startsWith(AdminBlockingOffloadFilter.AI_PROXY_PREFIX)
                        && !selfScheduled;
                if (filter.shouldOffload(prefix + "/probe") != expectOffload) {
                    failures.add(prefix + "（期望卸载=" + expectOffload + "，实际="
                            + filter.shouldOffload(prefix + "/probe") + "）: " + file);
                }
            }
        }

        assertTrue(scanned.size() >= 20,
                "必须扫到足量的 /api 控制器，否则说明扫描逻辑失效，实际扫到: " + scanned);
        assertTrue(failures.isEmpty(),
                "这些控制器与卸载规则不一致（默认线程模型会与预期相反）: " + failures);
        assertTrue(filter.shouldOffload("/api/auth/jwt/login"), "至少登录端点必须命中卸载规则");
        assertEquals(true, filter.shouldOffload("/api/models"));
    }
}
