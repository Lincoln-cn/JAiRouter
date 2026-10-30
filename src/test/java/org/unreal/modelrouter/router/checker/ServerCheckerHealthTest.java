package org.unreal.modelrouter.router.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.health.HealthCheckTracingEnhancer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * 健康检查主流程（v3.5.4，#650/#651）。
 *
 * <p>重点守住三件事：①无效 / 缺失的 base-url 必须判不健康，不能沿用上一次状态；
 * ②单个实例的异常不能中断同服务其它实例的检查；③批次可以在测试中直接同步跑完。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCheckerHealthTest {

    private ServerChecker serverChecker;

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private HealthCheckTracingEnhancer tracingEnhancer;

    @BeforeEach
    void setUp() {
        final HealthCheckProperties properties = new HealthCheckProperties();
        properties.setTimeoutMs(300);
        // 本类测的是 TCP 探活语义；#833 之后默认已改为 HTTP，这里必须显式指定。
        properties.setMode(HealthCheckProperties.Mode.TCP);
        // 本类断言的是「单轮探活失败即摘除」这一层语义；#855 的连续失败滞回由
        // ServerCheckerHysteresisTest 覆盖，这里显式把阈值降为 1 保持用例聚焦。
        properties.setFailureThreshold(1);
        serverChecker = new ServerChecker(modelServiceRegistry, serviceStateManager, properties);
    }

    @Test
    @DisplayName("HC-001: base-url 缺失时判不健康，且不中断同服务的其它实例")
    void blankBaseUrlMarksUnhealthyAndContinues() {
        final ModelRouterProperties.ModelInstance blank = instance("blank", " ");
        final ModelRouterProperties.ModelInstance bad = instance("bad", "ht tp://not a url");
        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(blank, bad)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false);

        serverChecker.runHealthCheckBatch();

        verify(serviceStateManager).updateInstanceHealthStatus("chat", blank, false);
        verify(serviceStateManager).updateInstanceHealthStatus("chat", bad, false);
        verify(serviceStateManager).updateServiceHealthStatus("chat", false);
    }

    @Test
    @DisplayName("HC-002: base-url 缺少端口时判不健康（而不是沿用上次状态）")
    void baseUrlWithoutPortMarksUnhealthy() {
        final ModelRouterProperties.ModelInstance instance =
                instance("no-port", "http://172.16.30.6");
        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false);

        serverChecker.runHealthCheckBatch();

        verify(serviceStateManager).updateInstanceHealthStatus("chat", instance, false);
    }

    @Test
    @DisplayName("HC-003: TCP 端口可连时判健康")
    void reachablePortMarksHealthy() throws Exception {
        final ModelRouterProperties.ModelInstance instance;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            instance = instance("tcp-ok", "http://127.0.0.1:" + socket.getLocalPort());
            final Map<ModelServiceRegistry.ServiceType,
                    List<ModelRouterProperties.ModelInstance>> registry = new HashMap<>();
            registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
            when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
            when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);

            serverChecker.runHealthCheckBatch();

            verify(serviceStateManager).updateInstanceHealthStatus("chat", instance, true);
            verify(serviceStateManager).updateServiceHealthStatus("chat", true);
        }
    }

    @Test
    @DisplayName("HC-004: 端口不通时判不健康")
    void unreachablePortMarksUnhealthy() throws Exception {
        final ModelRouterProperties.ModelInstance instance;
        final int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        instance = instance("tcp-dead", "http://127.0.0.1:" + port);
        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false);

        serverChecker.runHealthCheckBatch();

        verify(serviceStateManager).updateInstanceHealthStatus("chat", instance, false);
    }

    private static ModelRouterProperties.ModelInstance instance(final String name, final String baseUrl) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        instance.setPath("/v1/chat/completions");
        return instance;
    }
}
