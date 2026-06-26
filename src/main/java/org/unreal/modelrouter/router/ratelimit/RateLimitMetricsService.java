package org.unreal.modelrouter.router.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.util.List;

/**
 * 限流器指标服务
 * 定期收集限流器状态并更新 Prometheus Gauge
 *
 * @author JAiRouter Team
 * @since v2.10.0
 */
@Service
public class RateLimitMetricsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitMetricsService.class);

    private final RateLimitManager rateLimitManager;
    private final MetricsCollector metricsCollector;

    @Autowired
    public RateLimitMetricsService(final RateLimitManager rateLimitManager,
                                    final MetricsCollector metricsCollector) {
        this.rateLimitManager = rateLimitManager;
        this.metricsCollector = metricsCollector;

        LOGGER.info("RateLimitMetricsService initialized");
    }

    /**
     * 定时收集限流器状态指标
     * 每 10 秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    public void collectRateLimitMetrics() {
        try {
            List<RateLimitManager.RateLimiterMetrics> metricsList = rateLimitManager.getRateLimiterMetrics();

            for (RateLimitManager.RateLimiterMetrics metrics : metricsList) {
                metricsCollector.recordRateLimitStatus(
                        metrics.getService(),
                        metrics.getScope(),
                        metrics.getAlgorithm(),
                        metrics.getRemainingCapacity(),
                        metrics.getUsageRatio()
                );
            }

            LOGGER.debug("Collected {} rate limiter metrics", metricsList.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to collect rate limit metrics: {}", e.getMessage());
        }
    }

    /**
     * 获取统计信息
     */
    public int getMetricsCount() {
        return rateLimitManager.getRateLimiterMetrics().size();
    }
}
