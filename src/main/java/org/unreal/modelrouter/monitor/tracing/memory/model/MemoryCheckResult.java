package org.unreal.modelrouter.monitor.tracing.memory.model;

import lombok.Data;

/**
 * 内存检查结果
 */
@Data
public class MemoryCheckResult {
    private final long usedHeap;
    private final long maxHeap;
    private final double usageRatio;
    private final MemoryPressureLevel pressureLevel;
    private final int cacheSize;
    private final int spanCacheCount;
    private final long totalMemoryUsed;
}
