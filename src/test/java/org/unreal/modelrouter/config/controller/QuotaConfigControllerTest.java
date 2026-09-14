package org.unreal.modelrouter.config.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.util.JacksonHelper;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuotaConfigController} 端到端测试.
 *
 * <p>使用真实的 {@link QuotaLedgerService}（内存仓库 + 本地后端），不使用 mock，
 * 直接调用控制器方法验证行为。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaConfigController 配额运行时配置测试")
class QuotaConfigControllerTest {

    private static final ObjectMapper MAPPER = JacksonHelper.getObjectMapper();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private QuotaProperties quotaProperties;
    private QuotaLedgerService ledgerService;
    private QuotaConfigController controller;

    @BeforeEach
    void setUp() {
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(false);
        ledgerService = createLocalLedgerService(quotaProperties);
        controller = new QuotaConfigController(quotaProperties, ledgerService);
    }

    @Test
    @DisplayName("GET /api/config/quota - 返回默认配置快照")
    void getDefaultSnapshot() throws Exception {
        final Map<String, Object> body = getOk();

        assertEquals(false, body.get("enabled"));
        assertEquals(true, body.get("failOpen"));
        assertNotNull(body.get("windows"));
        assertEquals(60L, ((Number) body.get("flushIntervalSeconds")).longValue());
        assertNotNull(body.get("retention"));
        assertNotNull(body.get("distributed"));
        assertNotNull(body.get("backendName"));
        assertNotNull(body.get("hotEditableFields"));
        assertNotNull(body.get("restartRequiredFields"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 热改 enabled 成功并反映到 GET")
    void putHotEditableEnabled() throws Exception {
        quotaProperties.setEnabled(false);

        final String reqBody = "{\"enabled\": true}";
        final Map<String, Object> body = putOk(reqBody);

        assertTrue(quotaProperties.isEnabled(), "enabled 应被热改为 true");
        assertEquals(true, body.get("enabled"));
        assertEquals("local", body.get("backendName"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 热改 failOpen 成功")
    void putHotEditableFailOpen() throws Exception {
        final String reqBody = "{\"failOpen\": false}";
        putOk(reqBody);

        assertFalse(quotaProperties.isFailOpen());
    }

    @Test
    @DisplayName("PUT /api/config/quota - 热改 windows 成功")
    void putHotEditableWindows() throws Exception {
        final String reqBody = "{\"windows\": [\"MINUTE\", \"HOUR\"]}";
        putOk(reqBody);

        final List<QuotaWindow> actual = quotaProperties.enabledWindows();
        assertEquals(2, actual.size());
        assertTrue(actual.contains(QuotaWindow.MINUTE));
        assertTrue(actual.contains(QuotaWindow.HOUR));
        assertFalse(actual.contains(QuotaWindow.DAY));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 携带 flushIntervalSeconds 被拒绝（需重启）")
    void putFlushIntervalSecondsRejected() throws Exception {
        final String reqBody = "{\"flushIntervalSeconds\": 120}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertNotNull(error.get("message"));
        assertTrue(((String) error.get("message")).contains("flushIntervalSeconds"));
        assertEquals("RESTART_REQUIRED", error.get("errorCode"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 携带 distributed.enabled 被拒绝并有说明")
    void putDistributedEnabledRejected() throws Exception {
        final String reqBody = "{\"distributedEnabled\": true}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertNotNull(error.get("message"));
        assertTrue(((String) error.get("message")).contains("distributed.enabled"));
        assertEquals("RESTART_REQUIRED", error.get("errorCode"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 携带 distributed.keyPrefix 被拒绝")
    void putDistributedKeyPrefixRejected() throws Exception {
        final String reqBody = "{\"distributedKeyPrefix\": \"custom:prefix\"}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertTrue(((String) error.get("message")).contains("distributed.keyPrefix"));
        assertEquals("RESTART_REQUIRED", error.get("errorCode"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 携带 retention 被拒绝")
    void putRetentionRejected() throws Exception {
        final String reqBody = "{\"retention\": {\"minute\": \"2d\"}}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertTrue(((String) error.get("message")).contains("retention.*"));
        assertEquals("RESTART_REQUIRED", error.get("errorCode"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - 全 null 请求返回 400")
    void putAllNullRejected() throws Exception {
        final String reqBody = "{}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertTrue(((String) error.get("message")).contains("至少需要"));
        assertEquals("INVALID_REQUEST", error.get("errorCode"));
    }

    @Test
    @DisplayName("PUT /api/config/quota - flushIntervalSeconds 非 null 即被拒绝（0 也属需重启）")
    void putInvalidFlushIntervalRejected() throws Exception {
        final String reqBody = "{\"flushIntervalSeconds\": 0}";
        final Map<String, Object> error = putBadRequest(reqBody);

        assertTrue(((String) error.get("message")).contains("flushIntervalSeconds"));
        assertEquals("RESTART_REQUIRED", error.get("errorCode"));
    }

    @Test
    @DisplayName("enabled=false 时账本零副作用：usage 返回空")
    void disabledQuotaZeroSideEffect() throws Exception {
        quotaProperties.setEnabled(false);

        final Map<String, Object> body = getOk();
        assertEquals(false, body.get("enabled"));
        assertEquals("local", body.get("backendName"));
    }

    @Test
    @DisplayName("enabled 切换后 backendName 正确")
    void backendNameAfterToggle() throws Exception {
        quotaProperties.setEnabled(false);

        Map<String, Object> body = getOk();
        assertEquals("local", body.get("backendName"));

        // 启用后 backendName 仍为 local（无 Redis）
        putOk("{\"enabled\": true}");
        body = getOk();
        assertEquals("local", body.get("backendName"));
    }

    @Test
    @DisplayName("GET 快照包含 distributed 配置")
    void snapshotContainsDistributed() throws Exception {
        final Map<String, Object> body = getOk();
        @SuppressWarnings("unchecked")
        final Map<String, Object> distributed = (Map<String, Object>) body.get("distributed");
        assertNotNull(distributed);
        assertEquals(false, distributed.get("enabled"));
        assertEquals("jairouter:quota", distributed.get("keyPrefix"));
        assertNotNull(distributed.get("timeoutMs"));
        assertEquals(true, distributed.get("degradeToLocal"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 调用 GET /api/config/quota 并解析成功响应体.
     */
    private Map<String, Object> getOk() {
        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getConfig();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        return response.getBody().getData();
    }

    /**
     * 调用 PUT /api/config/quota 并解析成功响应体.
     */
    private Map<String, Object> putOk(final String bodyJson) throws Exception {
        final QuotaConfigController.QuotaConfigUpdateRequest request =
                MAPPER.readValue(bodyJson, QuotaConfigController.QuotaConfigUpdateRequest.class);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateConfig(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        return response.getBody().getData();
    }

    /**
     * 调用 PUT /api/config/quota 并解析 400 错误响应体.
     */
    private Map<String, Object> putBadRequest(final String bodyJson) throws Exception {
        final QuotaConfigController.QuotaConfigUpdateRequest request =
                MAPPER.readValue(bodyJson, QuotaConfigController.QuotaConfigUpdateRequest.class);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateConfig(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        final String rawJson = MAPPER.writeValueAsString(response.getBody());
        return MAPPER.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 创建本地模式的账本服务.
     */
    private static QuotaLedgerService createLocalLedgerService(final QuotaProperties props) {
        final InMemoryQuotaLedgerRepositoryForConfig repo = new InMemoryQuotaLedgerRepositoryForConfig();
        return new QuotaLedgerService(repo.proxy(), props, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    /**
     * 内存仓库（复用测试夹具模式）.
     */
    private static final class InMemoryQuotaLedgerRepositoryForConfig implements InvocationHandler {

        private final Map<String, QuotaLedgerEntity> rows = new LinkedHashMap<>();
        private long nextId = 1L;

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
                return "InMemoryRepo(config-test)";
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
            return Optional.empty();
        }
    }
}
