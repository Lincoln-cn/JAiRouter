package org.unreal.modelrouter.router.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.RateLimitMetricsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流器监控控制器
 * 提供限流器状态和指标的查询接口
 *
 * @author JAiRouter Team
 * @since v2.10.0
 */
@Slf4j
@RestController
@RequestMapping("/api/rate-limiter")
@RequiredArgsConstructor
public class RateLimitMonitorController {

    private final RateLimitManager rateLimitManager;
    private final RateLimitMetricsService rateLimitMetricsService;

    /**
     * 获取所有限流器状态概览
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllRateLimiterStatus() {
        log.debug("Getting all rate limiter status");
        Map<String, Object> status = rateLimitManager.getAllRateLimiterStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取限流器详细指标
     * 包括剩余容量、使用率等
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<RateLimiterMetricsVO>> getRateLimiterMetrics() {
        log.debug("Getting rate limiter metrics");
        List<RateLimitManager.RateLimiterMetrics> metrics = rateLimitManager.getRateLimiterMetrics();

        List<RateLimiterMetricsVO> voList = metrics.stream()
                .map(this::toVO)
                .toList();

        return ResponseEntity.ok(voList);
    }

    /**
     * 获取限流器统计摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<RateLimiterSummaryVO> getRateLimiterSummary() {
        log.debug("Getting rate limiter summary");
        List<RateLimitManager.RateLimiterMetrics> metrics = rateLimitManager.getRateLimiterMetrics();

        int totalLimiters = metrics.size();
        int globalCount = (int) metrics.stream().filter(m -> "global".equals(m.getScope())).count();
        int serviceCount = (int) metrics.stream().filter(m -> "service".equals(m.getScope())).count();
        int instanceCount = (int) metrics.stream().filter(m -> "instance".equals(m.getScope())).count();

        // 计算平均使用率
        double avgUsageRatio = metrics.stream()
                .filter(m -> m.getUsageRatio() >= 0)
                .mapToDouble(RateLimitManager.RateLimiterMetrics::getUsageRatio)
                .average()
                .orElse(0.0);

        // 计算高使用率（>80%）的限流器数量
        long highUsageCount = metrics.stream()
                .filter(m -> m.getUsageRatio() > 0.8)
                .count();

        RateLimiterSummaryVO summary = new RateLimiterSummaryVO();
        summary.setTotalLimiters(totalLimiters);
        summary.setGlobalLimiters(globalCount);
        summary.setServiceLimiters(serviceCount);
        summary.setInstanceLimiters(instanceCount);
        summary.setAverageUsageRatio(Math.round(avgUsageRatio * 10000) / 100.0); // 百分比，保留两位小数
        summary.setHighUsageLimiters((int) highUsageCount);

        return ResponseEntity.ok(summary);
    }

    /**
     * 获取 Prometheus 指标信息
     */
    @GetMapping("/prometheus-info")
    public ResponseEntity<Map<String, Object>> getPrometheusInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("metricsCount", rateLimitMetricsService.getMetricsCount());
        info.put("availableMetrics", List.of(
                "jairouter_rate_limit_events_total{service, algorithm, result}",
                "jairouter_rate_limit_remaining{service, scope, algorithm}",
                "jairouter_rate_limit_usage_ratio{service, scope, algorithm}"
        ));
        info.put("collectionInterval", "10s");
        return ResponseEntity.ok(info);
    }

    /**
     * 转换为 VO
     */
    private RateLimiterMetricsVO toVO(final RateLimitManager.RateLimiterMetrics metrics) {
        RateLimiterMetricsVO vo = new RateLimiterMetricsVO();
        vo.setService(metrics.getService());
        vo.setScope(metrics.getScope());
        vo.setIdentifier(metrics.getIdentifier());
        vo.setAlgorithm(metrics.getAlgorithm());
        vo.setRemainingCapacity(metrics.getRemainingCapacity());
        vo.setUsageRatio(Math.round(metrics.getUsageRatio() * 10000) / 100.0); // 百分比
        vo.setCapacity(metrics.getCapacity());
        vo.setRate(metrics.getRate());
        return vo;
    }

    /**
     * 限流器指标 VO
     */
    @lombok.Data
    public static class RateLimiterMetricsVO {
        private String service;
        private String scope;
        private String identifier;
        private String algorithm;
        private long remainingCapacity;
        private double usageRatio; // 百分比 (0-100)
        private long capacity;
        private long rate;
    }

    /**
     * 限流器统计摘要 VO
     */
    @lombok.Data
    public static class RateLimiterSummaryVO {
        private int totalLimiters;
        private int globalLimiters;
        private int serviceLimiters;
        private int instanceLimiters;
        private double averageUsageRatio; // 百分比
        private int highUsageLimiters;
    }
}
