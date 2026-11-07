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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #1010：熔断全开的 503 必须携带「最近一次真实失败」摘要
 * （{@code NoAvailableInstanceException.getLastFailure()} → {@code RouterResponse.data}），
 * 不再让汇总文案遮蔽真实失败原因。
 *
 * @author JAiRouter Team
 * @since v3.8.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("熔断 503 携带最近失败详情（issue #1010）")
class NoAvailableInstanceExceptionTest {

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
    }

    private ModelRouterProperties.ModelInstance openInstance(final String instanceId) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId(instanceId);
        instance.setName(MODEL);
        instance.setBaseUrl("http://127.0.0.1:18099");
        instance.setStatus("ACTIVE");
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);
        when(circuitBreakerManager.canExecute(instanceId, instance.getBaseUrl())).thenReturn(false);
        return instance;
    }

    @Test
    @DisplayName("NOAV-100 熔断全开 ⇒ NoAvailableInstanceException 且 lastFailure 含 reason/at/exceptionClass")
    void circuitOpenCarriesLastFailureDetail() {
        openInstance("inst-a");
        when(circuitBreakerManager.getCircuitBreakerDetail("inst-a")).thenReturn(Map.of(
                "state", "OPEN",
                "lastFailureReason", "CONNECT_FAILED",
                "lastFailureExceptionClass", "ConnectException",
                "lastFailureAt", 1730000000000L));

        ResponseStatusException ex = selector.createAppropriateException(
                ModelServiceRegistry.ServiceType.chat, MODEL, List.of(openInstance("inst-a")));

        NoAvailableInstanceException noAvailable =
                assertInstanceOf(NoAvailableInstanceException.class, ex, "503 熔断分支必须是 NoAvailableInstanceException");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, noAvailable.getStatusCode());
        assertTrue(noAvailable.getReason().contains("circuit"), noAvailable.getReason());

        Map<String, Object> lastFailure = noAvailable.getLastFailure();
        assertNotNull(lastFailure, "lastFailure 不得为空");
        assertEquals("CONNECT_FAILED", lastFailure.get("reason"));
        assertEquals("ConnectException", lastFailure.get("exceptionClass"));
        assertEquals(1730000000000L, lastFailure.get("at"));
        assertEquals("inst-a", lastFailure.get("instanceId"));
    }

    @Test
    @DisplayName("NOAV-101 多个 OPEN 实例取 lastFailureAt 最新的一条")
    void latestFailureWins() {
        ModelRouterProperties.ModelInstance older = openInstance("inst-older");
        ModelRouterProperties.ModelInstance newer = openInstance("inst-newer");
        when(circuitBreakerManager.getCircuitBreakerDetail("inst-older")).thenReturn(Map.of(
                "state", "OPEN", "lastFailureReason", "TIMEOUT",
                "lastFailureExceptionClass", "TimeoutException", "lastFailureAt", 100L));
        when(circuitBreakerManager.getCircuitBreakerDetail("inst-newer")).thenReturn(Map.of(
                "state", "OPEN", "lastFailureReason", "CONNECT_FAILED",
                "lastFailureExceptionClass", "ConnectException", "lastFailureAt", 200L));

        ResponseStatusException ex = selector.createAppropriateException(
                ModelServiceRegistry.ServiceType.chat, MODEL, List.of(older, newer));

        Map<String, Object> lastFailure = ((NoAvailableInstanceException) ex).getLastFailure();
        assertEquals("inst-newer", lastFailure.get("instanceId"));
        assertEquals("CONNECT_FAILED", lastFailure.get("reason"));
    }

    @Test
    @DisplayName("NOAV-102 无失败详情（老进程/恢复后被清）⇒ lastFailure 为 null，异常仍可用")
    void missingDetailYieldsNullLastFailure() {
        openInstance("inst-b");
        when(circuitBreakerManager.getCircuitBreakerDetail("inst-b")).thenReturn(null);

        ResponseStatusException ex = selector.createAppropriateException(
                ModelServiceRegistry.ServiceType.chat, MODEL, List.of(openInstance("inst-b")));

        NoAvailableInstanceException noAvailable = assertInstanceOf(NoAvailableInstanceException.class, ex);
        assertNull(noAvailable.getLastFailure());
    }

    @Test
    @DisplayName("NOAV-103 兼容钉：仍是 ResponseStatusException 503 且文案含 circuit（既有断言不破）")
    void stillResponseStatusExceptionWithCircuitWording() {
        openInstance("inst-c");
        when(circuitBreakerManager.getCircuitBreakerDetail("inst-c")).thenReturn(null);

        ResponseStatusException ex = selector.createAppropriateException(
                ModelServiceRegistry.ServiceType.chat, MODEL, List.of(openInstance("inst-c")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("circuit"), ex.getReason());
    }
}
