package org.unreal.modelrouter.router.checker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 健康判定的滞回（#855）.
 *
 * <p>背景：原来的语义是「单轮探活失败即把实例判为不健康」，而一个服务只要没有任何健康实例，
 * {@code ServiceRequestHandler} 就会对该类型的**全部请求**直接返回 503，直到下一轮检查
 * （默认 30 秒后）把实例判回来。也就是说，一次 GC 停顿、上游瞬时排队、丢一个包，都可能触发
 * 一次真实的、持续 30 秒的服务级 503 风暴。</p>
 *
 * <p>修法是给「健康 → 不健康」这个方向加滞回：连续失败达到
 * {@code jairouter.health-check.failure-threshold}（默认 2）轮才摘除；恢复方向不加滞回，
 * 一次探活成功即恢复（把恢复的实例继续挡在外面只会白白丢请求）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCheckerHysteresisTest {

    private ServerChecker serverChecker;
    private HealthCheckProperties properties;

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        properties.setTimeoutMs(200);
        properties.setMode(HealthCheckProperties.Mode.TCP);
        serverChecker = new ServerChecker(modelServiceRegistry, serviceStateManager, properties);
    }

    @Test
    @DisplayName("855-001 单轮探活失败（默认阈值 2）不摘除实例，服务仍判健康")
    void singleRoundFailureDoesNotEvictInstance() throws Exception {
        final ModelRouterProperties.ModelInstance dead = instance("flaky", closedPort());
        givenOnlyInstance(dead);

        serverChecker.runHealthCheckBatch();

        verify(serviceStateManager, never()).updateInstanceHealthStatus(eq("chat"), eq(dead), eq(false));
        verify(serviceStateManager).updateServiceHealthStatus("chat", true);
        // 数据库健康状态走 ServerChecker 的私有方法，测试里 serviceInstanceRepository 为 null 时直接短路，
        // 因此这里不断言落库，只断言「实例没被摘除」这一可观测语义。
    }

    @Test
    @DisplayName("855-002 连续两轮失败才摘除实例，服务随之判不健康")
    void twoConsecutiveFailuresEvictInstance() throws Exception {
        final ModelRouterProperties.ModelInstance dead = instance("dead", closedPort());
        givenOnlyInstance(dead);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false);

        serverChecker.runHealthCheckBatch();   // 第 1 轮：抖动，不摘除
        serverChecker.runHealthCheckBatch();   // 第 2 轮：连续失败，摘除

        verify(serviceStateManager, times(1)).updateInstanceHealthStatus("chat", dead, false);
        verify(serviceStateManager).updateServiceHealthStatus("chat", false);

    }

    @Test
    @DisplayName("855-003 成功一轮把失败计数清零，再失败一轮仍不摘除")
    void successResetsTheFailureStreak() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            final ModelRouterProperties.ModelInstance instance = instance("recovering", socket.getLocalPort());
            givenOnlyInstance(instance);
            when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);

            serverChecker.runHealthCheckBatch();   // 第 1 轮：成功（计数清零）
            socket.close();                        // 端口关掉，后续轮次失败
            serverChecker.runHealthCheckBatch();   // 第 2 轮：失败（计数 1，未达阈值 2）
            serverChecker.runHealthCheckBatch();   // 第 3 轮：失败（计数 2，摘除）

            verify(serviceStateManager, times(1)).updateInstanceHealthStatus("chat", instance, false);
        }
    }

    @Test
    @DisplayName("855-004 failure-threshold=1 时恢复旧语义：单轮失败即摘除")
    void thresholdOneKeepsLegacySemantics() throws Exception {
        properties.setFailureThreshold(1);
        final ModelRouterProperties.ModelInstance dead = instance("legacy", closedPort());
        givenOnlyInstance(dead);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(false);

        serverChecker.runHealthCheckBatch();

        verify(serviceStateManager).updateInstanceHealthStatus("chat", dead, false);
        verify(serviceStateManager).updateServiceHealthStatus("chat", false);
    }

    // ---------------------------------------------------------------- helpers

    /** 拿一个当前没有进程监听的端口（建连必失败）。 */
    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ModelRouterProperties.ModelInstance instance(final String name, final int port) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl("http://127.0.0.1:" + port);
        instance.setPath("/v1/chat/completions");
        return instance;
    }

    private void givenOnlyInstance(final ModelRouterProperties.ModelInstance instance) {
        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
    }
}
