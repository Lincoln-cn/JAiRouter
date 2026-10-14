package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #354：失败原因分类器必须给出**真实原因**，不得把「实例状态写坏」误报成
 * 「找不到该模型」（那会把排查方向指向模型名/服务类型）。
 *
 * <p>修复前：分类器先按 {@code status=active} 过滤，实例状态一旦是 {@code healthy} 等取值，
 * 就直接返回 404「No instances found for model ...」，后面「不健康 / 熔断」两条永远不可达。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceSelector：失败原因分类（issue #354）")
class ServiceInstanceSelectorReasonTest {

    private static final String MODEL = "probe-model";

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private RateLimitManager rateLimitManager;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    @Mock
    private RoutingMonitorService routingMonitorService;

    private ServiceInstanceSelector selector;

    @BeforeEach
    void setUp() {
        selector = new ServiceInstanceSelector(serviceStateManager, rateLimitManager,
                circuitBreakerManager, routingMonitorService);
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(true);
    }

    private ModelRouterProperties.ModelInstance instance(final String status, final boolean healthy) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-" + status);
        instance.setName(MODEL);
        instance.setBaseUrl("http://127.0.0.1:18099");
        instance.setStatus(status);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(healthy);
        return instance;
    }

    private ResponseStatusException classify(final List<ModelRouterProperties.ModelInstance> instances) {
        return selector.createAppropriateException(
                ModelServiceRegistry.ServiceType.chat, MODEL, instances);
    }

    @Test
    @DisplayName("真没有同名实例 ⇒ 404（保留原语义，且文案仍是「No instances found for model」）")
    void noInstanceWithThatName_is404() {
        ModelRouterProperties.ModelInstance other = new ModelRouterProperties.ModelInstance();
        other.setName("another-model");
        other.setStatus("ACTIVE");

        ResponseStatusException ex = classify(List.of(other));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("No instances found for model"), ex.getReason());
    }

    @Test
    @DisplayName("核心回归钉：status=healthy（被写坏）⇒ 503 且点名 status，**不得**是 404")
    void brokenStatus_isServiceUnavailable_notNotFound() {
        ResponseStatusException ex = classify(List.of(instance("healthy", true)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode(),
                "状态不合法是「有实例但不可路由」，不是「找不到模型」：" + ex.getReason());
        assertTrue(ex.getReason().contains("status"), "文案必须点名 status: " + ex.getReason());
        assertTrue(ex.getReason().contains("not active") || ex.getReason().contains("active"),
                ex.getReason());
        assertFalse(ex.getReason().contains("No instances found for model"),
                "不得再误报「找不到该模型」: " + ex.getReason());
    }

    @Test
    @DisplayName("status=active 但不健康 ⇒ 503 且点名 unhealthy（该分支此前不可达）")
    void unhealthy_isServiceUnavailable() {
        ResponseStatusException ex = classify(List.of(instance("ACTIVE", false)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("healthy"), ex.getReason());
    }

    @Test
    @DisplayName("status=active 且健康但熔断打开 ⇒ 503 且点名 circuit（该分支此前不可达）")
    void circuitOpen_isServiceUnavailable() {
        when(circuitBreakerManager.canExecute(anyString(), anyString())).thenReturn(false);
        ResponseStatusException ex = classify(List.of(instance("ACTIVE", true)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("circuit"), ex.getReason());
    }

    @Test
    @DisplayName("status=inactive（显式停用）⇒ 503 且看出是状态问题，不是 404")
    void inactive_isServiceUnavailable() {
        ResponseStatusException ex = classify(List.of(instance("INACTIVE", true)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("status") || ex.getReason().contains("active"), ex.getReason());
    }
}
