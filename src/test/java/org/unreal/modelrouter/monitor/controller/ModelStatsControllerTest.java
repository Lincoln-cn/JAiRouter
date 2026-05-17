package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * ModelStatsController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ModelStatsControllerTest {

    @Mock
    private ModelServiceRegistry registry;

    @InjectMocks
    private ModelStatsController controller;

    @Nested
    @DisplayName("获取配置统计信息测试")
    class GetConfigurationStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            when(registry.getAllServiceTypes()).thenReturn(Set.of("chat", "embedding"));
            when(registry.getAllInstances()).thenReturn(java.util.Collections.emptyMap());
            when(registry.getAvailableModels(any())).thenReturn(java.util.Collections.emptySet());

            StepVerifier.create(controller.getConfigurationStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                        assert response.getBody().isSuccess();
                        assert response.getBody().getData() != null;
                    })
                    .verifyComplete();

            verify(registry).getAllServiceTypes();
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(registry.getAllServiceTypes()).thenThrow(new RuntimeException("Test error"));

            StepVerifier.create(controller.getConfigurationStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 500;
                        assert response.getBody() != null;
                        assert !response.getBody().isSuccess();
                    })
                    .verifyComplete();
        }
    }
}
