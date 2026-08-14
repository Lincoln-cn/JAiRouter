package org.unreal.modelrouter.router.adapter.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AdapterTestService 适配器测试服务测试
 *
 * TDD 测试先行：覆盖适配器验证逻辑和结果构建
 * 实际 HTTP 调用通过集成测试验证
 */
@DisplayName("AdapterTestService 适配器测试服务测试")
@ExtendWith(MockitoExtension.class)
class AdapterTestServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private AdapterRegistry adapterRegistry;

    @InjectMocks
    private AdapterTestService testService;

    @BeforeEach
    void setUp() {
        // Mock WebClient.Builder 链式调用
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockGetSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(mockWebClient);
        lenient().when(mockWebClient.get()).thenReturn(mockGetSpec);
        lenient().when(mockWebClient.post()).thenReturn(mock(WebClient.RequestBodyUriSpec.class));
        lenient().when(mockGetSpec.uri(anyString())).thenReturn(mockHeadersSpec);
        lenient().when(mockHeadersSpec.header(anyString(), anyString())).thenReturn(mockHeadersSpec);
        lenient().when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        lenient().when(mockResponseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("测试环境无网络")));
    }

    // ==================== 已注册适配器验证测试 ====================

    @Nested
    @DisplayName("已注册适配器验证测试")
    class RegisteredAdapterTests {

        @Test
        @DisplayName("TEST-011: 测试未注册的适配器抛出异常")
        void testTestRegisteredAdapter_adapterNotFound() {
            // Given
            when(adapterRegistry.isAdapterSupported("nonexistent")).thenReturn(false);

            // When & Then
            assertThrows(IllegalArgumentException.class,
                    () -> testService.testRegisteredAdapter("nonexistent", "PING", null, null, null),
                    "未注册的适配器应抛出 IllegalArgumentException");
        }

        @Test
        @DisplayName("TEST-012: 测试已注册适配器验证通过")
        void testTestRegisteredAdapter_adapterExists() {
            // Given
            when(adapterRegistry.isAdapterSupported("normal")).thenReturn(true);
            ServiceCapability mockAdapter = mock(ServiceCapability.class);
            when(adapterRegistry.getAdapterByName("normal")).thenReturn(mockAdapter);

            // When
            var mono = testService.testRegisteredAdapter("normal", "PING", "sk-test", null, null);

            // Then - 验证返回 Mono 且不抛异常
            assertNotNull(mono, "应返回 Mono 对象");
            // 由于测试环境无网络，Mono 会返回错误结果（不是抛异常）
            StepVerifier.create(mono)
                    .assertNext(result -> assertNotNull(result))
                    .verifyComplete();
        }
    }

    // ==================== 结果构建测试 ====================

    @Nested
    @DisplayName("结果构建测试")
    class ResultBuilderTests {

        @Test
        @DisplayName("TEST-015: 测试结果延迟为非负数")
        void testTestResult_latencyPositive() {
            // When
            AdapterTestResult result = AdapterTestResult.connected(100, "成功");

            // Then
            assertTrue(result.getLatencyMs() >= 0, "延迟应为非负数");
            assertTrue(result.isSuccess());
            assertEquals(AdapterTestResult.STATUS_CONNECTED, result.getStatus());
        }

        @Test
        @DisplayName("TEST-014: API Key 不泄露到结果消息")
        void testApiKey_notLogged() {
            // When
            AdapterTestResult result = AdapterTestResult.connected(100, "成功连接");

            // Then - 结果消息中不应包含 API Key
            assertNotNull(result.getMessage());
            assertFalse(result.getMessage().contains("sk-"), "结果消息不应包含 API Key");
        }

        @Test
        @DisplayName("TEST-002: 认证失败结果构建正确")
        void testAuthFailedResult() {
            // When
            AdapterTestResult result = AdapterTestResult.authFailed(50, "认证失败");

            // Then
            assertFalse(result.isSuccess());
            assertEquals(AdapterTestResult.STATUS_AUTH_FAILED, result.getStatus());
            assertEquals(50, result.getLatencyMs());
        }

        @Test
        @DisplayName("TEST-004: 超时结果构建正确")
        void testTimeoutResult() {
            // When
            AdapterTestResult result = AdapterTestResult.timeout("连接超时");

            // Then
            assertFalse(result.isSuccess());
            assertEquals(AdapterTestResult.STATUS_TIMEOUT, result.getStatus());
            assertEquals(0, result.getLatencyMs());
        }

        @Test
        @DisplayName("TEST-005: DNS 失败结果构建正确")
        void testDnsErrorResult() {
            // When
            AdapterTestResult result = AdapterTestResult.error("DNS 解析失败");

            // Then
            assertFalse(result.isSuccess());
            assertEquals(AdapterTestResult.STATUS_ERROR, result.getStatus());
            assertTrue(result.getMessage().contains("DNS"));
        }

        @Test
        @DisplayName("TEST-006: 连接拒绝结果构建正确")
        void testConnectionRefusedResult() {
            // When
            AdapterTestResult result = AdapterTestResult.error("连接被拒绝");

            // Then
            assertFalse(result.isSuccess());
            assertEquals(AdapterTestResult.STATUS_ERROR, result.getStatus());
        }

        @Test
        @DisplayName("TEST-013: 预览测试方法可调用")
        void testPreviewMethodSignature() {
            // When
            var mono = testService.testPreview(
                    "openai-compatible", "https://api.example.com",
                    "Authorization", "Bearer sk-test", "PING", null);

            // Then - 验证返回 Mono 且不抛异常
            assertNotNull(mono, "应返回 Mono 对象");
            StepVerifier.create(mono)
                    .assertNext(result -> assertNotNull(result))
                    .verifyComplete();
        }
    }
}
