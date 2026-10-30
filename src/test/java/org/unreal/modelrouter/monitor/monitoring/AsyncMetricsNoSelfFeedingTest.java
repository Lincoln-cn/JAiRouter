package org.unreal.modelrouter.monitor.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.collector.AsyncMetricsCollector;
import org.unreal.modelrouter.monitor.monitoring.collector.DefaultMetricsCollector;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #848 守卫：异步指标处理器不得把「处理结果」重新入队。
 *
 * <p>缺陷形态：{@link AsyncMetricsProcessor} 原先注入接口 {@code MetricsCollector}，而该接口的
 * {@code @Primary} 实现是 {@link AsyncMetricsCollector}；处理一个事件时又调回异步采集器，事件被
 * 重新入队 ⇒ 自持队列。实测后果（v3.5.7 默认配置）：71 秒自产 167 万个事件、
 * {@code metrics-processor} 单核 75% CPU，而真实业务指标一条都没进 Micrometer。</p>
 *
 * <p>本用例同时断言两件事，缺一不可：① 一次采集真的落到 {@link DefaultMetricsCollector}
 * （Micrometer 里出现 {@code jairouter_requests_total}）；② 处理计数**不再随时间增长**
 * （旧实现下它每秒涨两万多，断言必然红）。</p>
 */
class AsyncMetricsNoSelfFeedingTest {

    private AsyncMetricsProcessor processor;
    private MetricsMemoryManager memoryManager;

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.shutdown();
        }
        if (memoryManager != null) {
            memoryManager.shutdown();
        }
    }

    @Test
    @DisplayName("848-001 一次采集只处理一次，且真实写入 Micrometer")
    void singleRecordIsProcessedOnceAndReachesMicrometer() throws InterruptedException {
        final MonitoringProperties properties = new MonitoringProperties();
        properties.getPerformance().setAsyncProcessing(true);
        properties.getPerformance().setBufferSize(64);
        properties.getSampling().setRequestMetrics(1.0);

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final DefaultMetricsCollector defaultCollector = new DefaultMetricsCollector(registry, properties);
        this.memoryManager = new MetricsMemoryManager(properties);
        this.processor = new AsyncMetricsProcessor(properties, defaultCollector, new MetricsCircuitBreaker());
        final AsyncMetricsCollector asyncCollector = new AsyncMetricsCollector(
                properties, processor, memoryManager, defaultCollector);

        asyncCollector.recordRequest("chat", "POST", 12L, "200");

        // 等待首个事件被处理（最多 5 秒）
        final long deadline = System.currentTimeMillis() + 5_000L;
        while (processor.getStats().getProcessedCount() < 1L && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertTrue(processor.getStats().getProcessedCount() >= 1L,
                "事件未被处理，异步链路没有工作");

        // 静置一段时间：旧实现会在此期间持续自产事件，计数飞涨
        Thread.sleep(400L);
        final long settled = processor.getStats().getProcessedCount();
        Thread.sleep(400L);
        final long later = processor.getStats().getProcessedCount();

        assertTrue(later - settled <= 2L,
                "处理计数仍在持续增长（" + settled + " → " + later + "），说明事件被重新入队："
                        + "处理器必须只依赖 DefaultMetricsCollector，不得回调 @Primary 的异步采集器");

        assertTrue(registry.find(properties.getPrefix() + "_requests_total").counter() != null,
                "业务请求指标没有落到 Micrometer：异步链路把事件吃掉后没有交给同步采集器");
    }

    @Test
    @DisplayName("848-002 处理器线程内采集走同步路径，不再入队")
    void recordFromProcessorThreadGoesSyncInsteadOfEnqueueing() throws Exception {
        final MonitoringProperties properties = new MonitoringProperties();
        properties.getPerformance().setAsyncProcessing(true);
        properties.getPerformance().setBufferSize(64);
        properties.getSampling().setRequestMetrics(1.0);

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final DefaultMetricsCollector defaultCollector = new DefaultMetricsCollector(registry, properties);
        this.memoryManager = new MetricsMemoryManager(properties);
        this.processor = new AsyncMetricsProcessor(properties, defaultCollector, new MetricsCircuitBreaker());
        final AsyncMetricsCollector asyncCollector = new AsyncMetricsCollector(
                properties, processor, memoryManager, defaultCollector);

        final long before = processor.getStats().getProcessedCount();
        final Thread processorThread = new Thread(
                () -> asyncCollector.recordRequest("chat", "POST", 7L, "200"), "metrics-processor-sim");
        processorThread.start();
        processorThread.join(2_000L);

        Thread.sleep(300L);
        // 旧实现（没有「处理器线程内走同步」这道保险）会把这次调用重新入队，processedCount 必然 +1
        assertTrue(processor.getStats().getProcessedCount() == before,
                "处理器线程内的采集又被入队了（processedCount " + before + " → "
                        + processor.getStats().getProcessedCount() + "）：必须改走同步采集器");
        assertTrue(registry.find(properties.getPrefix() + "_requests_total").counter() != null,
                "处理器线程内的采集没有落到 Micrometer");
    }
}
