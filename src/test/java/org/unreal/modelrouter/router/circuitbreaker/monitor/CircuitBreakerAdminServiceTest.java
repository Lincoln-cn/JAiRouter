package org.unreal.modelrouter.router.circuitbreaker.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.router.circuitbreaker.AdaptiveThresholdManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CircuitBreakerAdminService 单元测试
 *
 * <p>守护「手动重置是一次完整体」这条契约：内存状态机、历史记录、监控事件、自适应计数、持久化删除
 * 五件事必须一起发生；任何一件缺失都会让 #551 的缺陷复活。</p>
 *
 * @author JAiRouter Team
 * @since 3.3.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CircuitBreakerAdminService 手动重置编排")
class CircuitBreakerAdminServiceTest {

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
    private CircuitBreakerAdminService adminService;

    @Test
    @DisplayName("重置：置 CLOSED + 写 MANUAL_RESET 历史 + 写监控事件 + 清零自适应计数 + 删除持久化")
    void resetRunsTheWholeBundle() {
        ServiceInstanceEntity instance = instance("uuid-1", 5L);
        ServiceConfigEntity config = config(5L, "chat");
        AdaptiveThresholdManager.InstanceMetrics metrics =
                new AdaptiveThresholdManager.InstanceMetrics("uuid-1", "instance-1", "chat");
        metrics.recordFailure();
        metrics.recordFailure();

        when(serviceInstanceRepository.findByInstanceId("uuid-1")).thenReturn(Optional.of(instance));
        when(serviceConfigRepository.findById(5L)).thenReturn(Optional.of(config));
        when(circuitBreakerManager.resetCircuitBreakerInPlace("uuid-1")).thenReturn(CircuitBreaker.State.OPEN);
        when(adaptiveThresholdManager.getMetrics("uuid-1")).thenReturn(metrics);
        when(persistenceAdapter.deleteCircuitBreakerState("uuid-1")).thenReturn(Mono.just(true));

        CircuitBreakerResetResult result = adminService.reset("uuid-1", "运维确认上游已恢复").block();

        assertNotNull(result);
        assertEquals("uuid-1", result.instanceId());
        assertEquals("instance-1", result.instanceName());
        assertEquals("chat", result.serviceType());
        assertEquals("OPEN", result.previousState());
        assertEquals("CLOSED", result.currentState());
        assertEquals("MANUAL_RESET", result.triggerReason());
        assertTrue(result.persistedStateDeleted());

        verify(circuitBreakerConfigService).recordStateChange("uuid-1", "instance-1", "chat",
                CircuitBreaker.State.OPEN, CircuitBreaker.State.CLOSED, "MANUAL_RESET", 0, 0);
        verify(monitorService).recordStateChange("uuid-1", "instance-1", "chat",
                "OPEN", "CLOSED", 0, 0, "MANUAL_RESET");
        verify(persistenceAdapter).deleteCircuitBreakerState("uuid-1");
        assertEquals(0, metrics.getTotalCalls(), "手动重置后自适应采样计数必须清零");
    }

    @Test
    @DisplayName("重置：实例当前没有熔断器时返回空 Mono（控制器据此回 404，不再假成功）")
    void resetReturnsEmptyWhenBreakerMissing() {
        when(serviceInstanceRepository.findByInstanceId("missing")).thenReturn(Optional.empty());
        when(circuitBreakerManager.resetCircuitBreakerInPlace("missing")).thenReturn(null);

        assertTrue(adminService.reset("missing", null).blockOptional().isEmpty());
        verifyNoInteractions(persistenceAdapter, circuitBreakerConfigService, monitorService);
    }

    @Test
    @DisplayName("重置：持久化删除失败不影响重置本身，但结果里如实标记未删除")
    void resetSurvivesPersistenceFailure() {
        when(serviceInstanceRepository.findByInstanceId("uuid-2")).thenReturn(Optional.empty());
        when(circuitBreakerManager.resetCircuitBreakerInPlace("uuid-2")).thenReturn(CircuitBreaker.State.HALF_OPEN);
        when(persistenceAdapter.deleteCircuitBreakerState("uuid-2"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        CircuitBreakerResetResult result = adminService.reset("uuid-2", null).block();

        assertNotNull(result);
        assertEquals("HALF_OPEN", result.previousState());
        assertFalse(result.persistedStateDeleted());
        assertNull(result.instanceName());
        verify(circuitBreakerConfigService).recordStateChange(eq("uuid-2"), eq(null), eq(null),
                eq(CircuitBreaker.State.HALF_OPEN), eq(CircuitBreaker.State.CLOSED), eq("MANUAL_RESET"), eq(0), eq(0));
    }

    @Test
    @DisplayName("视图：用实例表补齐实例名、服务类型与上游地址")
    void listInstancesJoinsRegistryFields() {
        when(circuitBreakerManager.getAllCircuitBreakerDetails()).thenReturn(Map.of(
                "uuid-1", Map.of("state", "OPEN", "failureCount", 5, "successCount", 0,
                        "failureThreshold", 3, "successThreshold", 2, "timeout", 60000L,
                        "lastFailureTime", 1000L)));
        when(serviceInstanceRepository.findAll()).thenReturn(List.of(instance("uuid-1", 5L)));
        when(serviceConfigRepository.findAll()).thenReturn(List.of(config(5L, "chat")));

        List<CircuitBreakerInstanceView> views = adminService.listInstances();

        assertEquals(1, views.size());
        CircuitBreakerInstanceView view = views.get(0);
        assertEquals("uuid-1", view.instanceId());
        assertEquals("instance-1", view.instanceName());
        assertEquals("chat", view.serviceType());
        assertEquals("https://api.example.com/v1", view.baseUrl());
        assertEquals("OPEN", view.state());
        assertEquals(5, view.failureCount());
        assertEquals(3, view.failureThreshold());
        assertEquals(60000L, view.timeoutMillis());
        assertEquals(1000L, view.lastFailureTime());
    }

    @Test
    @DisplayName("视图：熔断器存在但实例已删除时保留该行，实例名/服务类型为 null")
    void listInstancesKeepsOrphanBreaker() {
        when(circuitBreakerManager.getAllCircuitBreakerDetails()).thenReturn(Map.of(
                "orphan-uuid", Map.of("state", "CLOSED", "failureCount", 0, "successCount", 0,
                        "failureThreshold", 5, "successThreshold", 2, "timeout", 60000L)));
        when(serviceInstanceRepository.findAll()).thenReturn(List.of());
        when(serviceConfigRepository.findAll()).thenReturn(List.of());

        List<CircuitBreakerInstanceView> views = adminService.listInstances();

        assertEquals(1, views.size());
        assertNull(views.get(0).instanceName());
        assertNull(views.get(0).serviceType());
        assertNull(views.get(0).baseUrl());
        assertNull(views.get(0).lastFailureTime(), "从未失败时 lastFailureTime 必须为 null 而不是 0");
    }

    @Test
    @DisplayName("批量重置：按状态过滤，只重置命中的实例")
    void resetBatchFiltersByState() {
        when(circuitBreakerManager.getAllCircuitBreakerDetails()).thenReturn(Map.of(
                "open-uuid", Map.of("state", "OPEN", "failureCount", 5, "successCount", 0,
                        "failureThreshold", 3, "successThreshold", 2, "timeout", 60000L),
                "closed-uuid", Map.of("state", "CLOSED", "failureCount", 1, "successCount", 0,
                        "failureThreshold", 3, "successThreshold", 2, "timeout", 60000L)));
        when(serviceInstanceRepository.findAll()).thenReturn(List.of());
        when(serviceConfigRepository.findAll()).thenReturn(List.of());
        when(serviceInstanceRepository.findByInstanceId("open-uuid")).thenReturn(Optional.empty());
        when(circuitBreakerManager.resetCircuitBreakerInPlace("open-uuid")).thenReturn(CircuitBreaker.State.OPEN);
        when(persistenceAdapter.deleteCircuitBreakerState("open-uuid")).thenReturn(Mono.just(false));

        CircuitBreakerBatchResetResult result = adminService.resetBatch(null, null, List.of("OPEN"), "批量止血").block();

        assertNotNull(result);
        assertEquals(1, result.matched());
        assertEquals(1, result.reset().size());
        assertEquals("open-uuid", result.reset().get(0).instanceId());
        verify(circuitBreakerManager, never()).resetCircuitBreakerInPlace("closed-uuid");
    }

    @Test
    @DisplayName("批量重置：没有实例列表也没有过滤条件时不重置任何东西（避免无差别清空）")
    void resetBatchWithoutTargetDoesNothing() {
        CircuitBreakerBatchResetResult result = adminService.resetBatch(null, "  ", List.of(), null).block();

        assertNotNull(result);
        assertEquals(0, result.matched());
        assertTrue(result.reset().isEmpty());
        verify(circuitBreakerManager, never()).resetCircuitBreakerInPlace(any());
    }

    @Test
    @DisplayName("重置全部：以内存注册表的全部实例为目标，逐个走完整重置并返回命中数")
    void resetAllResetsEveryInstanceInRegistry() {
        when(circuitBreakerManager.getAllCircuitBreakerDetails()).thenReturn(Map.of(
                "uuid-1", Map.of("state", "OPEN", "failureCount", 5, "successCount", 0,
                        "failureThreshold", 3, "successThreshold", 2, "timeout", 60000L),
                "uuid-2", Map.of("state", "HALF_OPEN", "failureCount", 2, "successCount", 1,
                        "failureThreshold", 3, "successThreshold", 2, "timeout", 60000L)));
        when(serviceInstanceRepository.findAll()).thenReturn(List.of(
                instance("uuid-1", 5L), instance("uuid-2", 5L)));
        when(serviceConfigRepository.findAll()).thenReturn(List.of(config(5L, "chat")));
        when(serviceInstanceRepository.findByInstanceId("uuid-1")).thenReturn(Optional.of(instance("uuid-1", 5L)));
        when(serviceInstanceRepository.findByInstanceId("uuid-2")).thenReturn(Optional.of(instance("uuid-2", 5L)));
        when(serviceConfigRepository.findById(5L)).thenReturn(Optional.of(config(5L, "chat")));
        when(circuitBreakerManager.resetCircuitBreakerInPlace("uuid-1")).thenReturn(CircuitBreaker.State.OPEN);
        when(circuitBreakerManager.resetCircuitBreakerInPlace("uuid-2")).thenReturn(CircuitBreaker.State.HALF_OPEN);
        when(persistenceAdapter.deleteCircuitBreakerState("uuid-1")).thenReturn(Mono.just(true));
        when(persistenceAdapter.deleteCircuitBreakerState("uuid-2")).thenReturn(Mono.just(true));

        CircuitBreakerBatchResetResult result = adminService.resetAll("清空全部").block();

        assertNotNull(result);
        assertEquals(2, result.matched());
        assertEquals(2, result.reset().size());
        List<String> resetIds = result.reset().stream().map(CircuitBreakerResetResult::instanceId).toList();
        assertTrue(resetIds.containsAll(List.of("uuid-1", "uuid-2")), "两个实例都必须被重置");
        assertTrue(result.reset().stream().allMatch(CircuitBreakerResetResult::persistedStateDeleted),
                "持久化状态必须逐个删除");
        verify(circuitBreakerConfigService).recordStateChange("uuid-1", "instance-1", "chat",
                CircuitBreaker.State.OPEN, CircuitBreaker.State.CLOSED, "MANUAL_RESET", 0, 0);
        verify(circuitBreakerConfigService).recordStateChange("uuid-2", "instance-1", "chat",
                CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED, "MANUAL_RESET", 0, 0);
        verify(persistenceAdapter).deleteCircuitBreakerState("uuid-1");
        verify(persistenceAdapter).deleteCircuitBreakerState("uuid-2");
    }

    @Test
    @DisplayName("重置全部：内存里没有熔断器时返回 total=0 且不做任何重置")
    void resetAllWithoutInstancesDoesNothing() {
        when(circuitBreakerManager.getAllCircuitBreakerDetails()).thenReturn(Map.of());
        when(serviceInstanceRepository.findAll()).thenReturn(List.of());
        when(serviceConfigRepository.findAll()).thenReturn(List.of());

        CircuitBreakerBatchResetResult result = adminService.resetAll(null).block();

        assertNotNull(result);
        assertEquals(0, result.matched());
        assertTrue(result.reset().isEmpty());
        verify(circuitBreakerManager, never()).resetCircuitBreakerInPlace(any());
        verifyNoInteractions(persistenceAdapter, circuitBreakerConfigService, monitorService);
    }

    private ServiceInstanceEntity instance(final String uuid, final Long configId) {
        return ServiceInstanceEntity.builder()
                .id(1L)
                .serviceConfigId(configId)
                .instanceName("instance-1")
                .instanceId(uuid)
                .baseUrl("https://api.example.com")
                .path("/v1")
                .build();
    }

    private ServiceConfigEntity config(final Long id, final String serviceType) {
        return ServiceConfigEntity.builder()
                .id(id)
                .configKey(serviceType + "-default")
                .serviceType(serviceType)
                .build();
    }
}
