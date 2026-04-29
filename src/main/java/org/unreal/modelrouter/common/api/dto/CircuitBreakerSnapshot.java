package org.unreal.modelrouter.common.api.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 熔断器状态快照 - 用于 persistence 存储，无业务逻辑依赖。
 *
 * <p>此 DTO 用于模块间通信，persistence 模块只存储此快照，
 * 不感知 CircuitBreaker 业务组件。
 *
 * @since v2.8.0
 */
public class CircuitBreakerSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例 ID */
    private String instanceId;

    /** 状态：CLOSED, OPEN, HALF_OPEN */
    private String state;

    /** 失败计数 */
    private int failureCount;

    /** 成功计数 */
    private int successCount;

    /** 最后失败时间 */
    private Instant lastFailureTime;

    /** 打开时间（OPEN 状态时） */
    private Instant openTime;

    /** 半开状态尝试次数 */
    private int halfOpenAttempts;

    /** 元数据（扩展字段） */
    private Map<String, Object> metadata;

    // === 构造函数 ===

    public CircuitBreakerSnapshot() {
    }

    public CircuitBreakerSnapshot(String instanceId, String state) {
        this.instanceId = instanceId;
        this.state = state;
    }

    // === Getter/Setter ===

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public Instant getLastFailureTime() {
        return lastFailureTime;
    }

    public void setLastFailureTime(Instant lastFailureTime) {
        this.lastFailureTime = lastFailureTime;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Instant openTime) {
        this.openTime = openTime;
    }

    public int getHalfOpenAttempts() {
        return halfOpenAttempts;
    }

    public void setHalfOpenAttempts(int halfOpenAttempts) {
        this.halfOpenAttempts = halfOpenAttempts;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // === 静态工厂方法 ===

    /** 创建 CLOSED 状态快照 */
    public static CircuitBreakerSnapshot closed(String instanceId) {
        return new CircuitBreakerSnapshot(instanceId, "CLOSED");
    }

    /** 创建 OPEN 状态快照 */
    public static CircuitBreakerSnapshot open(String instanceId, Instant openTime) {
        CircuitBreakerSnapshot snapshot = new CircuitBreakerSnapshot(instanceId, "OPEN");
        snapshot.setOpenTime(openTime);
        return snapshot;
    }

    /** 创建 HALF_OPEN 状态快照 */
    public static CircuitBreakerSnapshot halfOpen(String instanceId, int attempts) {
        CircuitBreakerSnapshot snapshot = new CircuitBreakerSnapshot(instanceId, "HALF_OPEN");
        snapshot.setHalfOpenAttempts(attempts);
        return snapshot;
    }
}