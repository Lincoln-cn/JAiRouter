package org.unreal.modelrouter.monitor.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 仪表盘业务指标控制器
 * 提供真实的业务监控数据，而非监控系统自身的状态
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardMetricsController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardMetricsController.class);

    private final MeterRegistry meterRegistry;

    public DashboardMetricsController(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取仪表盘真实业务指标
     */
    @GetMapping("/metrics")
    public Mono<RouterResponse<Map<String, Object>>> getDashboardMetrics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> metrics = new HashMap<>();

            // JVM 内存指标
            metrics.put("jvm", getJvmMetrics());

            // HTTP 请求指标
            metrics.put("http", getHttpMetrics());

            // 安全认证指标
            metrics.put("security", getSecurityMetrics());

            // 审计指标
            metrics.put("audit", getAuditMetrics());

            // 系统指标
            metrics.put("system", getSystemMetrics());

            // 监控系统自身指标
            metrics.put("monitoring", getMonitoringMetrics());

            return RouterResponse.success(metrics);
        }).onErrorResume(e -> {
            logger.error("获取仪表盘指标失败: {}", e.getMessage());
            return Mono.just(RouterResponse.<Map<String, Object>>error("获取指标失败: " + e.getMessage()));
        });
    }

    private Map<String, Object> getJvmMetrics() {
        Map<String, Object> jvm = new HashMap<>();

        // 内存使用
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedHeap = memoryBean.getHeapMemoryUsage().getUsed();
        long maxHeap = memoryBean.getHeapMemoryUsage().getMax();
        long committedHeap = memoryBean.getHeapMemoryUsage().getCommitted();

        jvm.put("heapUsedMB", usedHeap / (1024 * 1024));
        jvm.put("heapMaxMB", maxHeap / (1024 * 1024));
        jvm.put("heapCommittedMB", committedHeap / (1024 * 1024));
        jvm.put("heapUsagePercent", maxHeap > 0 ? Math.round(100.0 * usedHeap / maxHeap) : 0);

        // 线程
        jvm.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("daemonThreadCount", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        jvm.put("peakThreadCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());

        // 类加载
        jvm.put("loadedClassCount", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        jvm.put("totalLoadedClassCount", ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());

        // GC
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            Map<String, Object> gcInfo = new HashMap<>();
            gcInfo.put("collectionCount", gc.getCollectionCount());
            gcInfo.put("collectionTimeMs", gc.getCollectionTime());
            jvm.put(gc.getName().replaceAll("[^a-zA-Z0-9]", "_"), gcInfo);
        });

        return jvm;
    }

    private Map<String, Object> getHttpMetrics() {
        Map<String, Object> http = new HashMap<>();

        // HTTP 请求统计
        try {
            Timer totalTimer = meterRegistry.find("http.server.requests").timer();
            if (totalTimer != null) {
                http.put("totalRequests", totalTimer.count());
                http.put("avgResponseTimeMs", Math.round(totalTimer.mean(TimeUnit.MILLISECONDS) * 100) / 100.0);
                http.put("maxResponseTimeMs", Math.round(totalTimer.max(TimeUnit.MILLISECONDS)));
            } else {
                http.put("totalRequests", 0L);
                http.put("avgResponseTimeMs", 0.0);
                http.put("maxResponseTimeMs", 0L);
            }

            // 活跃请求数
            http.put("activeRequests", getGaugeValue("http.server.requests.active"));

        } catch (Exception e) {
            logger.debug("获取HTTP指标失败: {}", e.getMessage());
            http.put("totalRequests", 0L);
            http.put("avgResponseTimeMs", 0.0);
        }

        return http;
    }

    private Map<String, Object> getSecurityMetrics() {
        Map<String, Object> security = new HashMap<>();

        // 认证统计
        security.put("authSuccesses", getCounterValue("jairouter.security.authentication.successes.total"));
        security.put("authFailures", getCounterValue("jairouter.security.authentication.failures.total"));
        security.put("authAttempts", getCounterValue("jairouter.security.authentication.attempts.total"));
        security.put("activeUsers", getGaugeValue("jairouter.security.active.users"));
        security.put("activeApiKeys", getGaugeValue("jairouter.security.apikeys.active"));

        // JWT 统计
        security.put("jwtValidations", getCounterValue("jairouter.security.jwt.validations.total"));
        security.put("jwtRefreshes", getCounterValue("jairouter.security.jwt.refreshes.total"));
        security.put("jwtExpired", getCounterValue("jairouter.security.jwt.expired.total"));

        // 安全缓存
        security.put("cacheHits", getCounterValue("jairouter.security.cache.hits.total"));
        security.put("cacheMisses", getCounterValue("jairouter.security.cache.misses.total"));
        security.put("cacheSize", getGaugeValue("jairouter.security.cache.size"));

        return security;
    }

    private Map<String, Object> getAuditMetrics() {
        Map<String, Object> audit = new HashMap<>();

        audit.put("totalEvents", getCounterValue("jairouter.audit.events.total"));
        audit.put("successEvents", getCounterValue("jairouter.audit.events.success.total"));
        audit.put("failureEvents", getCounterValue("jairouter.audit.events.failure.total"));
        audit.put("bufferSize", getGaugeValue("jairouter.audit.buffer.size"));
        audit.put("healthScore", getGaugeValue("jairouter.audit.health.score"));

        return audit;
    }

    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> system = new HashMap<>();

        // CPU
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        system.put("availableProcessors", osBean.getAvailableProcessors());
        system.put("systemLoadAverage", osBean.getSystemLoadAverage());

        // 进程 CPU 使用率
        system.put("processCpuUsage", getGaugeValue("process.cpu.usage"));
        system.put("systemCpuUsage", getGaugeValue("system.cpu.usage"));

        // 运行时间
        system.put("uptimeSeconds", getGaugeValue("process.uptime"));
        system.put("startTime", getGaugeValue("process.start.time"));

        // 文件描述符
        system.put("openFiles", getGaugeValue("process.files.open.files"));
        system.put("maxFiles", getGaugeValue("process.files.max.files"));

        return system;
    }

    private Map<String, Object> getMonitoringMetrics() {
        Map<String, Object> monitoring = new HashMap<>();

        // 监控系统缓存（注意：这是监控系统自身的缓存）
        monitoring.put("cacheHits", getCounterValue("jairouter.metrics.cache.hits.total"));
        monitoring.put("cacheMisses", getCounterValue("jairouter.metrics.cache.misses.total"));
        monitoring.put("cacheSize", getGaugeValue("jairouter.metrics.cache.size"));

        // 重试统计
        monitoring.put("retryAttempts", getCounterValue("jairouter.metrics.retry.attempts.total"));
        monitoring.put("retrySuccesses", getCounterValue("jairouter.metrics.retry.successes.total"));
        monitoring.put("retryFailures", getCounterValue("jairouter.metrics.retry.failures.total"));
        monitoring.put("retryActive", getGaugeValue("jairouter.metrics.retry.active"));

        // 降级统计
        monitoring.put("degradationLevel", getGaugeValue("jairouter.metrics.degradation.level"));
        monitoring.put("degradations", getCounterValue("jairouter.metrics.degradations.total"));

        // 错误统计
        monitoring.put("errorsTotal", getCounterValue("jairouter.metrics.errors.total"));

        return monitoring;
    }

    private double getGaugeValue(final String name) {
        try {
            return meterRegistry.get(name).gauge().value();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getCounterValue(final String name) {
        try {
            return meterRegistry.get(name).counter().count();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
