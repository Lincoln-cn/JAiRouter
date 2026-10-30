package org.unreal.modelrouter.router.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 健康状态入库（v3.5.4，#652）。
 *
 * <p>同名实例（副本）在库里是多行；缺 instance-id 时旧实现会「按名字全量更新」，
 * 让一次探活把其它副本的状态一起改掉。这里守住「只更新 base-url 匹配的那一行」和
 * 「状态没变就不写库」。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCheckerDatabaseHealthTest {

    private ServerChecker serverChecker;

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceInstanceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        final HealthCheckProperties properties = new HealthCheckProperties();
        properties.setTimeoutMs(300);
        // 本类测的是 TCP 探活语义；#833 之后默认已改为 HTTP，这里必须显式指定。
        properties.setMode(HealthCheckProperties.Mode.TCP);
        serverChecker = new ServerChecker(modelServiceRegistry, serviceStateManager, properties);
        final Field field = ServerChecker.class.getDeclaredField("serviceInstanceRepository");
        field.setAccessible(true);
        field.set(serverChecker, repository);
    }

    @Test
    @DisplayName("DB-001: 缺 instance-id 时只更新 base-url 匹配的那一行")
    void updatesOnlyMatchingBaseUrlRow() throws Exception {
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final String baseUrl = "http://127.0.0.1:" + port;

        final ServiceInstanceEntity replicaA = entity("qwen3:4b", null, "http://10.0.0.1:9090");
        final ServiceInstanceEntity replicaB = entity("qwen3:4b", null, baseUrl);
        when(repository.findAllByInstanceName("qwen3:4b")).thenReturn(List.of(replicaA, replicaB));

        final ModelRouterProperties.ModelInstance instance = instance("qwen3:4b", baseUrl);
        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);

        serverChecker.runHealthCheckBatch();

        verify(repository).save(replicaB);
        verify(repository, never()).save(replicaA);
        verify(repository, never()).save(argThat(e -> e == replicaA));
    }

    @Test
    @DisplayName("DB-002: 状态未变化时不写库")
    void skipsSaveWhenStatusUnchanged() throws Exception {
        // 端口必须在整轮检查期间保持可连，否则探活结果会变成 UNHEALTHY、状态也就「变了」
        try (ServerSocket socket = new ServerSocket(0)) {
            final String baseUrl = "http://127.0.0.1:" + socket.getLocalPort();
            final ServiceInstanceEntity entity = entity("qwen3:4b", "inst-1", baseUrl);
            entity.setHealthStatus("HEALTHY");
            when(repository.findByInstanceId("inst-1")).thenReturn(Optional.of(entity));

            final ModelRouterProperties.ModelInstance instance = instance("qwen3:4b", baseUrl);
            instance.setInstanceId("inst-1");
            final Map<ModelServiceRegistry.ServiceType,
                    List<ModelRouterProperties.ModelInstance>> registry = new HashMap<>();
            registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
            when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
            when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);

            serverChecker.runHealthCheckBatch();

            verify(repository, never()).save(any());
        }
    }

    private static ServiceInstanceEntity entity(final String name, final String instanceId,
                                                final String baseUrl) {
        final ServiceInstanceEntity entity = new ServiceInstanceEntity();
        entity.setInstanceName(name);
        entity.setInstanceId(instanceId);
        entity.setBaseUrl(baseUrl);
        entity.setHealthStatus("UNKNOWN");
        return entity;
    }

    private static ModelRouterProperties.ModelInstance instance(final String name, final String baseUrl) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl(baseUrl);
        instance.setPath("/v1/chat/completions");
        return instance;
    }
}
