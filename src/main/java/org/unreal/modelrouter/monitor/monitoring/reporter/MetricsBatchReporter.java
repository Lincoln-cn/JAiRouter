package org.unreal.modelrouter.monitor.monitoring.reporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标批量上报器
 *
 * 功能：
 * - 聚合 Micrometer 指标到内存缓冲区
 * - 定期批量上报到外部系统（可扩展：Prometheus Pushgateway, InfluxDB, Datadog）
 * - 减少 HTTP 请求次数，提升上报效率
 *
 * 性能提升：
 * - 上报次数减少：N 次/秒 → 1 次/分钟（批量）
 * - 网络开销降低：~95%（假设每秒 10 次请求 → 每分钟 1 次）
 * - 可配置批量大小和上报间隔
 *
 * @author JAiRouter Team
 * @since v2.7.12
 */
@Component
public class MetricsBatchReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsBatchReporter.class);

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    // 指标缓冲区（内存队列）
    private final ConcurrentLinkedQueue<MetricData> metricsBuffer = new ConcurrentLinkedQueue<>();

    // 批量大小限制
    private static final int MAX_BATCH_SIZE = 1000;

    // 上报间隔（毫秒）
    private static final long REPORT_INTERVAL_MS = 60000; // 1分钟

    // 统计信息
    private final AtomicLong totalMetricsCollected = new AtomicLong(0);
    private final AtomicLong totalBatchesSent = new AtomicLong(0);
    private final AtomicLong lastReportTime = new AtomicLong(System.currentTimeMillis());

    // 上报目标（可扩展）
    private volatile ReportTarget reportTarget = ReportTarget.LOG;

    // HTTP 上报配置
    @Value("${monitoring.metrics.export.http.enabled:false}")
    private boolean httpEnabled;
    @Value("${monitoring.metrics.export.http.url:}")
    private String httpEndpointUrl;
    @Value("${monitoring.metrics.export.http.timeout:5000}")
    private int httpTimeoutMs;
    @Value("${monitoring.metrics.export.http.format:prometheus}")
    private String httpFormat;

    // 文件上报配置
    @Value("${monitoring.metrics.export.file.enabled:false}")
    private boolean fileEnabled;
    @Value("${monitoring.metrics.export.file.path:./logs/metrics}")
    private String filePath;
    @Value("${monitoring.metrics.export.file.format:json}")
    private String fileFormat;
    @Value("${monitoring.metrics.export.file.max-size-mb:100}")
    private int fileMaxSizeMb;

    public MetricsBatchReporter(final MeterRegistry meterRegistry, final ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
        LOGGER.info("MetricsBatchReporter initialized with target: {}", reportTarget);
    }

    /**
     * 定时收集指标到缓冲区
     * 每 10 秒收集一次
     */
    @Scheduled(fixedRate = 10000)
    public void collectMetrics() {
        try {
            int collected = 0;
            for (Meter meter : meterRegistry.getMeters()) {
                MetricData data = extractMetricData(meter);
                if (data != null && metricsBuffer.size() < MAX_BATCH_SIZE) {
                    metricsBuffer.offer(data);
                    collected++;
                }
            }
            totalMetricsCollected.addAndGet(collected);
            if (collected > 0) {
                LOGGER.debug("Collected {} metrics to buffer, buffer size: {}", collected, metricsBuffer.size());
            }
        } catch (Exception e) {
            LOGGER.warn("Error collecting metrics: {}", e.getMessage());
        }
    }

    /**
     * 定时批量上报
     * 每分钟上报一次
     */
    @Scheduled(fixedRate = 60000)
    public void reportBatch() {
        if (metricsBuffer.isEmpty()) {
            return;
        }

        try {
            // 转换为批次
            List<MetricData> batch = new ArrayList<>();
            MetricData data;
            while ((data = metricsBuffer.poll()) != null) {
                batch.add(data);
            }

            if (batch.isEmpty()) {
                return;
            }

            // 上报到目标系统
            boolean success = reportToTarget(batch);
            if (success) {
                totalBatchesSent.incrementAndGet();
                lastReportTime.set(System.currentTimeMillis());
                LOGGER.info("Reported {} metrics in batch, total batches: {}", batch.size(), totalBatchesSent.get());
            }
        } catch (Exception e) {
            LOGGER.error("Error reporting metrics batch: {}", e.getMessage());
        }
    }

    /**
     * 上报到目标系统
     */
    private boolean reportToTarget(final List<MetricData> batch) {
        switch (reportTarget) {
            case LOG:
                return reportToLog(batch);
            case HTTP:
                return reportToHttp(batch);
            case FILE:
                return reportToFile(batch);
            default:
                LOGGER.warn("Unknown report target: {}", reportTarget);
                return false;
        }
    }

    /**
     * 上报到日志（默认）
     */
    private boolean reportToLog(final List<MetricData> batch) {
        try {
            // 按类型分组统计
            Map<String, Integer> typeCount = new HashMap<>();
            for (MetricData data : batch) {
                typeCount.merge(data.getType(), 1, Integer::sum);
            }

            LOGGER.info("Metrics Batch Report - Total: {}, Types: {}", batch.size(), typeCount);
            if (LOGGER.isDebugEnabled()) {
                for (MetricData data : batch) {
                    LOGGER.debug("  {}: {} [tags={}]", data.getName(), data.getValue(), data.getTags());
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Error logging metrics batch: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 上报到 HTTP 端点（可扩展：Prometheus Pushgateway, InfluxDB）
     */
    private boolean reportToHttp(final List<MetricData> batch) {
        if (!httpEnabled || httpEndpointUrl == null || httpEndpointUrl.isEmpty()) {
            LOGGER.debug("HTTP reporting disabled or no endpoint configured");
            return false;
        }

        try {
            String requestBody;
            MediaType contentType;

            // 根据格式选择不同的序列化方式
            if ("prometheus".equalsIgnoreCase(httpFormat)) {
                requestBody = formatAsPrometheus(batch);
                contentType = MediaType.TEXT_PLAIN;
            } else {
                requestBody = objectMapper.writeValueAsString(Map.of(
                        "metrics", batch,
                        "timestamp", Instant.now().toString(),
                        "source", "jairouter"
                ));
                contentType = MediaType.APPLICATION_JSON;
            }

            webClient.post()
                    .uri(httpEndpointUrl)
                    .contentType(contentType)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(httpTimeoutMs))
                    .block();

            LOGGER.debug("Reported {} metrics to HTTP endpoint: {}", batch.size(), httpEndpointUrl);
            return true;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing metrics for HTTP report: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("Error reporting metrics to HTTP endpoint {}: {}", httpEndpointUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 格式化为 Prometheus Pushgateway 格式
     */
    private String formatAsPrometheus(final List<MetricData> batch) {
        StringBuilder sb = new StringBuilder();
        for (MetricData data : batch) {
            // 格式: metric_name{tag1="value1",tag2="value2"} value
            sb.append(data.getName());
            if (!data.getTags().isEmpty()) {
                sb.append("{");
                boolean first = true;
                for (Map.Entry<String, String> tag : data.getTags().entrySet()) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(tag.getKey()).append("=\"").append(tag.getValue()).append("\"");
                    first = false;
                }
                sb.append("}");
            }
            sb.append(" ").append(data.getValue());
            sb.append(" ").append(data.getTimestamp());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 上报到文件
     */
    private boolean reportToFile(final List<MetricData> batch) {
        if (!fileEnabled || filePath == null || filePath.isEmpty()) {
            LOGGER.debug("File reporting disabled or no path configured");
            return false;
        }

        try {
            Path dirPath = Paths.get(filePath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String extension = "json".equalsIgnoreCase(fileFormat) ? "json" : "txt";
            String dateStr = DateTimeFormatter.ofPattern("yyyyMMdd")
                    .format(Instant.now().atZone(java.time.ZoneId.systemDefault()));

            // 生成内容
            String content;
            if ("json".equalsIgnoreCase(fileFormat)) {
                // JSON Lines 格式：每行一个 JSON 对象，便于追加和解析
                content = objectMapper.writeValueAsString(Map.of(
                        "metrics", batch,
                        "timestamp", Instant.now().toString(),
                        "source", "jairouter",
                        "count", batch.size()
                )) + "\n";
            } else {
                content = formatAsPrometheus(batch);
            }

            // 查找合适的文件（考虑文件大小限制和序号）
            long contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            Path file = findAvailableFile(dirPath, dateStr, extension, contentBytes);
            if (file == null) {
                LOGGER.error("No available metric file for date {}, all files are full", dateStr);
                return false;
            }

            if (Files.exists(file)) {
                Files.writeString(file, content, StandardOpenOption.APPEND);
            } else {
                Files.writeString(file, content, StandardOpenOption.CREATE);
            }
            LOGGER.debug("Reported {} metrics to file: {}", batch.size(), file);
            return true;
        } catch (IOException e) {
            LOGGER.error("Error writing metrics to file {}: {}", filePath, e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("Error reporting metrics to file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查找可用的文件（考虑文件大小限制）
     * 文件命名规则：metrics-YYYYMMDD.json 或 metrics-YYYYMMDD-N.json
     */
    private Path findAvailableFile(final Path dirPath, final String dateStr,
                                    final String extension, final long contentLength) throws IOException {
        long maxSizeBytes = (long) fileMaxSizeMb * 1024 * 1024;

        // 先检查基础文件名
        Path baseFile = dirPath.resolve("metrics-" + dateStr + "." + extension);
        if (!Files.exists(baseFile)) {
            return baseFile;
        }

        long currentSize = Files.size(baseFile);
        if (currentSize + contentLength <= maxSizeBytes) {
            return baseFile;
        }

        // 文件已满，查找下一个可用序号
        int index = 1;
        while (true) {
            if (index > 1000) {
                LOGGER.error("All metric files for date {} are full, cannot write batch of {} bytes",
                        dateStr, contentLength);
                return null;
            }
            Path indexedFile = dirPath.resolve("metrics-" + dateStr + "-" + index + "." + extension);
            if (!Files.exists(indexedFile)) {
                return indexedFile;
            }
            currentSize = Files.size(indexedFile);
            if (currentSize + contentLength <= maxSizeBytes) {
                return indexedFile;
            }
            index++;
        }
    }

    /**
     * 从 Meter 提取指标数据
     */
    private MetricData extractMetricData(final Meter meter) {
        try {
            String name = meter.getId().getName();
            String type = meter.getId().getType().name().toLowerCase();
            Map<String, String> tags = new HashMap<>();
            meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));

            double value = 0.0;
            if (meter instanceof Counter) {
                value = ((Counter) meter).count();
            } else if (meter instanceof Gauge) {
                value = ((Gauge) meter).value();
            } else if (meter instanceof Timer) {
                value = ((Timer) meter).mean(java.util.concurrent.TimeUnit.MILLISECONDS);
                type = "timer_mean_ms";
            } else {
                // 其他类型跳过
                return null;
            }

            return new MetricData(name, type, value, tags, System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.debug("Error extracting metric data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置上报目标
     */
    public void setReportTarget(final ReportTarget target) {
        this.reportTarget = target;
        LOGGER.info("Report target changed to: {}", target);
    }

    /**
     * 获取统计信息
     */
    public ReporterStats getStats() {
        return new ReporterStats(
                totalMetricsCollected.get(),
                totalBatchesSent.get(),
                metricsBuffer.size(),
                lastReportTime.get(),
                reportTarget.name()
        );
    }

    /**
     * 清空缓冲区
     */
    public void clearBuffer() {
        metricsBuffer.clear();
        LOGGER.info("Metrics buffer cleared");
    }

    /**
     * 指标数据
     */
    public static class MetricData {
        private final String name;
        private final String type;
        private final double value;
        private final Map<String, String> tags;
        private final long timestamp;

        public MetricData(final String name, final String type, final double value,
                          final Map<String, String> tags, final long timestamp) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.tags = tags;
            this.timestamp = timestamp;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public double getValue() {
            return value;
        }

        public Map<String, String> getTags() {
            return tags;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("MetricData{name='%s', type='%s', value=%.2f, tags=%s}",
                    name, type, value, tags);
        }
    }

    /**
     * 上报目标
     */
    public enum ReportTarget {
        LOG,    // 日志输出（默认）
        HTTP,   // HTTP 端点（可扩展）
        FILE    // 文件输出
    }

    /**
     * 上报器统计信息
     */
    public static class ReporterStats {
        private final long totalMetricsCollected;
        private final long totalBatchesSent;
        private final int bufferSize;
        private final long lastReportTime;
        private final String reportTarget;

        public ReporterStats(final long totalMetricsCollected, final long totalBatchesSent,
                             final int bufferSize, final long lastReportTime, final String reportTarget) {
            this.totalMetricsCollected = totalMetricsCollected;
            this.totalBatchesSent = totalBatchesSent;
            this.bufferSize = bufferSize;
            this.lastReportTime = lastReportTime;
            this.reportTarget = reportTarget;
        }

        public long getTotalMetricsCollected() {
            return totalMetricsCollected;
        }

        public long getTotalBatchesSent() {
            return totalBatchesSent;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public long getLastReportTime() {
            return lastReportTime;
        }

        public String getReportTarget() {
            return reportTarget;
        }

        @Override
        public String toString() {
            return String.format("ReporterStats{collected=%d, batches=%d, bufferSize=%d, target=%s}",
                    totalMetricsCollected, totalBatchesSent, bufferSize, reportTarget);
        }
    }
}
