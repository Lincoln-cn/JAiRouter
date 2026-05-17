package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AdapterController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/config/adapter - 获取所有适配器
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("AdapterController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class AdapterControllerTest {

    @Mock
    private AdapterRegistry adapterRegistry;

    @InjectMocks
    private AdapterController controller;

    // ==================== 获取适配器列表测试 ====================

    @Nested
    @DisplayName("GET /api/config/adapter - 获取适配器列表测试")
    class GetAdaptersTests {

        @Test
        @DisplayName("ADAPTER-001: 成功获取所有适配器")
        void testGetAdapters_success() {
            // Given
            Map<String, ServiceCapability> adapters = new HashMap<>();
            ServiceCapability mockAdapter = mock(ServiceCapability.class);
            adapters.put("normal", mockAdapter);
            adapters.put("gpustack", mockAdapter);
            when(adapterRegistry.getAllAdapters()).thenReturn(adapters);

            // When
            ResponseEntity<RouterResponse<List<Object>>> result = controller.getAdapters();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals(2, result.getBody().getData().size());
            assertEquals("获取适配器列表成功", result.getBody().getMessage());
        }

        @Test
        @DisplayName("ADAPTER-002: 空适配器列表")
        void testGetAdapters_emptyList() {
            // Given
            when(adapterRegistry.getAllAdapters()).thenReturn(new HashMap<>());

            // When
            ResponseEntity<RouterResponse<List<Object>>> result = controller.getAdapters();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
            assertEquals(0, result.getBody().getData().size());
        }

        @Test
        @DisplayName("ADAPTER-003: 异常处理 - 返回500错误")
        void testGetAdapters_exception() {
            // Given
            when(adapterRegistry.getAllAdapters()).thenThrow(new RuntimeException("Database error"));

            // When
            ResponseEntity<RouterResponse<List<Object>>> result = controller.getAdapters();

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("获取适配器列表失败"));
        }
    }
}
