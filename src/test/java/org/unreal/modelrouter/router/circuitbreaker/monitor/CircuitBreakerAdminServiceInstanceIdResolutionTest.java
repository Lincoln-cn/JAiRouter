package org.unreal.modelrouter.router.circuitbreaker.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.router.circuitbreaker.AdaptiveThresholdManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

/**
 * #1009 的守卫：管理 API 暴露的 {@code instanceId} 是数据库主键的字符串形式，
 * 而熔断器的键是 {@code service_instance.instance_id} 里的 UUID。
 *
 * <p>复位接口必须同时接受两者 —— 否则调用方拿 API 返回值去复位，永远得到
 * 「该实例当前没有熔断器记录」。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#1009 熔断器复位同时接受 UUID 与数据库主键")
class CircuitBreakerAdminServiceInstanceIdResolutionTest {

    private static final String INSTANCE_UUID = "f0461c5b-f894-4b6e-aace-6a133b2a1559";
    private static final String PRIMARY_KEY = "1";

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    @Mock
    private AdaptiveThresholdManager adaptiveThresholdManager;

    @Mock
    private CircuitBreakerConfigService circuitBreakerConfigService;

    @Mock
    private CircuitBreakerMonitorService monitorService;

    @Mock
    private CircuitBreakerStatePersistenceAdapter persistenceAdapter;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @InjectMocks
    private CircuitBreakerAdminService service;

    private ServiceInstanceEntity instanceEntity() {
        return ServiceInstanceEntity.builder()
                .id(1L)
                .instanceId(INSTANCE_UUID)
                .instanceName("qwen3:4b")
                .build();
    }

    @Test
    @DisplayName("传数据库主键时，内部解析成 UUID 再复位")
    void primaryKeyIsResolvedToUuid() {
        when(serviceInstanceRepository.findByInstanceId(PRIMARY_KEY)).thenReturn(Optional.empty());
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(instanceEntity()));
        when(serviceInstanceRepository.findByInstanceId(INSTANCE_UUID)).thenReturn(Optional.of(instanceEntity()));
        when(circuitBreakerManager.resetCircuitBreakerInPlace(INSTANCE_UUID)).thenReturn(CircuitBreaker.State.OPEN);
        when(persistenceAdapter.deleteCircuitBreakerState(INSTANCE_UUID))
                .thenReturn(reactor.core.publisher.Mono.just(true));

        CircuitBreakerResetResult result = service.reset(PRIMARY_KEY, "test").block();

        assertThat(result).isNotNull();
        assertThat(result.instanceId()).isEqualTo(INSTANCE_UUID);
        assertThat(result.previousState()).isEqualTo("OPEN");
        verify(circuitBreakerManager).resetCircuitBreakerInPlace(INSTANCE_UUID);
        verify(circuitBreakerManager, never()).resetCircuitBreakerInPlace(PRIMARY_KEY);
    }

    @Test
    @DisplayName("传 UUID 时行为不变，不再尝试把它当主键")
    void uuidStillWorks() {
        when(serviceInstanceRepository.findByInstanceId(INSTANCE_UUID)).thenReturn(Optional.of(instanceEntity()));
        when(circuitBreakerManager.resetCircuitBreakerInPlace(INSTANCE_UUID))
                .thenReturn(CircuitBreaker.State.HALF_OPEN);
        when(persistenceAdapter.deleteCircuitBreakerState(INSTANCE_UUID))
                .thenReturn(reactor.core.publisher.Mono.just(true));

        CircuitBreakerResetResult result = service.reset(INSTANCE_UUID, "test").block();

        assertThat(result).isNotNull();
        assertThat(result.instanceId()).isEqualTo(INSTANCE_UUID);
        verify(serviceInstanceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("既不是现有主键、也解析不出 UUID 时返回空（控制器回 404）")
    void unknownIdStillEmpty() {
        when(serviceInstanceRepository.findByInstanceId("999")).thenReturn(Optional.empty());
        when(serviceInstanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(service.reset("999", "test").block()).isNull();
        // 说明：实现仍会用原值问一次内存里的熔断器（它可能没有对应的 DB 行），
        // 那一步返回 null 后整体就是 empty —— 这里断言的是「最终结果为空」，
        // 而不是「一次都没问过」。
    }

    @Test
    @DisplayName("能解析出 UUID 但该实例没有熔断器记录时也返回空")
    void noCircuitBreakerStillEmpty() {
        when(serviceInstanceRepository.findByInstanceId(PRIMARY_KEY)).thenReturn(Optional.empty());
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(instanceEntity()));
        when(serviceInstanceRepository.findByInstanceId(INSTANCE_UUID)).thenReturn(Optional.of(instanceEntity()));
        when(circuitBreakerManager.resetCircuitBreakerInPlace(INSTANCE_UUID)).thenReturn(null);

        assertThat(service.reset(PRIMARY_KEY, "test").block()).isNull();
        // 未触发持久化删除
        verify(persistenceAdapter, never()).deleteCircuitBreakerState(anyString());
        // 显式引用一次，避免静态分析把它当未使用
        assertThat(ReflectionTestUtils.getField(service, "monitorService")).isSameAs(monitorService);
    }
}
