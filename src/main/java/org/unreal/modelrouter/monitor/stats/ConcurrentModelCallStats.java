package org.unreal.modelrouter.monitor.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * 并发模型调用统计
 *
 * 使用 LongAdder 和原子操作替代 synchronized 块，
 * 实现高并发场景下的低延迟统计更新。
 *
 * 相比 synchronized 方案：
 * - LongAdder 在高并发下性能提升 5-10 倍
 * - 无锁竞争，线程安全
 * - 适合写多读少的统计场景
 *
 * @author JAiRouter Team
 * @since 2.7.2
 */
public class ConcurrentModelCallStats {

    private final String modelName;
    private final String serviceType;

    // 使用 LongAdder 进行无锁计数
    private final LongAdder totalCalls = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder circuitBreakerCount = new LongAdder();
    private final LongAdder rateLimitCount = new LongAdder();

    // 响应时间统计
    private final LongAdder totalResponseTime = new LongAdder();
    private final DoubleAdder avgResponseTime = new DoubleAdder();

    // 使用 LongAccumulator 计算最小/最大值
    private final LongAccumulator minResponseTime = new LongAccumulator(Math::min, Long.MAX_VALUE);
    private final LongAccumulator maxResponseTime = new LongAccumulator(Math::max, 0);

    // 最后调用时间
    private volatile long lastCallTime = 0;

    // 统计开始时间
    private final long statsStartTime;

    // QPS 缓冲区
    private final CircularTimeBuffer qpsBuffer;

    // 错误码分布（需要同步保护）
    private final Map<String, LongAdder> errorCodeDistribution = new HashMap<>();

    /**
     * 创建并发统计对象
     *
     * @param modelName 模型名称
     * @param serviceType 服务类型
     */
    public ConcurrentModelCallStats(final String modelName, final String serviceType) {
        this.modelName = modelName;
        this.serviceType = serviceType;
        this.statsStartTime = System.currentTimeMillis();
        this.qpsBuffer = new CircularTimeBuffer(60000, 60); // 60秒窗口
    }

    /**
     * 更新统计信息
     *
     * @param success 是否成功
     * @param responseTime 响应时间（毫秒）
     */
    public void updateStats(final boolean success, final long responseTime) {
        // 更新计数器
        totalCalls.increment();
        if (success) {
            successCount.increment();
        } else {
            failureCount.increment();
        }

        // 更新响应时间统计
        totalResponseTime.add(responseTime);
        minResponseTime.accumulate(responseTime);
        maxResponseTime.accumulate(responseTime);

        // 使用移动平均更新平均响应时间
        long calls = totalCalls.sum();
        if (calls > 0) {
            avgResponseTime.add((responseTime - avgResponseTime.sum()) / calls);
        }

        // 更新时间戳
        lastCallTime = System.currentTimeMillis();

        // 更新 QPS 缓冲区
        qpsBuffer.record();
    }

    /**
     * 记录熔断事件
     */
    public void recordCircuitBreaker() {
        totalCalls.increment();
        circuitBreakerCount.increment();
        lastCallTime = System.currentTimeMillis();
        qpsBuffer.record();
    }

    /**
     * 记录限流事件
     */
    public void recordRateLimit() {
        totalCalls.increment();
        rateLimitCount.increment();
        lastCallTime = System.currentTimeMillis();
        qpsBuffer.record();
    }

    /**
     * 记录错误码
     *
     * @param errorCode 错误码
     */
    public synchronized void recordErrorCode(final String errorCode) {
        errorCodeDistribution
                .computeIfAbsent(errorCode, k -> new LongAdder())
                .increment();
    }

    // ========== Getters ==========

    public String getModelName() {
        return modelName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public long getTotalCalls() {
        return totalCalls.sum();
    }

    public long getSuccessCount() {
        return successCount.sum();
    }

    public long getFailureCount() {
        return failureCount.sum();
    }

    public long getCircuitBreakerCount() {
        return circuitBreakerCount.sum();
    }

    public long getRateLimitCount() {
        return rateLimitCount.sum();
    }

    public double getAvgResponseTime() {
        return avgResponseTime.sum();
    }

    public long getMinResponseTime() {
        long min = minResponseTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    public long getMaxResponseTime() {
        return maxResponseTime.get();
    }

    public long getLastCallTime() {
        return lastCallTime;
    }

    public long getStatsStartTime() {
        return statsStartTime;
    }

    /**
     * 计算成功率
     */
    public double calculateSuccessRate() {
        long calls = totalCalls.sum();
        if (calls == 0) {
            return 1.0;
        }
        return (double) successCount.sum() / calls;
    }

    /**
     * 计算失败率
     */
    public double calculateFailureRate() {
        long calls = totalCalls.sum();
        if (calls == 0) {
            return 0.0;
        }
        return (double) failureCount.sum() / calls;
    }

    /**
     * 计算熔断率
     */
    public double calculateCircuitBreakerRate() {
        long calls = totalCalls.sum();
        if (calls == 0) {
            return 0.0;
        }
        return (double) circuitBreakerCount.sum() / calls;
    }

    /**
     * 计算限流率
     */
    public double calculateRateLimitRate() {
        long calls = totalCalls.sum();
        if (calls == 0) {
            return 0.0;
        }
        return (double) rateLimitCount.sum() / calls;
    }

    /**
     * 获取当前 QPS
     */
    public double getCurrentQps() {
        return qpsBuffer.getQps();
    }

    /**
     * 判断健康状态
     */
    public String determineHealthStatus() {
        double successRate = calculateSuccessRate();
        double circuitBreakerRate = calculateCircuitBreakerRate();

        if (successRate >= 0.99 && circuitBreakerRate < 0.01) {
            return "HEALTHY";
        } else if (successRate >= 0.95 && circuitBreakerRate < 0.05) {
            return "DEGRADED";
        } else {
            return "UNHEALTHY";
        }
    }

    /**
     * 获取错误码分布
     */
    public synchronized Map<String, Long> getErrorCodeDistribution() {
        Map<String, Long> result = new HashMap<>();
        errorCodeDistribution.forEach((key, adder) -> result.put(key, adder.sum()));
        return result;
    }

    /**
     * 重置统计
     */
    public void reset() {
        totalCalls.reset();
        successCount.reset();
        failureCount.reset();
        circuitBreakerCount.reset();
        rateLimitCount.reset();
        totalResponseTime.reset();
        avgResponseTime.reset();
        minResponseTime.reset();
        maxResponseTime.reset();
        lastCallTime = 0;
        qpsBuffer.reset();

        synchronized (this) {
            errorCodeDistribution.clear();
        }
    }

    /**
     * 是否活跃（最近有调用）
     */
    public boolean isActive() {
        return lastCallTime > 0 && (System.currentTimeMillis() - lastCallTime) < 300000; // 5分钟内
    }
}
