package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.quota.FakeReactiveRedisTemplate;
import org.unreal.modelrouter.auth.security.quota.InMemoryQuotaLedgerRepository;
import org.unreal.modelrouter.auth.security.quota.QuotaCounterMetrics;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaRequest;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.auth.security.quota.RedisCounterBackend;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaMonitoringController} 测试.
 *
 * <p>使用真实的 {@link QuotaLedgerService}（内存仓库 + 本地后端），
 * 验证 status 与 usage 端点的返回形状与语义。</p>
 *
 * <p>usage 端点返回 {@code Mono}（issue #105：把阻塞读数移出事件循环），测试统一通过
 * {@link #requestUsage} 订阅并取回响应，因此断言仍与同步实现的载荷一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaMonitoringController 配额观测面测试")
class QuotaMonitoringControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private QuotaProperties quotaProperties;
    private QuotaLedgerService ledgerService;
    private QuotaMonitoringController controller;
    private InMemoryRepo repo;

    @BeforeEach
    void setUp() {
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);
        repo = InMemoryRepo.create();
        ledgerService = new QuotaLedgerService(
                repo.proxy(), quotaProperties,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        controller = new QuotaMonitoringController(ledgerService, quotaProperties,
                QuotaCounterMetrics.noop());
    }

    // ==================== status 端点 ====================

    @Test
    @DisplayName("GET /api/monitoring/quota/status - 返回正确的状态形状（local 后端）")
    void statusShapeWithLocalBackend() {
        final ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getStatus();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());

        final Map<String, Object> data = resp.getBody().getData();
        assertNotNull(data);
        assertEquals(true, data.get("enabled"));
        assertEquals("local", data.get("backendName"));
        assertEquals(false, data.get("degraded"));
        assertEquals("", data.get("degradedReason"));
        assertEquals(true, data.get("failOpen"));
        assertNotNull(data.get("windows"));

        @SuppressWarnings("unchecked")
        final List<String> windows = (List<String>) data.get("windows");
        assertTrue(windows.contains("MINUTE"));
        assertTrue(windows.contains("HOUR"));
        assertTrue(windows.contains("DAY"));
        assertTrue(windows.contains("MONTH"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> distributed = (Map<String, Object>) data.get("distributed");
        assertNotNull(distributed);
        assertEquals(false, distributed.get("enabled"));
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/status - enabled=false 时仍返回状态")
    void statusWhenDisabled() {
        quotaProperties.setEnabled(false);

        final ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getStatus();
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        final Map<String, Object> data = resp.getBody().getData();
        assertEquals(false, data.get("enabled"));
        assertEquals("local", data.get("backendName"));
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/status - Redis 模式下包含 redisProbe 和 counterMetrics")
    void statusWithDistributedEnabledProbeField() {
        quotaProperties.getDistributed().setEnabled(true);

        final ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getStatus();
        final Map<String, Object> data = resp.getBody().getData();

        assertNotNull(data.get("redisProbe"),
                "distributed.enabled=true 时应包含 redisProbe");
        assertNotNull(data.get("counterMetrics"),
                "distributed.enabled=true 时应包含 counterMetrics");
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/status - distributed.enabled=false 时无 redisProbe")
    void statusWithoutDistributedNoProbeField() {
        quotaProperties.getDistributed().setEnabled(false);

        final ResponseEntity<RouterResponse<Map<String, Object>>> resp = controller.getStatus();
        final Map<String, Object> data = resp.getBody().getData();

        assertFalse(data.containsKey("redisProbe"),
                "distributed.enabled=false 时不应包含 redisProbe");
    }

    // ==================== usage 端点 ====================

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - enabled=false 时返回空列表")
    void usageWhenDisabled() {
        quotaProperties.setEnabled(false);

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, null, null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().getData().isEmpty());
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 预置计数后按维度查询")
    void usageWithPresetCounters() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("test-key");
        ledgerService.reserve(QuotaRequest.of(dim, 100));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, "test-key", null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertFalse(data.isEmpty(), "预置计数后应有数据");

        final Map<String, Object> first = data.get(0);
        assertNotNull(first.get("dimensions"));
        assertNotNull(first.get("window"));
        assertNotNull(first.get("windowStart"));
        assertNotNull(first.get("requestCount"));
        assertNotNull(first.get("tokenCount"));

        assertEquals(1L, ((Number) first.get("requestCount")).longValue());
        assertEquals(100L, ((Number) first.get("tokenCount")).longValue());
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 按窗口筛选（单个窗口）")
    void usageFilterByWindow() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("key-w");
        ledgerService.reserve(QuotaRequest.of(dim, 50));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, "key-w", null, null, null, "MINUTE");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertEquals(1, data.size(), "指定单个窗口时只返回该窗口");
        assertEquals("MINUTE", data.get(0).get("window"));
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 无效窗口类型返回 400")
    void usageInvalidWindow() {
        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, null, null, null, null, "INVALID");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("INVALID_REQUEST", resp.getBody().getErrorCode());
        assertEquals("无效的窗口类型: INVALID（有效值: MINUTE, HOUR, DAY, MONTH）",
                resp.getBody().getMessage(), "错误响应正文必须与同步实现完全一致");
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 返回结构包含 dimensions 嵌套对象")
    void usageContainsNestedDimensions() {
        final QuotaDimension dim = new QuotaDimension("t1", "k1", "u1", "chat", "gpt-4");
        ledgerService.reserve(QuotaRequest.of(dim, 200));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage("t1", "k1", "u1", "chat", "gpt-4", "DAY");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertFalse(data.isEmpty());

        @SuppressWarnings("unchecked")
        final Map<String, String> dimensions = (Map<String, String>) data.get(0).get("dimensions");
        assertNotNull(dimensions);
        assertEquals("t1", dimensions.get("tenantId"));
        assertEquals("k1", dimensions.get("apiKeyId"));
        assertEquals("u1", dimensions.get("userId"));
        assertEquals("chat", dimensions.get("serviceType"));
        assertEquals("gpt-4", dimensions.get("model"));
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 多窗口全部返回")
    void usageAllWindowsReturned() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("multi-key");
        ledgerService.reserve(QuotaRequest.of(dim, 30));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, "multi-key", null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertEquals(4, data.size(), "默认四级窗口都应有数据");
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 只读：多次调用计数不增长")
    void usageReadOnlyNoSideEffect() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("readonly-key");
        ledgerService.reserve(QuotaRequest.of(dim, 10));

        requestUsage(null, "readonly-key", null, null, null, null);
        requestUsage(null, "readonly-key", null, null, null, null);
        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> third =
                requestUsage(null, "readonly-key", null, null, null, "DAY");

        assertEquals(1, third.getBody().getData().size());
        assertEquals(1L, ((Number) third.getBody().getData().get(0).get("requestCount")).longValue(),
                "多次查询不应增加请求数");
    }

    // ==================== issue #105：阻塞读数不得落在事件循环上 ====================

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 从非阻塞线程发起：账本阻塞读不得在事件循环上执行")
    void usageNeverReadsLedgerOnNonBlockingThread() throws Exception {
        // 该维度没有本地槽位 → usage() 会回退数据库快照，仓库被调用的线程即“账本阻塞读”的执行线程
        final AtomicReference<Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>>> pending =
                new AtomicReference<>();
        final CountDownLatch invoked = new CountDownLatch(1);
        Schedulers.parallel().schedule(() -> {
            pending.set(controller.getUsage("t1", "no-slot", "u1", "chat", "gpt-4", "DAY"));
            invoked.countDown();
        });
        assertTrue(invoked.await(5, TimeUnit.SECONDS), "非阻塞线程上的调用必须立即返回（不得同步阻塞）");

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                pending.get().block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(repo.invocationCount() > 0, "无本地槽位时应回退数据库快照（确保用例真的触发了阻塞读）");
        assertEquals(0, repo.nonBlockingThreadInvocations(),
                "账本阻塞读不得在 Reactor 非阻塞线程（事件循环）上执行");
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 改为 Mono 后载荷不变（Redis 权威读数）")
    void usagePayloadUnchangedAfterReactiveConversion() throws Exception {
        final QuotaProperties distributedProperties = new QuotaProperties();
        distributedProperties.setEnabled(true);
        distributedProperties.getDistributed().setEnabled(true);
        final FakeReactiveRedisTemplate template = new FakeReactiveRedisTemplate();
        final InMemoryQuotaLedgerRepository ledgerRepository = InMemoryQuotaLedgerRepository.create();
        final QuotaLedgerService distributedLedger = new QuotaLedgerService(
                ledgerRepository.proxy(), distributedProperties,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE),
                new RedisCounterBackend(template, distributedProperties), QuotaCounterMetrics.noop());
        distributedLedger.reserve(QuotaRequest.of(QuotaDimension.ofApiKey("redis-key"), 100));
        final QuotaMonitoringController distributedController =
                new QuotaMonitoringController(distributedLedger, distributedProperties,
                        QuotaCounterMetrics.noop());

        final AtomicReference<Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>>> pending =
                new AtomicReference<>();
        final CountDownLatch invoked = new CountDownLatch(1);
        Schedulers.parallel().schedule(() -> {
            pending.set(distributedController.getUsage(null, "redis-key", null, null, null, null));
            invoked.countDown();
        });
        assertTrue(invoked.await(5, TimeUnit.SECONDS));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                pending.get().block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().isSuccess());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertEquals(4, data.size(), "Redis 模式默认四级窗口都应有数据（载荷与同步实现一致）");
        for (final Map<String, Object> row : data) {
            assertEquals(1L, ((Number) row.get("requestCount")).longValue(), "窗口 " + row.get("window"));
            assertEquals(100L, ((Number) row.get("tokenCount")).longValue(), "窗口 " + row.get("window"));
            assertNotNull(row.get("windowStart"));
        }
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - enabled=false 时保留空列表与“配额账本未启用”提示")
    void usageWhenDisabledKeepsMessageAndEmptyList() {
        quotaProperties.setEnabled(false);

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                requestUsage(null, null, null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().isSuccess());
        assertEquals("配额账本未启用", resp.getBody().getMessage());
        assertTrue(resp.getBody().getData().isEmpty());
    }

    // ==================== 辅助 ====================

    /**
     * 调用 usage 端点并等待响应（端点返回 {@code Mono}，负载交给 {@code boundedElastic} 执行）.
     *
     * @param tenantId    租户 ID
     * @param apiKeyId    API Key ID
     * @param userId      用户 ID
     * @param serviceType 服务类型
     * @param model       模型名称
     * @param window      窗口类型
     * @return 响应
     */
    private ResponseEntity<RouterResponse<List<Map<String, Object>>>> requestUsage(
            final String tenantId, final String apiKeyId, final String userId,
            final String serviceType, final String model, final String window) {
        return controller.getUsage(tenantId, apiKeyId, userId, serviceType, model, window)
                .block(Duration.ofSeconds(5));
    }

    /**
     * 简单内存仓库代理（测试用）.
     */
    private static final class InMemoryRepo implements InvocationHandler {

        private final Map<String, QuotaLedgerEntity> rows = new LinkedHashMap<>();
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger nonBlockingInvocations = new AtomicInteger();
        private long nextId = 1L;

        static InMemoryRepo create() {
            return new InMemoryRepo();
        }

        QuotaLedgerRepository proxy() {
            return (QuotaLedgerRepository) Proxy.newProxyInstance(
                    QuotaLedgerRepository.class.getClassLoader(),
                    new Class<?>[]{QuotaLedgerRepository.class},
                    this);
        }

        /**
         * 被调用的方法次数.
         *
         * @return 次数
         */
        int invocationCount() {
            return invocations.get();
        }

        /**
         * 在 Reactor 非阻塞线程上被调用的次数（应当恒为 0：账本读必须离开事件循环）.
         *
         * @return 次数
         */
        int nonBlockingThreadInvocations() {
            return nonBlockingInvocations.get();
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            invocations.incrementAndGet();
            if (Schedulers.isInNonBlockingThread()) {
                nonBlockingInvocations.incrementAndGet();
            }
            final String name = method.getName();
            if ("toString".equals(name)) {
                return "InMemoryRepo(monitor-test)";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("findByApiKeyId".equals(name)) {
                return new ArrayList<>();
            }
            if ("count".equals(name) || "countAll".equals(name)) {
                return (long) rows.size();
            }
            if ("save".equals(name)) {
                final QuotaLedgerEntity entity = (QuotaLedgerEntity) args[0];
                if (entity.getId() == null) {
                    entity.setId(nextId++);
                }
                return entity;
            }
            return java.util.Optional.empty();
        }
    }
}
