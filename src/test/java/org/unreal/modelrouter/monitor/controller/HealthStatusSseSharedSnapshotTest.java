package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.monitor.monitoring.metrics.SseConnectionMetrics;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * #866：健康状态 SSE 的周期快照应在所有连接之间共享，而不是每个连接各自全表扫描。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthStatusSseSharedSnapshotTest {

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private SseConnectionMetrics sseConnectionMetrics;

    private HealthStatusSseController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthStatusSseController();
        ReflectionTestUtils.setField(controller, "serviceStateManager", serviceStateManager);
        ReflectionTestUtils.setField(controller, "serviceInstanceRepository", serviceInstanceRepository);
        ReflectionTestUtils.setField(controller, "serviceConfigRepository", serviceConfigRepository);
        ReflectionTestUtils.setField(controller, "sseConnectionMetrics", sseConnectionMetrics);
        when(sseConnectionMetrics.track(any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(controller, "streamIntervalMs", 100L);
        when(serviceConfigRepository.findAll()).thenReturn(List.of());
        when(serviceInstanceRepository.findAll()).thenReturn(List.of());
    }

    @Test
    @DisplayName("866-001 三个并发订阅者共享同一次快照计算")
    void threeConcurrentSubscribersReuseOneSnapshot() {
        final Flux<ServerSentEvent<String>> first = controller.streamHealthStatus();
        final Flux<ServerSentEvent<String>> second = controller.streamHealthStatus();
        final Flux<ServerSentEvent<String>> third = controller.streamHealthStatus();

        StepVerifier.create(Flux.merge(first.take(1), second.take(1), third.take(1)))
                .expectNextCount(3)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        final long computations = controller.snapshotComputations();
        assertTrue(computations > 0, "至少应生成一次快照");
        assertTrue(computations <= 2,
                "三个并发订阅者应共享快照计算（旧实现恰好 3 次），实际生成 " + computations + " 次");
    }

    @Test
    @DisplayName("866-002 没有订阅者时不做任何快照计算")
    void noSubscriberMeansNoComputation() {
        controller.streamHealthStatus();

        sleep(300L);

        assertEquals(0L, controller.snapshotComputations(), "无订阅者时定时器不应运行");
    }

    @Test
    @DisplayName("866-003 单个订阅者仍能收到周期事件")
    void singleSubscriberStillReceivesEvents() {
        final ServerSentEvent<String> event = controller.streamHealthStatus()
                .take(1)
                .blockFirst(Duration.ofSeconds(10));

        assertNotNull(event, "订阅者应收到周期事件");
        assertEquals("health-update", event.event());
        assertNotNull(event.data());
        assertTrue(event.data().contains("instanceHealth"));
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
