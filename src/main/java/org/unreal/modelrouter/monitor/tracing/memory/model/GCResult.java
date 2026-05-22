package org.unreal.modelrouter.monitor.tracing.memory.model;

import lombok.Data;

/**
 * 垃圾回收结果
 */
@Data
public class GCResult {
    private final long beforeUsed;
    private final long afterUsed;
    private final long freedMemory;
    private final long gcTime;
}
