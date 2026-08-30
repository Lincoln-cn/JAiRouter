package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组件特定配置
 */
@Data
public class TracingComponentConfig {
    private HttpConfig http = new HttpConfig();
    private DatabaseConfig database = new DatabaseConfig();
    private CacheConfig cache = new CacheConfig();
    private MessagingConfig messaging = new MessagingConfig();
    private LoadBalancerConfig loadBalancer = new LoadBalancerConfig();
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    @Data
    public static class HttpConfig {
        private boolean enabled = true;
        private boolean captureHeaders = true;
        private boolean captureBody = false;
        private int maxBodySize = 1024;
        private List<String> excludedPaths = new ArrayList<>();
    }

    @Data
    public static class DatabaseConfig {
        private boolean enabled = false;
        private boolean captureSql = false;
        private int maxSqlLength = 1000;
    }

    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private boolean captureKeys = false;
        private boolean captureValues = false;
    }

    @Data
    public static class MessagingConfig {
        private boolean enabled = false;
        private boolean captureHeaders = true;
        private boolean captureBody = false;
    }

    @Data
    public static class LoadBalancerConfig {
        private boolean enabled = true;
        private boolean captureStrategy = true;
        private boolean captureCandidates = true;
        private boolean captureSelection = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class RateLimiterConfig {
        private boolean enabled = true;
        private boolean captureAlgorithm = true;
        private boolean captureQuota = true;
        private boolean captureDecision = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private boolean captureState = true;
        private boolean captureStateChanges = true;
        private boolean captureStatistics = true;
        private boolean captureFailureRate = true;
    }
}
