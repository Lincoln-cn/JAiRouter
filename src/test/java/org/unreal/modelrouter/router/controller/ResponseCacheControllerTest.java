package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.controller.ResponseCacheController.RuntimeConfigUpdateRequest;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v2.9.10: ResponseCacheController 单元测试.
 *
 * <p>覆盖：全清 / 按 serviceType / 按 serviceType+model / 无效 serviceType / 缓存禁用。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResponseCacheController 测试")
class ResponseCacheControllerTest {

    @Mock
    private ResponseCacheService responseCacheService;

    private ResponseCacheController controller;

    @BeforeEach
    void setUp() {
        controller = new ResponseCacheController(responseCacheService);
    }

    @Test
    @DisplayName("无参数 → 全清 invalidateAll")
    void noParamsClearsAll() {
        when(responseCacheService.invalidateAll()).thenReturn(true);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue((Boolean) response.getBody().getData().get("executed"));
        assertNull(response.getBody().getData().get("serviceType"));
        assertNull(response.getBody().getData().get("model"));
        verify(responseCacheService).invalidateAll();
    }

    @Test
    @DisplayName("仅 serviceType → 按服务类型失效")
    void serviceTypeOnlyInvalidatesByService() {
        when(responseCacheService.invalidate(ServiceType.chat, null)).thenReturn(true);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache("chat", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue((Boolean) response.getBody().getData().get("executed"));
        assertEquals("chat", response.getBody().getData().get("serviceType"));
        assertNull(response.getBody().getData().get("model"));
        verify(responseCacheService).invalidate(ServiceType.chat, null);
    }

    @Test
    @DisplayName("serviceType + model → 精确失效")
    void serviceTypeAndModelInvalidatesByPrefix() {
        when(responseCacheService.invalidate(ServiceType.chat, "gpt-4")).thenReturn(true);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache("chat", "gpt-4");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue((Boolean) response.getBody().getData().get("executed"));
        assertEquals("chat", response.getBody().getData().get("serviceType"));
        assertEquals("gpt-4", response.getBody().getData().get("model"));
        verify(responseCacheService).invalidate(ServiceType.chat, "gpt-4");
    }

    @Test
    @DisplayName("无效 serviceType → IllegalArgumentException")
    void invalidServiceTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.invalidateCache("invalid-type", null));
    }

    @Test
    @DisplayName("serviceType 大小写不敏感")
    void serviceTypeIsCaseInsensitive() {
        when(responseCacheService.invalidate(ServiceType.embedding, null)).thenReturn(true);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache("EMBEDDING", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("embedding", response.getBody().getData().get("serviceType"));
        verify(responseCacheService).invalidate(ServiceType.embedding, null);
    }

    @Test
    @DisplayName("缓存禁用 → 返回 executed=false")
    void cacheDisabledReturnsNotExecuted() {
        when(responseCacheService.invalidateAll()).thenReturn(false);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse((Boolean) response.getBody().getData().get("executed"));
        assertEquals("缓存未启用，操作未执行", response.getBody().getMessage());
    }

    @Test
    @DisplayName("仅 model 参数（无 serviceType）→ 全清（model 依赖 serviceType）")
    void modelOnlyWithoutServiceTypeClearsAll() {
        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.invalidateCache(null, "gpt-4");

        // model 参数不带 serviceType 时，serviceType 仍为 null → 走全清
        // 注意：resolvedType=null 且 resolvedModel 不为 null 时走 invalidate(null, model)
        // 但 invalidate 中 serviceType=null 会清空全部
        verify(responseCacheService).invalidate(null, "gpt-4");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /response → 返回缓存配置与状态（含 hits/misses/hitRatio）")
    void getCacheStatusReturnsConfigAndSize() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", true);
        snapshot.put("ttlSeconds", 7200L);
        snapshot.put("maxSize", 5000);
        snapshot.put("size", 42L);
        snapshot.put("skipStreaming", true);
        snapshot.put("onlyDeterministic", false);
        snapshot.put("hits", 80L);
        snapshot.put("misses", 20L);
        snapshot.put("hitRatio", 0.8);
        when(responseCacheService.snapshot()).thenReturn(snapshot);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.getCacheStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        Map<String, Object> data = response.getBody().getData();
        assertEquals(true, data.get("enabled"));
        assertEquals(7200L, data.get("ttlSeconds"));
        assertEquals(5000, data.get("maxSize"));
        assertEquals(42L, data.get("size"));
        assertEquals(true, data.get("skipStreaming"));
        assertEquals(false, data.get("onlyDeterministic"));
        assertEquals(80L, data.get("hits"));
        assertEquals(20L, data.get("misses"));
        assertEquals(0.8, data.get("hitRatio"));
    }

    @Test
    @DisplayName("GET /response 缓存禁用 → size=0, hitRatio=null（无请求数据）")
    void getCacheStatusWhenDisabledReturnsZeroSize() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", false);
        snapshot.put("ttlSeconds", 3600L);
        snapshot.put("maxSize", 10000);
        snapshot.put("size", 0L);
        snapshot.put("skipStreaming", true);
        snapshot.put("onlyDeterministic", true);
        snapshot.put("hits", 0L);
        snapshot.put("misses", 0L);
        snapshot.put("hitRatio", null);
        when(responseCacheService.snapshot()).thenReturn(snapshot);

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.getCacheStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        Map<String, Object> data = response.getBody().getData();
        assertEquals(false, data.get("enabled"));
        assertEquals(0L, data.get("size"));
        assertNull(data.get("hitRatio"), "无请求数据时 hitRatio 应为 null");
    }

    // ==================== PUT /response/config 测试 ====================

    @Test
    @DisplayName("PUT /response/config 部分更新 enabled → 返回更新后快照")
    void putPartialUpdateEnabledReturnsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", false);
        snapshot.put("ttlSeconds", 300L);
        snapshot.put("maxSize", 10000);
        snapshot.put("size", 0L);
        snapshot.put("skipStreaming", true);
        snapshot.put("onlyDeterministic", true);
        snapshot.put("hits", 0L);
        snapshot.put("misses", 0L);
        snapshot.put("hitRatio", null);
        when(responseCacheService.updateRuntimeConfig(
                eq(false), eq(null), eq(null), eq(null)))
                .thenReturn(snapshot);

        RuntimeConfigUpdateRequest request = new RuntimeConfigUpdateRequest();
        request.enabled = false;

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateRuntimeConfig(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("缓存配置已更新", response.getBody().getMessage());
        assertEquals(false, response.getBody().getData().get("enabled"));
        verify(responseCacheService).updateRuntimeConfig(false, null, null, null);
    }

    @Test
    @DisplayName("PUT /response/config 非法 ttlSeconds → 400")
    void putInvalidTtlReturnsBadRequest() {
        when(responseCacheService.updateRuntimeConfig(
                any(), any(), any(), eq(0L)))
                .thenThrow(new IllegalArgumentException(
                        "ttlSeconds 超出范围 [1, 604800]: 0"));

        RuntimeConfigUpdateRequest request = new RuntimeConfigUpdateRequest();
        request.ttlSeconds = 0L;

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateRuntimeConfig(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
        assertTrue(response.getBody().getMessage().contains("ttlSeconds"));
    }

    @Test
    @DisplayName("PUT /response/config 全 null 请求体 → 400")
    void putAllNullReturnsBadRequest() {
        when(responseCacheService.updateRuntimeConfig(
                eq(null), eq(null), eq(null), eq(null)))
                .thenThrow(new IllegalArgumentException("至少需要指定一个配置参数"));

        RuntimeConfigUpdateRequest request = new RuntimeConfigUpdateRequest();

        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateRuntimeConfig(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
        assertTrue(response.getBody().getMessage().contains("至少需要指定一个配置参数"));
    }

    @Test
    @DisplayName("PUT /response/config null 请求体 → 400")
    void putNullBodyReturnsBadRequest() {
        ResponseEntity<RouterResponse<Map<String, Object>>> response =
                controller.updateRuntimeConfig(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
    }
}
