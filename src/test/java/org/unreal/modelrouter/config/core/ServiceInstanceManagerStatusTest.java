package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.common.constants.InstanceStatus;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #354：实例 {@code status} 写入侧必须按词表校验/归一 —— 不得静默接受
 * {@code "HEALTHY"} 这类取值（它会让实例被可用性过滤排除，客户端收到误导性的 404）。
 *
 * <p>判据：非 {@code active}/{@code inactive}（大小写不敏感）⇒ {@link IllegalArgumentException}
 * （经 {@code ApiExceptions.wrap} 映射为 400 INVALID_REQUEST）且**不落库**；
 * 合法值一律归一为规范大写。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceManager：status 词表校验（issue #354）")
class ServiceInstanceManagerStatusTest {

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.InstanceRateLimitRepository rateLimitRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.InstanceCircuitBreakerRepository circuitBreakerRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private org.unreal.modelrouter.config.core.manager.ConfigVersionManager configVersionManager;

    @Mock
    private org.unreal.modelrouter.router.model.ModelServiceRegistry modelServiceRegistry;

    @Mock
    private org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager circuitBreakerManager;

    private ServiceInstanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceInstanceManager(
                serviceInstanceRepository, rateLimitRepository, circuitBreakerRepository,
                serviceConfigRepository, configurationService, configVersionManager,
                modelServiceRegistry, circuitBreakerManager);
        when(serviceInstanceRepository.save(any())).thenAnswer(inv -> {
            ServiceInstanceEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(1L);
            }
            return entity;
        });
    }

    private CreateServiceInstanceRequest request(final String status) {
        CreateServiceInstanceRequest request = new CreateServiceInstanceRequest();
        request.setName("inst-1");
        request.setBaseUrl("http://127.0.0.1:18099");
        request.setPath("/v1/chat/completions");
        request.setStatus(status);
        return request;
    }

    private ServiceInstanceEntity savedEntity() {
        ArgumentCaptor<ServiceInstanceEntity> captor = ArgumentCaptor.forClass(ServiceInstanceEntity.class);
        verify(serviceInstanceRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("创建：status=\"HEALTHY\" ⇒ 400（IllegalArgumentException）且不落库")
    void create_rejectsHealthLikeStatus() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createInstance(1L, request("HEALTHY")));
        assertTrue(ex.getMessage().contains("HEALTHY"), ex.getMessage());
        assertTrue(ex.getMessage().contains("ACTIVE") && ex.getMessage().contains("INACTIVE"),
                "错误消息必须列出合法取值: " + ex.getMessage());
        verify(serviceInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建：status=\"active\" ⇒ 归一为规范值 ACTIVE")
    void create_normalizesLowercase() {
        manager.createInstance(1L, request("active"));
        assertEquals(InstanceStatus.ACTIVE, savedEntity().getStatus());
    }

    @Test
    @DisplayName("创建：status=\"InActive\"（混合大小写）⇒ 归一为 INACTIVE")
    void create_normalizesMixedCase() {
        manager.createInstance(1L, request("InActive"));
        assertEquals(InstanceStatus.INACTIVE, savedEntity().getStatus());
    }

    @Test
    @DisplayName("创建：不传 status ⇒ 默认 ACTIVE")
    void create_defaultsToActive() {
        manager.createInstance(1L, request(null));
        assertEquals(InstanceStatus.ACTIVE, savedEntity().getStatus());
    }

    @Test
    @DisplayName("更新：status=\"healthy\" ⇒ 400 且不落库（历史数据里的坏值不再被写出来）")
    void update_rejectsBadStatus() {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .id(7L).serviceConfigId(1L).instanceName("inst-1").instanceId("uuid-1")
                .baseUrl("http://127.0.0.1:18099").path("/v1/chat/completions")
                .weight(1).status(InstanceStatus.ACTIVE).healthStatus("HEALTHY")
                .build();
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class,
                () -> manager.updateInstance(7L, request("healthy")));
        verify(serviceInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新：status=\"inactive\" ⇒ 落库为 INACTIVE（合法值不受影响）")
    void update_acceptsInactive() {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .id(7L).serviceConfigId(1L).instanceName("inst-1").instanceId("uuid-1")
                .baseUrl("http://127.0.0.1:18099").path("/v1/chat/completions")
                .weight(1).status(InstanceStatus.ACTIVE).healthStatus("HEALTHY")
                .build();
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(entity));

        manager.updateInstance(7L, request("inactive"));

        assertEquals(InstanceStatus.INACTIVE, savedEntity().getStatus());
    }
}
