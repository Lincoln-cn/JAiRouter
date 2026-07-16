package org.unreal.modelrouter.monitor.tracing.performance;

import lombok.Data;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TracingPerformanceMonitor 数据模型
 */
public class TracingPerformanceModels {

    public enum SystemHealth {
        HEALTHY, DEGRADED, UNHEALTHY
    }

    public enum BottleneckType {
        MEMORY, PROCESSING, OPERATION, SYSTEM
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    @Data
    public static class PerformanceSnapshot {
        private final Instant timestamp;
        private final long totalOperations;
        private final long slowOperations;
        private final double memoryUsage;
        private final double dropRate;
        private final double cpuUsage;
        private final double throughput;
    }

    @Data
    public static class OperationMetrics {
        private final String operation;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong totalLatency = new AtomicLong(0);
        private final AtomicLong maxLatency = new AtomicLong(0);
        private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);

        public OperationMetrics(final String operation) {
            this.operation = operation;
        }

        public void recordOperation(final long latency, final boolean success) {
            totalCount.incrementAndGet();
            if (success) {
                successCount.incrementAndGet();
            }
            totalLatency.addAndGet(latency);
            maxLatency.updateAndGet(current -> Math.max(current, latency));
            minLatency.updateAndGet(current -> Math.min(current, latency));
        }

        public double getAverageLatency() {
            long count = totalCount.get();
            return count > 0 ? (double) totalLatency.get() / count : 0.0;
        }

        public double getSuccessRate() {
            long count = totalCount.get();
            return count > 0 ? (double) successCount.get() / count : 0.0;
        }

        public long getP99Latency() {
            return (long) (getAverageLatency() * 1.5);
        }
    }

    @Data
    public static class PerformanceThreshold {
        private final long slowThreshold;
        private final long criticalThreshold;
    }

    @Data
    public static class PerformanceBottleneck {
        private final BottleneckType type;
        private final String description;
        private final Severity severity;
        private final List<OptimizationSuggestion> suggestions;
    }

    @Data
    public static class OptimizationSuggestion {
        private final String title;
        private final String description;
        private final Priority priority;
    }

    @Data
    public static class PerformanceIssue {
        private final String description;
        private final Severity severity;
        private final Instant detectedAt;
    }

    @Data
    public static class PerformanceReport {
        private final Instant timestamp;
        private final long totalOperations;
        private final long slowOperations;
        private final MemoryStats memoryStats;
        private final AsyncTracingProcessor.ProcessingStats processingStats;
        private final List<OperationMetrics> operationMetrics;
        private final SystemHealth systemHealth;
        private final List<PerformanceIssue> activeIssues;
    }

    @Data
    public static class TuningResult {
        private final List<String> appliedActions;
        private final List<String> failedActions;
    }
}
