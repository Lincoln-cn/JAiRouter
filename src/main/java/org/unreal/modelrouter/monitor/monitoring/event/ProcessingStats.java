package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 处理统计信息类
 */
public class ProcessingStats {
    private final long processedCount;
    private final long droppedCount;
    private final long queueSize;
    private final String circuitBreakerState;

    public ProcessingStats(final long processedCount, final long droppedCount, final long queueSize, final String circuitBreakerState) {
        this.processedCount = processedCount;
        this.droppedCount = droppedCount;
        this.queueSize = queueSize;
        this.circuitBreakerState = circuitBreakerState;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public long getDroppedCount() {
        return droppedCount;
    }

    public long getQueueSize() {
        return queueSize;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }
}
