package org.unreal.modelrouter.common.api.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 负载均衡状态快照 - 用于 persistence 存储，无业务逻辑依赖。
 *
 * <p>此 DTO 用于模块间通信，persistence 模块只存储此快照，
 * 不感知 LoadBalancer 业务组件。
 *
 * @since v2.8.0
 */
public class LoadBalancerSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务 ID */
    private String serviceId;

    /** 负载均衡策略：ROUND_ROBIN, WEIGHTED, LEAST_CONNECTIONS, CONSISTENT_HASH */
    private String strategy;

    /** 实例权重映射（instanceId -> weight） */
    private Map<String, Integer> weights;

    /** 实例连接数映射（instanceId -> connections） */
    private Map<String, Integer> connections;

    /** 最后选择的实例 */
    private String lastSelectedInstance;

    /** 选择计数（用于 round-robin） */
    private long selectionCount;

    /** 更新时间 */
    private Instant updateTime;

    /** 元数据（扩展字段） */
    private Map<String, Object> metadata;

    // === 构造函数 ===

    public LoadBalancerSnapshot() {
    }

    public LoadBalancerSnapshot(String serviceId, String strategy) {
        this.serviceId = serviceId;
        this.strategy = strategy;
    }

    // === Getter/Setter ===

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Map<String, Integer> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Integer> weights) {
        this.weights = weights;
    }

    public Map<String, Integer> getConnections() {
        return connections;
    }

    public void setConnections(Map<String, Integer> connections) {
        this.connections = connections;
    }

    public String getLastSelectedInstance() {
        return lastSelectedInstance;
    }

    public void setLastSelectedInstance(String lastSelectedInstance) {
        this.lastSelectedInstance = lastSelectedInstance;
    }

    public long getSelectionCount() {
        return selectionCount;
    }

    public void setSelectionCount(long selectionCount) {
        this.selectionCount = selectionCount;
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