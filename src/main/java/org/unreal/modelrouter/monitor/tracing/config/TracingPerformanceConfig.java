package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.time.Duration;

/**
 * 性能优化配置
 */
@Data
public class TracingPerformanceConfig {
    /**
     * 异步处理
     */
    private boolean asyncProcessing = true;

    /**
     * 线程池配置
     */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    /**
     * 缓冲区配置
     */
    private BufferConfig buffer = new BufferConfig();

    /**
     * 内存管理
     */
    private MemoryConfig memory = new MemoryConfig();

    /**
     * 批处理配置
     */
    private BatchConfig batch = new BatchConfig();

    @Data
    public static class ThreadPoolConfig {
        private int coreSize = 2;
        private int maxSize = 8;
        private int queueCapacity = 1000;
        private Duration keepAlive = Duration.ofSeconds(60);
        private String threadNamePrefix = "tracing-";
    }

    @Data
    public static class BufferConfig {
        private int size = 1024;
        private Duration flushInterval = Duration.ofSeconds(5);
        private Duration maxWaitTime = Duration.ofSeconds(30);
    }

    @Data
    public static class MemoryConfig {
        private int maxSpansInMemory = 10000;
        private int memoryLimitMb = 100;
        private Duration gcInterval = Duration.ofSeconds(60);
    }

    @Data
    public static class BatchConfig {
        private int size = 100;
        private Duration timeout = Duration.ofSeconds(5);
        private int maxConcurrentBatches = 3;
    }
}
