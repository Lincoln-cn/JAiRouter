package org.unreal.modelrouter.monitor.tracing.memory.model;

import lombok.Data;

/**
 * 内存统计信息
 */
@Data
public class MemoryStats {
    private final long usedHeap;
    private final long maxHeap;
    private final long totalMemoryUsed;
    private final int cacheSize;
    private final int spanCacheCount;
    private final long cacheHits;
    private final long cacheMisses;
    private final long evictionCount;
    private final long gcCount;
    private final MemoryPressureLevel pressureLevel;

    public double getHitRatio() {
        long total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0.0;
    }

    public double getHeapUsageRatio() {
        return maxHeap > 0 ? (double) usedHeap / maxHeap : 0.0;
    }
}
