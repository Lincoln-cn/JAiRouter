package org.unreal.modelrouter.monitor.monitoring.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsBatchReporter 单元测试
 *
 * @author JAiRouter Team
 * @since v2.7.x
 */
class MetricsBatchReporterTest {

    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private MetricsBatchReporter reporter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        reporter = new MetricsBatchReporter(meterRegistry, objectMapper);
    }

    @AfterEach
    void tearDown() {
        reporter.clearBuffer();
    }

    // ==================== 构造函数测试 ====================

    @Test
    @DisplayName("构造函数应正确初始化")
    void constructor_shouldInitialize() {
        assertNotNull(reporter);
        MetricsBatchReporter.ReporterStats stats = reporter.getStats();
        assertNotNull(stats);
        assertEquals(0, stats.getTotalMetricsCollected());
        assertEquals(0, stats.getTotalBatchesSent());
        assertEquals("LOG", stats.getReportTarget());
    }

    // ==================== 统计信息测试 ====================

    @Test
    @DisplayName("获取统计信息应返回正确值")
    void getStats_shouldReturnCorrectValues() {
        MetricsBatchReporter.ReporterStats stats = reporter.getStats();

        assertEquals(0, stats.getTotalMetricsCollected());
        assertEquals(0, stats.getTotalBatchesSent());
        assertEquals(0, stats.getBufferSize());
        assertEquals("LOG", stats.getReportTarget());
        assertTrue(stats.getLastReportTime() > 0);
    }

    @Test
    @DisplayName("toString 应包含关键信息")
    void statsToString_shouldContainKeyInfo() {
        MetricsBatchReporter.ReporterStats stats = reporter.getStats();
        String str = stats.toString();

        assertTrue(str.contains("collected=0"));
        assertTrue(str.contains("batches=0"));
        assertTrue(str.contains("target=LOG"));
    }

    // ==================== 上报目标切换测试 ====================

    @Test
    @DisplayName("设置上报目标应更新状态")
    void setReportTarget_shouldUpdateState() {
        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.HTTP);
        assertEquals("HTTP", reporter.getStats().getReportTarget());

        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.FILE);
        assertEquals("FILE", reporter.getStats().getReportTarget());

        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.LOG);
        assertEquals("LOG", reporter.getStats().getReportTarget());
    }

    // ==================== 缓冲区管理测试 ====================

    @Test
    @DisplayName("清空缓冲区应重置大小")
    void clearBuffer_shouldResetSize() {
        // 添加一些指标
        Counter counter = Counter.builder("test.counter").register(meterRegistry);
        counter.increment();

        // 触发收集
        reporter.collectMetrics();

        // 清空缓冲区
        reporter.clearBuffer();
        assertEquals(0, reporter.getStats().getBufferSize());
    }

    // ==================== 指标收集测试 ====================

    @Test
    @DisplayName("收集 Counter 指标应成功")
    void collectMetrics_counter_shouldSucceed() {
        Counter counter = Counter.builder("test.counter")
                .tag("service", "chat")
                .register(meterRegistry);
        counter.increment(5);

        reporter.collectMetrics();

        assertTrue(reporter.getStats().getBufferSize() > 0);
    }

    @Test
    @DisplayName("收集 Gauge 指标应成功")
    void collectMetrics_gauge_shouldSucceed() {
        Gauge.builder("test.gauge", () -> 42.0)
                .tag("type", "test")
                .register(meterRegistry);

        reporter.collectMetrics();

        assertTrue(reporter.getStats().getBufferSize() > 0);
    }

    @Test
    @DisplayName("收集 Timer 指标应成功")
    void collectMetrics_timer_shouldSucceed() {
        Timer timer = Timer.builder("test.timer")
                .tag("operation", "request")
                .register(meterRegistry);
        timer.record(100, TimeUnit.MILLISECONDS);

        reporter.collectMetrics();

        assertTrue(reporter.getStats().getBufferSize() > 0);
    }

    @Test
    @DisplayName("收集多个指标应全部加入缓冲区")
    void collectMetrics_multipleMeters_shouldAllBeCollected() {
        Counter.builder("test.counter1").register(meterRegistry);
        Counter.builder("test.counter2").register(meterRegistry);
        Gauge.builder("test.gauge", () -> 1.0).register(meterRegistry);

        reporter.collectMetrics();

        assertTrue(reporter.getStats().getBufferSize() >= 3);
    }

    // ==================== Prometheus 格式化测试 ====================

    @Test
    @DisplayName("Prometheus 格式应正确生成")
    void formatAsPrometheus_shouldGenerateCorrectFormat() {
        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "jairouter_requests_total",
                "counter",
                12345.0,
                Map.of("service", "chat", "status", "200"),
                1719123456789L
        );

        String result = invokeFormatAsPrometheus(List.of(data));

        assertTrue(result.contains("jairouter_requests_total"));
        assertTrue(result.contains("service=\"chat\""));
        assertTrue(result.contains("status=\"200\""));
        assertTrue(result.contains("12345.0"));
        assertTrue(result.contains("1719123456789"));
    }

    @Test
    @DisplayName("Prometheus 格式无标签指标应正确处理")
    void formatAsPrometheus_noTags_shouldHandleCorrectly() {
        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "simple_metric",
                "gauge",
                42.0,
                Map.of(),
                1719123456789L
        );

        String result = invokeFormatAsPrometheus(List.of(data));

        assertTrue(result.startsWith("simple_metric 42.0"));
    }

    // ==================== MetricData 测试 ====================

    @Test
    @DisplayName("MetricData toString 应包含所有字段")
    void metricDataToString_shouldContainAllFields() {
        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "test.metric",
                "counter",
                100.0,
                Map.of("key", "value"),
                1719123456789L
        );

        String str = data.toString();

        assertTrue(str.contains("test.metric"));
        assertTrue(str.contains("counter"));
        assertTrue(str.contains("100"));
    }

    @Test
    @DisplayName("MetricData getters 应返回正确值")
    void metricDataGetters_shouldReturnCorrectValues() {
        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "test.metric",
                "gauge",
                99.5,
                Map.of("env", "prod"),
                1719123456789L
        );

        assertEquals("test.metric", data.getName());
        assertEquals("gauge", data.getType());
        assertEquals(99.5, data.getValue(), 0.001);
        assertEquals(1, data.getTags().size());
        assertEquals("prod", data.getTags().get("env"));
        assertEquals(1719123456789L, data.getTimestamp());
    }

    // ==================== 文件上报测试 ====================

    @Test
    @DisplayName("文件上报禁用时应返回 false")
    void reportToFile_disabled_shouldReturnFalse() {
        ReflectionTestUtils.setField(reporter, "fileEnabled", false);

        boolean result = invokeReportToFile(List.of());

        assertFalse(result);
    }

    @Test
    @DisplayName("文件上报应创建文件")
    void reportToFile_enabled_shouldCreateFile() throws Exception {
        ReflectionTestUtils.setField(reporter, "fileEnabled", true);
        ReflectionTestUtils.setField(reporter, "filePath", tempDir.toString());
        ReflectionTestUtils.setField(reporter, "fileFormat", "json");

        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "test.metric",
                "counter",
                100.0,
                Map.of(),
                System.currentTimeMillis()
        );

        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.FILE);

        // 直接调用私有方法（通过反射或公开方法）
        boolean result = invokeReportToFile(List.of(data));

        assertTrue(result);
        assertTrue(Files.list(tempDir).findAny().isPresent());
    }

    @Test
    @DisplayName("Prometheus 格式文件上报应正确写入")
    void reportToFile_prometheusFormat_shouldWriteCorrectly() throws Exception {
        ReflectionTestUtils.setField(reporter, "fileEnabled", true);
        ReflectionTestUtils.setField(reporter, "filePath", tempDir.toString());
        ReflectionTestUtils.setField(reporter, "fileFormat", "prometheus");

        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "test_metric",
                "counter",
                50.0,
                Map.of("label", "value"),
                1719123456789L
        );

        boolean result = invokeReportToFile(List.of(data));

        assertTrue(result);
        Path writtenFile = Files.list(tempDir).findFirst().orElseThrow();
        String content = Files.readString(writtenFile);
        assertTrue(content.contains("test_metric"));
        assertTrue(content.contains("50.0"));
    }

    @Test
    @DisplayName("文件大小滚动应创建新文件")
    void findAvailableFile_shouldRollWhenSizeExceeded() throws Exception {
        ReflectionTestUtils.setField(reporter, "fileEnabled", true);
        ReflectionTestUtils.setField(reporter, "filePath", tempDir.toString());
        ReflectionTestUtils.setField(reporter, "fileFormat", "json");
        ReflectionTestUtils.setField(reporter, "fileMaxSizeMb", 1); // 1MB

        // 创建一个大文件
        String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(Instant.now().atZone(java.time.ZoneId.systemDefault()));
        Path baseFile = tempDir.resolve("metrics-" + dateStr + ".json");
        String largeContent = "x".repeat(1024 * 1024 + 1); // > 1MB
        Files.writeString(baseFile, largeContent);

        MetricsBatchReporter.MetricData data = new MetricsBatchReporter.MetricData(
                "test.metric", "counter", 1.0, Map.of(), System.currentTimeMillis()
        );

        boolean result = invokeReportToFile(List.of(data));

        assertTrue(result);
        // 应该创建了带序号的新文件
        assertTrue(Files.exists(tempDir.resolve("metrics-" + dateStr + "-1.json")));
    }

    // ==================== HTTP 上报测试 ====================

    @Test
    @DisplayName("HTTP 上报禁用时应返回 false")
    void reportToHttp_disabled_shouldReturnFalse() {
        ReflectionTestUtils.setField(reporter, "httpEnabled", false);

        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.HTTP);
        boolean result = invokeReportToHttp(List.of());

        assertFalse(result);
    }

    @Test
    @DisplayName("HTTP 上报无 URL 时应返回 false")
    void reportToHttp_noUrl_shouldReturnFalse() {
        ReflectionTestUtils.setField(reporter, "httpEnabled", true);
        ReflectionTestUtils.setField(reporter, "httpEndpointUrl", "");

        reporter.setReportTarget(MetricsBatchReporter.ReportTarget.HTTP);
        boolean result = invokeReportToHttp(List.of());

        assertFalse(result);
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射调用 formatAsPrometheus 方法
     */
    private String invokeFormatAsPrometheus(List<MetricsBatchReporter.MetricData> batch) {
        try {
            var method = MetricsBatchReporter.class.getDeclaredMethod("formatAsPrometheus", List.class);
            method.setAccessible(true);
            return (String) method.invoke(reporter, batch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke formatAsPrometheus", e);
        }
    }

    /**
     * 通过反射调用 reportToFile 方法
     */
    private boolean invokeReportToFile(List<MetricsBatchReporter.MetricData> batch) {
        try {
            var method = MetricsBatchReporter.class.getDeclaredMethod("reportToFile", List.class);
            method.setAccessible(true);
            return (boolean) method.invoke(reporter, batch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke reportToFile", e);
        }
    }

    /**
     * 通过反射调用 reportToHttp 方法
     */
    private boolean invokeReportToHttp(List<MetricsBatchReporter.MetricData> batch) {
        try {
            var method = MetricsBatchReporter.class.getDeclaredMethod("reportToHttp", List.class);
            method.setAccessible(true);
            return (boolean) method.invoke(reporter, batch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke reportToHttp", e);
        }
    }
}
