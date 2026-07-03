package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.interceptor.ControllerTracingInterceptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UniversalController RESTful 接口测试
 *
 * 测试范围：
 * - POST /api/v1/chat/completions - 聊天补全
 * - POST /api/v1/embeddings - 文本嵌入
 * - POST /api/v1/rerank - 重排序
 * - POST /api/v1/audio/speech - 文本转语音
 * - POST /api/v1/images/generations - 图像生成
 * - POST /api/v1/images/edits - 图像编辑
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("UniversalController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UniversalControllerTest {

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ControllerTracingInterceptor tracingInterceptor;

    @Mock
    private ServerWebExchange exchange;

    @InjectMocks
    private UniversalController controller;

    @BeforeEach
    void setUp() {
        // 配置服务健康状态
        lenient().when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        // 配置实例选择
        ModelRouterProperties.ModelInstance mockInstance = mock(ModelRouterProperties.ModelInstance.class);
        lenient().when(mockInstance.getName()).thenReturn("test-instance");
        lenient().when(mockInstance.getAdapter()).thenReturn("normal");
        lenient().when(registry.selectInstance(any(), anyString(), anyString())).thenReturn(mockInstance);

        // 配置适配器
        ServiceCapability mockAdapter = mock(ServiceCapability.class);
        lenient().when(adapterRegistry.getAdapter(any(ModelServiceRegistry.ServiceType.class), any())).thenReturn(mockAdapter);

        // 配置HTTP请求
        HttpHeaders headers = new HttpHeaders();
        lenient().when(exchange.getRequest().getHeaders()).thenReturn(headers);
        lenient().when(exchange.getRequest().getMethod()).thenReturn(org.springframework.http.HttpMethod.POST);
    }

    // ==================== Chat Completions 测试 ====================

    @Nested
    @DisplayName("POST /api/v1/chat/completions - 聊天补全测试")
    class ChatCompletionsTests {

        @Test
        @DisplayName("UNIVERSAL-001: 验证Controller注入成功")
        void testControllerInjection() {
            // 验证Controller已成功注入
            assertNotNull(controller);
        }
    }

    // ==================== Embeddings 测试 ====================

    @Nested
    @DisplayName("POST /api/v1/embeddings - 文本嵌入测试")
    class EmbeddingsTests {

        @Test
        @DisplayName("UNIVERSAL-002: 请求体为空抛出异常")
        void testEmbeddings_nullRequest() {
            // When & Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ServerWebInputException.class,
                    () -> controller.embeddings("Bearer token", null, exchange)
            );
        }
    }

    // ==================== 图像生成测试 ====================

    @Nested
    @DisplayName("POST /api/v1/images/generations - 图像生成测试")
    class ImageGenerateTests {

        @Test
        @DisplayName("UNIVERSAL-003: 请求体为空抛出异常")
        void testImageGenerate_nullRequest() {
            // When & Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ServerWebInputException.class,
                    () -> controller.imageGenerate("Bearer token", null, exchange)
            );
        }
    }

    // ==================== 图像编辑测试 ====================

    @Nested
    @DisplayName("POST /api/v1/images/edits - 图像编辑测试")
    class ImageEditsTests {

        @Test
        @DisplayName("UNIVERSAL-004: 请求体为空抛出异常")
        void testImageEdits_nullRequest() {
            // When & Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ServerWebInputException.class,
                    () -> controller.imageEdits("Bearer token", null, exchange)
            );
        }
    }
}
