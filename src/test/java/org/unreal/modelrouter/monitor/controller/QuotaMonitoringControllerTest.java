package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.quota.QuotaCounterMetrics;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaRequest;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                controller.getUsage(null, null, null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().getData().isEmpty());
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 预置计数后按维度查询")
    void usageWithPresetCounters() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("test-key");
        ledgerService.reserve(QuotaRequest.of(dim, 100));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                controller.getUsage(null, "test-key", null, null, null, null);

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
                controller.getUsage(null, "key-w", null, null, null, "MINUTE");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertEquals(1, data.size(), "指定单个窗口时只返回该窗口");
        assertEquals("MINUTE", data.get(0).get("window"));
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 无效窗口类型返回 400")
    void usageInvalidWindow() {
        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                controller.getUsage(null, null, null, null, null, "INVALID");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("INVALID_REQUEST", resp.getBody().getErrorCode());
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 返回结构包含 dimensions 嵌套对象")
    void usageContainsNestedDimensions() {
        final QuotaDimension dim = new QuotaDimension("t1", "k1", "u1", "chat", "gpt-4");
        ledgerService.reserve(QuotaRequest.of(dim, 200));

        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> resp =
                controller.getUsage("t1", "k1", "u1", "chat", "gpt-4", "DAY");

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
                controller.getUsage(null, "multi-key", null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final List<Map<String, Object>> data = resp.getBody().getData();
        assertEquals(4, data.size(), "默认四级窗口都应有数据");
    }

    @Test
    @DisplayName("GET /api/monitoring/quota/usage - 只读：多次调用计数不增长")
    void usageReadOnlyNoSideEffect() {
        final QuotaDimension dim = QuotaDimension.ofApiKey("readonly-key");
        ledgerService.reserve(QuotaRequest.of(dim, 10));

        controller.getUsage(null, "readonly-key", null, null, null, null);
        controller.getUsage(null, "readonly-key", null, null, null, null);
        final ResponseEntity<RouterResponse<List<Map<String, Object>>>> third =
                controller.getUsage(null, "readonly-key", null, null, null, "DAY");

        assertEquals(1, third.getBody().getData().size());
        assertEquals(1L, ((Number) third.getBody().getData().get(0).get("requestCount")).longValue(),
                "多次查询不应增加请求数");
    }

    // ==================== 辅助 ====================

    /**
     * 简单内存仓库代理（测试用）.
     */
    private static final class InMemoryRepo implements InvocationHandler {

        private final Map<String, QuotaLedgerEntity> rows = new LinkedHashMap<>();
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

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
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
