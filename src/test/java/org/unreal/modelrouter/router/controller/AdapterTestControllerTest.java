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
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.test.AdapterTestResult;
import org.unreal.modelrouter.router.adapter.test.AdapterTestService;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdapterTestController 测试 API 端点测试
 *
 * TDD 测试先行：7 个测试用例覆盖测试 API 端点
 */
@DisplayName("AdapterTestController 测试 API 端点测试")
@ExtendWith(MockitoExtension.class)
class AdapterTestControllerTest {

    @Mock
    private AdapterTestService testService;

    @Mock
    private AdapterRegistry adapterRegistry;

    @InjectMocks
    private AdapterTestController controller;

    // ==================== 测试已注册适配器 ====================

    @Nested
    @DisplayName("测试已注册适配器")
    class TestAdapterTests {

        @Test
        @DisplayName("TEST-API-001: 测试已注册适配器 PING 成功")
        void testTestAdapter_pingSuccess() {
            // Given
            when(adapterRegistry.isAdapterSupported("normal")).thenReturn(true);
            AdapterTestResult testResult = AdapterTestResult.connected(100, "成功");
            when(testService.testRegisteredAdapter("normal", "PING", null, null, null))
                    .thenReturn(Mono.just(testResult));

            Map<String, Object> request = new HashMap<>();
            request.put("testType", "PING");

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testAdapter("normal", request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData().isSuccess());
        }

        @Test
        @DisplayName("TEST-API-003: 测试不存在的适配器返回 404")
        void testTestAdapter_adapterNotFound() {
            // Given
            when(adapterRegistry.isAdapterSupported("nonexistent")).thenReturn(false);

            Map<String, Object> request = new HashMap<>();
            request.put("testType", "PING");

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testAdapter("nonexistent", request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertFalse(response.getBody().isSuccess());
            assertEquals("ADAPTER_NOT_FOUND", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("TEST-API-004: 测试服务异常返回 500")
        void testTestAdapter_serviceException() {
            // Given
            when(adapterRegistry.isAdapterSupported("normal")).thenReturn(true);
            when(testService.testRegisteredAdapter(eq("normal"), eq("PING"), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("服务异常")));

            Map<String, Object> request = new HashMap<>();
            request.put("testType", "PING");

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testAdapter("normal", request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertFalse(response.getBody().isSuccess());
        }
    }

    // ==================== 预览测试 ====================

    @Nested
    @DisplayName("预览测试")
    class TestPreviewTests {

        @Test
        @DisplayName("TEST-API-005: 预览 PING 测试成功")
        void testTestPreview_pingSuccess() {
            // Given
            AdapterTestResult testResult = AdapterTestResult.connected(100, "成功");
            when(testService.testPreview(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Mono.just(testResult));

            Map<String, Object> request = new HashMap<>();
            request.put("type", "openai-compatible");
            request.put("baseUrl", "https://api.deepseek.com");
            request.put("authHeaderName", "Authorization");
            request.put("authHeaderPrefix", "Bearer ");
            request.put("testType", "PING");

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testPreview(request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("TEST-API-006: 预览测试缺少 baseUrl 返回 400")
        void testTestPreview_missingBaseUrl() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("type", "openai-compatible");
            request.put("testType", "PING");
            // baseUrl 缺失

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testPreview(request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(response.getBody().isSuccess());
            assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("TEST-API-007: 预览测试缺少 type 返回 400")
        void testTestPreview_missingType() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("baseUrl", "https://api.deepseek.com");
            request.put("testType", "PING");
            // type 缺失

            // When
            Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> resultMono =
                    controller.testPreview(request);

            // Then
            assertNotNull(resultMono);
            ResponseEntity<RouterResponse<AdapterTestResult>> response = resultMono.block();
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(response.getBody().isSuccess());
            assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
        }
    }
}
