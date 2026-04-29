package org.unreal.modelrouter.common.api.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 限流器状态快照 - 用于 persistence 存储，无业务逻辑依赖。
 *
 * <p>此 DTO 用于模块间通信，persistence 模块只存储此快照，
 * 不感知 RateLimiter 业务组件。
 *
 * @since v2.8.0
 */
public class RateLimiterSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例 ID */
    private String instanceId;

    /** 算法类型：TOKEN_BUCKET, LEAKY_BUCKET, SLIDING_WINDOW, FIXED_WINDOW */
    private String algorithm;

    /** 可用令牌数（TokenBucket） */
    private long availableTokens;

    /** 令牌容量 */
    private long capacity;

    /** 填充速率（tokens/second） */
    private double refillRate;

    /** 最后填充时间 */
    private Instant lastRefillTime;

    /** 请求数（计数器） */
    private long requestCount;

    /** 拒绝数 */
    private long rejectedCount;

    /** 窗口开始时间（SlidingWindow） */
    private Instant windowStartTime;

    /** 更新时间 */
    private Instant updateTime;

    /** 元数据（扩展字段） */
    private Map<String, Object> metadata;

    // === 构造函数 ===

    public RateLimiterSnapshot() {
    }

    public RateLimiterSnapshot(String instanceId, String algorithm) {
        this.instanceId = instanceId;
        this.algorithm = algorithm;
    }

    // === Getter/Setter ===

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getAvailableTokens() {
        return availableTokens;
    }

    public void setAvailableTokens(long availableTokens) {
        this.availableTokens = availableTokens;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public Instant getLastRefillTime() {
        return lastRefillTime;
    }

    public void setLastRefillTime(Instant lastRefillTime) {
        this.lastRefillTime = lastRefillTime;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public long getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(long rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public Instant getWindowStartTime() {
        return windowStartTime;
    }

    public void setWindowStartTime(Instant windowStartTime) {
        this.windowStartTime = windowStartTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}