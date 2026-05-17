package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.test.StepVerifier;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ModelInfoController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/models - 获取所有可用模型
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ModelInfoController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class ModelInfoControllerTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private AdapterRegistry adapterRegistry;

    @InjectMocks
    private ModelInfoController controller;

    @BeforeEach
    void setUp() {
        // 配置模拟数据 - getAvailableModels 返回 Set<String>
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat))
                .thenReturn(new java.util.HashSet<>(Arrays.asList("gpt-4", "gpt-3.5-turbo")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.embedding))
                .thenReturn(new java.util.HashSet<>(Arrays.asList("text-embedding-3", "text-embedding-ada-002")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.rerank))
                .thenReturn(new java.util.HashSet<>(Collections.singletonList("rerank-v1")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.tts))
                .thenReturn(new java.util.HashSet<>(Collections.singletonList("tts-1")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.stt))
                .thenReturn(new java.util.HashSet<>(Collections.singletonList("whisper-1")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.imgGen))
                .thenReturn(new java.util.HashSet<>(Arrays.asList("dall-e-3", "dall-e-2")));
        lenient().when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.imgEdit))
                .thenReturn(new java.util.HashSet<>(Collections.singletonList("dall-e-2")));

        // 模拟适配器
        ServiceCapability mockAdapter = mock(ServiceCapability.class);
        lenient().when(adapterRegistry.getAdapter(any(ModelServiceRegistry.ServiceType.class)))
                .thenReturn(mockAdapter);
    }

    // ==================== 获取所有可用模型测试 ====================

    @Nested
    @DisplayName("GET /api/models - 获取所有可用模型测试")
    class GetModelsTests {

        @Test
        @DisplayName("MODEL-001: 成功获取所有模型")
        void testGetModels_success() {
            // When
            var result = controller.getModels();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("MODEL-002: 验证模型列表包含必要字段")
        void testGetModels_structure() {
            // When
            var result = controller.getModels();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) response.getData();
                        assertEquals("list", data.get("object"));
                        assertNotNull(data.get("data"));

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> models = (List<Map<String, Object>>) data.get("data");
                        for (Map<String, Object> model : models) {
                            assertTrue(model.containsKey("id"));
                            assertTrue(model.containsKey("object"));
                            assertTrue(model.containsKey("service_type"));
                        }
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("MODEL-003: 异常处理")
        void testGetModels_exception() {
            // Given
            when(registry.getAvailableModels(any(ModelServiceRegistry.ServiceType.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When
            var result = controller.getModels();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("获取模型列表失败"));
                    })
                    .verifyComplete();
        }
    }
}
