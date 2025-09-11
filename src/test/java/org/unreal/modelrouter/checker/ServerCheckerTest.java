package org.unreal.modelrouter.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.tracing.health.HealthCheckTracingEnhancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerCheckerTest {

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
        serverChecker = new ServerChecker(modelServiceRegistry, serviceStateManager);
    }

    @Test
    void testCheckAllServicesWithEmptyRegistry() {
        // Given
        when(modelServiceRegistry.getAllInstances()).thenReturn(new HashMap<>());

        // When
        serverChecker.checkAllServices();

        // Then
        // 验证没有执行任何检查
        verifyNoInteractions(serviceStateManager);
    }

    @Test
    void testCheckAllServicesWithInstances() {
        // Given
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry = new HashMap<>();
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-instance");
        instance.setBaseUrl("http://localhost:8080");
        instances.add(instance);
        
        instanceRegistry.put(ModelServiceRegistry.ServiceType.chat, instances);
        when(modelServiceRegistry.getAllInstances()).thenReturn(instanceRegistry);

        // When
        serverChecker.checkAllServices();

        // Then
        // 验证检查了实例的健康状态
        verify(serviceStateManager, atLeastOnce()).isInstanceHealthy(anyString(), any());
    }
}