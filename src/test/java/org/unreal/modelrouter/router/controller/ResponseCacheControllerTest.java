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
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
