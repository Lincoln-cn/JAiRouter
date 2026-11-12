package org.unreal.modelrouter.config.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitService;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitCounter;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitProperties.MissingIdentity;
import org.unreal.modelrouter.router.ratelimit.enduser.LocalEndUserRateLimitCounter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RatelimitConfigController} 测试.
 *
 * <p>直接调用控制器方法，用真实配置对象 + 可控的 {@link ObjectProvider} 覆盖：
 * 默认快照、热改生效、需重启字段被拒、非法取值被拒。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@DisplayName("RatelimitConfigController 限流运行时配置测试")
class RatelimitConfigControllerTest {

    private static final ObjectMapper MAPPER = JacksonHelper.getObjectMapper();

    private ApiKeyMinuteRateLimitProperties apiKeyProperties;
    private EndUserRateLimitProperties endUserProperties;
    private RatelimitConfigController controller;

    @BeforeEach
    void setUp() {
        apiKeyProperties = new ApiKeyMinuteRateLimitProperties();
        endUserProperties = new EndUserRateLimitProperties();
        controller = newController(null, null, false, 50L);
    }

    @Test
    @DisplayName("GET - 返回默认配置快照（L1 默认开、L2 默认关）")
    void getDefaultSnapshot() throws Exception {
        final Map<String, Object> body = getOk();

        final Map<String, Object> apiKey = nested(body, "apiKey");
        assertEquals(true, apiKey.get("enabled"), "L1 默认开启：旧的两条判定已删除，关闭即等于不限流");
        assertEquals(50000, ((Number) apiKey.get("localCacheSize")).intValue());

        final Map<String, Object> endUser = nested(body, "endUser");
        assertEquals(false, endUser.get("enabled"), "L2 默认关闭：保持零行为变更");
        assertEquals(60, ((Number) endUser.get("requestsPerMinute")).intValue());
        assertEquals(true, endUser.get("headerEnabled"));
        assertEquals("SKIP", endUser.get("missingIdentity"));
        assertEquals(16, ((Number) endUser.get("fingerprintLength")).intValue());

        final Map<String, Object> distributed = nested(body, "distributed");
        assertEquals(false, distributed.get("enabled"));
        assertEquals(50, ((Number) distributed.get("timeoutMs")).intValue());
        assertEquals("unknown", distributed.get("apiKeyBackend"));
        assertEquals("unknown", distributed.get("endUserBackend"));

        assertNotNull(body.get("hotEditableFields"));
        assertNotNull(body.get("restartRequiredFields"));
    }

    @Test
    @DisplayName("GET - 快照如实报告两层的计数后端")
    void getSnapshot_reportsBackends() throws Exception {
        final ApiKeyMinuteRateLimitService apiKeyService = mock(ApiKeyMinuteRateLimitService.class);
        when(apiKeyService.backendName()).thenReturn("redis");
        controller = newController(apiKeyService, new LocalEndUserRateLimitCounter(64), false, 50L);

        final Map<String, Object> distributed = nested(getOk(), "distributed");
        assertEquals("redis", distributed.get("apiKeyBackend"));
        assertEquals("local", distributed.get("endUserBackend"));
    }

    @Test
    @DisplayName("PUT - 热改 L1 开关生效并反映到 GET")
    void putHotEditable_apiKeyEnabled() throws Exception {
        assertEquals(true, nested(getOk(), "apiKey").get("enabled"));

        final Map<String, Object> body = putOk("{\"apiKeyEnabled\": false}");

        assertEquals(false, nested(body, "apiKey").get("enabled"));
        assertFalse(apiKeyProperties.isEnabled());
        assertEquals(false, nested(getOk(), "apiKey").get("enabled"), "热改必须对后续查询可见");
    }

    @Test
    @DisplayName("PUT - 热改 L2 开关与阈值，无需重启")
    void putHotEditable_endUserSwitchAndThreshold() throws Exception {
        final Map<String, Object> body = putOk(
                "{\"endUserEnabled\": true, \"endUserRequestsPerMinute\": 30}");

        final Map<String, Object> endUser = nested(body, "endUser");
        assertEquals(true, endUser.get("enabled"));
        assertEquals(30, ((Number) endUser.get("requestsPerMinute")).intValue());
        assertTrue(endUserProperties.isEnabled());
        assertEquals(30, endUserProperties.getRequestsPerMinute());
    }

    @Test
    @DisplayName("PUT - 标识缺失处理方式大小写不敏感")
    void putHotEditable_missingIdentityCaseInsensitive() throws Exception {
        final Map<String, Object> body = putOk("{\"endUserMissingIdentity\": \"reject\"}");

        assertEquals("REJECT", nested(body, "endUser").get("missingIdentity"));
        assertEquals(MissingIdentity.REJECT, endUserProperties.getMissingIdentity());
    }

    @Test
    @DisplayName("PUT - 携带需重启字段时明确拒绝，且不产生任何改动")
    void putRestartRequiredField_rejected() throws Exception {
        endUserProperties.setEnabled(false);

        final Map<String, Object> body = putBadRequest(
                "{\"endUserEnabled\": true, \"distributedEnabled\": true}");

        assertEquals(ErrorCode.RESTART_REQUIRED.code(), body.get("errorCode"));
        assertFalse(endUserProperties.isEnabled(),
                "同一个请求里混入需重启字段时整条请求都应被拒，不得部分生效");
    }

    @Test
    @DisplayName("PUT - 全 null 请求视为非法")
    void putAllNull_rejected() throws Exception {
        final Map<String, Object> body = putBadRequest("{}");

        assertEquals(ErrorCode.INVALID_REQUEST.code(), body.get("errorCode"));
    }

    @Test
    @DisplayName("PUT - 非法的缺失处理方式被拒")
    void putInvalidMissingIdentity_rejected() throws Exception {
        final Map<String, Object> body = putBadRequest("{\"endUserMissingIdentity\": \"IGNORE\"}");

        assertEquals(ErrorCode.INVALID_REQUEST.code(), body.get("errorCode"));
        assertEquals(MissingIdentity.SKIP, endUserProperties.getMissingIdentity());
    }

    @Test
    @DisplayName("PUT - 阈值必须大于 0")
    void putZeroThreshold_rejected() throws Exception {
        final Map<String, Object> body = putBadRequest("{\"endUserRequestsPerMinute\": 0}");

        assertEquals(ErrorCode.INVALID_REQUEST.code(), body.get("errorCode"));
    }

    /**
     * 构造控制器（可控的 ObjectProvider）.
     */
    private RatelimitConfigController newController(
            final ApiKeyMinuteRateLimitService apiKeyService,
            final EndUserRateLimitCounter endUserCounter,
            final boolean distributedEnabled,
            final long timeoutMs) {
        return new RatelimitConfigController(apiKeyProperties, endUserProperties,
                providerOf(apiKeyService), providerOf(endUserCounter),
                distributedEnabled, timeoutMs);
    }

    /**
     * 构造只返回固定实例的 {@link ObjectProvider}.
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(final T instance) {
        final ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    /**
     * 取出嵌套的 map 字段.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(final Map<String, Object> body, final String key) {
        final Object value = body.get(key);
        assertNotNull(value, "快照缺少字段: " + key);
        return (Map<String, Object>) value;
    }

    /**
     * 调用 GET 并返回 data.
     */
    private Map<String, Object> getOk() {
        final ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getConfig();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        return response.getBody().getData();
    }

    /**
     * 调用 PUT 并返回 data.
     */
    private Map<String, Object> putOk(final String bodyJson) throws Exception {
        final RatelimitConfigController.RatelimitConfigUpdateRequest request =
                MAPPER.readValue(bodyJson, RatelimitConfigController.RatelimitConfigUpdateRequest.class);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateConfig(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        return response.getBody().getData();
    }

    /**
     * 调用 PUT 并解析 400 错误响应体.
     */
    private Map<String, Object> putBadRequest(final String bodyJson) throws Exception {
        final RatelimitConfigController.RatelimitConfigUpdateRequest request =
                MAPPER.readValue(bodyJson, RatelimitConfigController.RatelimitConfigUpdateRequest.class);

        final ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateConfig(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        final String rawJson = MAPPER.writeValueAsString(response.getBody());
        return MAPPER.readValue(rawJson, new TypeReference<Map<String, Object>>() { });
    }
}
