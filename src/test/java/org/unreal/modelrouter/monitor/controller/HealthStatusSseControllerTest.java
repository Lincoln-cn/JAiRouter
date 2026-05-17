package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.checker.ServiceStateManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HealthStatusSseController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthStatusSseControllerTest {

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @InjectMocks
    private HealthStatusSseController controller;

    @Nested
    @DisplayName("SSE 流测试")
    class StreamHealthStatusTests {

        @Test
        @DisplayName("SSE 流正常创建")
        void streamCreatesSuccessfully() {
            // 验证 SSE 流可以被创建
            var flux = controller.streamHealthStatus();
            assertNotNull(flux);
        }

        @Test
        @DisplayName("SSE 流非空")
        void streamIsNotNull() {
            var flux = controller.streamHealthStatus();
            assertNotNull(flux, "SSE 流不应为 null");
        }
    }

    @Nested
    @DisplayName("健康状态变化通知测试")
    class NotifyHealthStatusChangeTests {

        @Test
        @DisplayName("主动推送更新成功")
        void notifySuccess() {
            // 先触发 SSE 流初始化
            controller.streamHealthStatus();

            // 触发通知（不应抛出异常）
            assertDoesNotThrow(() -> controller.notifyHealthStatusChange());
        }

        @Test
        @DisplayName("多次通知不会抛出异常")
        void multipleNotificationsSucceed() {
            controller.streamHealthStatus();
            
            assertDoesNotThrow(() -> {
                controller.notifyHealthStatusChange();
                controller.notifyHealthStatusChange();
            });
        }
    }
}
