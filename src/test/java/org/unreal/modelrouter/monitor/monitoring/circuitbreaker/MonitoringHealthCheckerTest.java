package org.unreal.modelrouter.monitor.monitoring.circuitbreaker;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.monitoring.AsyncMetricsProcessor;
import org.unreal.modelrouter.monitor.monitoring.MetricsMemoryManager;
import org.unreal.modelrouter.monitor.monitoring.error.MetricsErrorHandler;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MonitoringHealthChecker 的「失败消息口径」测试（#1197）。
 *
 * <p>健康详情最终会进监控健康接口与 actuator 健康端点，因此 error 字段同样属于
 * 「客户端可见」：只允许固定短语，异常原文只进日志。</p>
 */
class MonitoringHealthCheckerTest {

    /** 故意带连接串与口令，用来证明详情里没有把它们带出去。 */
    private static final String LEAKY =
            "java.sql.SQLException: 连接失败 jdbc:postgresql://db.internal:5432/prod?password=s3cret";

    private MeterRegistry meterRegistry;
    private MetricsErrorHandler errorHandler;
    private MonitoringHealthChecker checker;

    @BeforeEach
    void setUp() {
        meterRegistry = mock(MeterRegistry.class);
        errorHandler = mock(MetricsErrorHandler.class);
        checker = new MonitoringHealthChecker(
                meterRegistry,
                errorHandler,
                mock(MetricsDegradationStrategy.class),
                mock(MetricsCacheAndRetry.class),
                mock(AsyncMetricsProcessor.class),
                mock(MetricsMemoryManager.class));
    }

    @Test
    @DisplayName("组件读取失败：详情里的 error 是固定短语，不含异常原文")
    void componentFailureDoesNotEchoExceptionDetail() {
        when(errorHandler.getErrorStats()).thenThrow(new RuntimeException(LEAKY));

        Map<String, Object> details = checker.performHealthCheck().getDetails();
        Map<?, ?> errorHandlerDetails = (Map<?, ?>) details.get("errorHandler");

        assertNotNull(errorHandlerDetails, "详情里应保留 errorHandler 一节");
        assertEquals("检查错误处理器状态失败", errorHandlerDetails.get("error"));
        assertNoLeak(String.valueOf(errorHandlerDetails.get("error")));
    }

    @Test
    @DisplayName("指标注册表不可用：详情里的 error 是固定短语，不含异常原文")
    void meterRegistryFailureDoesNotEchoExceptionDetail() {
        when(meterRegistry.getMeters()).thenThrow(new IllegalStateException(LEAKY));

        Map<String, Object> details = checker.performHealthCheck().getDetails();
        Map<?, ?> registryDetails = (Map<?, ?>) details.get("meterRegistry");

        assertNotNull(registryDetails, "详情里应保留 meterRegistry 一节");
        assertEquals("检查指标注册表状态失败", registryDetails.get("error"));
        assertNoLeak(String.valueOf(registryDetails.get("error")));
    }

    private void assertNoLeak(final String text) {
        assertFalse(text.contains("jdbc"), "不得回显连接串: " + text);
        assertFalse(text.contains("s3cret"), "不得回显口令: " + text);
        assertFalse(text.contains("SQLException"), "不得回显异常类名: " + text);
    }
}
