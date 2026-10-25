package org.unreal.modelrouter.router.adapter.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 调用结果分类的记账测试（v3.5.4，#647）。
 *
 * <p>核心约定：4xx（客户端错误）只完成在途计数、不累加熔断失败；5xx / 网络异常才计入失败。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdapterMetricsRecorder 调用结果分类（#647）")
class AdapterMetricsOutcomeTest {

    private static final ModelServiceRegistry.ServiceType CHAT = ModelServiceRegistry.ServiceType.chat;
    private static final String MODEL = "gpt-4";

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ModelCallStatsRepository statsRepository;

    @Mock
    private ModelServiceRegistry registry;

    private AdapterMetricsRecorder recorder;

    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        recorder = new AdapterMetricsRecorder(metricsCollector, statsRepository, registry);
        instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:4b");
        instance.setInstanceId("inst-1");
        instance.setBaseUrl("http://172.16.30.6:9090");
    }

    @Test
    @DisplayName("客户端错误：只完成在途计数，不计入熔断失败")
    void clientErrorDoesNotCountAsBreakerFailure() {
        recorder.recordCompleteCall("gpustack", "inst-1", 12L,
                AdapterMetricsRecorder.CallOutcome.CLIENT_ERROR, "UPSTREAM_400", MODEL, CHAT, instance);

        verify(registry).recordCallComplete(CHAT, instance, 12L, false);
        verify(registry, never()).recordCallFailure(any(), any(), anyLong(), any());
        // 可观测性不受影响：统计与错误码照常记录
        verify(statsRepository).updateStats("chat", MODEL, false, 12L);
        verify(statsRepository).recordErrorCode("chat", MODEL, "UPSTREAM_400");
    }

    @Test
    @DisplayName("实例故障：计入熔断失败")
    void instanceFailureCountsAsBreakerFailure() {
        recorder.recordCompleteCall("gpustack", "inst-1", 30L,
                AdapterMetricsRecorder.CallOutcome.INSTANCE_FAILURE, "UPSTREAM_500", MODEL, CHAT, instance);

        verify(registry).recordCallFailure(CHAT, instance, 30L, "UPSTREAM_500");
        verify(registry, never()).recordCallComplete(any(), any(), anyLong(), eq(false));
        verify(statsRepository).updateStats("chat", MODEL, false, 30L);
    }

    @Test
    @DisplayName("成功：计入成功并重置熔断失败计数")
    void successIsRecordedAsSuccess() {
        recorder.recordCompleteCallStats("gpustack", "inst-1", 8L,
                AdapterMetricsRecorder.CallOutcome.SUCCESS, null, MODEL, CHAT, instance);

        verify(registry).recordCallComplete(CHAT, instance, 8L, true);
        verify(registry, never()).recordCallFailure(any(), any(), anyLong(), any());
        verify(statsRepository).updateStats("chat", MODEL, true, 8L);
    }

    @Test
    @DisplayName("布尔重载默认按实例故障处理（保持向后兼容）")
    void booleanOverloadFallsBackToInstanceFailure() {
        recorder.recordCompleteCallStats("gpustack", "inst-1", 5L, false,
                "TIMEOUT", MODEL, CHAT, instance);

        verify(registry).recordCallFailure(CHAT, instance, 5L, "TIMEOUT");
    }
}
