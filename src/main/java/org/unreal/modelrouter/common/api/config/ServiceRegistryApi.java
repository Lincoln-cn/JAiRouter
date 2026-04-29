package org.unreal.modelrouter.common.api.config;

import java.util.List;

/**
 * 服务注册接口 - 其他模块通过此接口获取可用实例。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口选择目标实例</li>
 *   <li>monitor 模块通过此接口监控实例健康状态</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 config-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface ServiceRegistryApi {

    /**
     * 获取健康的实例列表。
     *
     * @param serviceId 服务 ID
     * @return 健康实例列表
     */
    List<ConfigurationApi.ServiceInstanceSnapshot> getHealthyInstances(String serviceId);

    /**
     * 获取所有实例列表。
     *
     * @param serviceId 服务 ID
     * @return 所有实例列表
     */
    List<ConfigurationApi.ServiceInstanceSnapshot> getAllInstances(String serviceId);

    /**
     * 更新实例健康状态。
     *
     * @param instanceId 实例 ID
     * @param healthy 是否健康
     */
    void updateInstanceHealth(String instanceId, boolean healthy);

    /**
     * 获取实例数量统计。
     *
     * @param serviceId 服务 ID
     * @return 实例数量（total, healthy, unhealthy）
     */
    InstanceCount getInstanceCount(String serviceId);

    // === 内部 DTO ===

    /**
     * 实例数量统计。
     */
    class InstanceCount {
        private int total;
        private int healthy;
        private int unhealthy;

        public InstanceCount() {
        }

        public InstanceCount(int total, int healthy, int unhealthy) {
            this.total = total;
            this.healthy = healthy;
            this.unhealthy = unhealthy;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getHealthy() {
            return healthy;
        }

        public void setHealthy(int healthy) {
            this.healthy = healthy;
        }

        public int getUnhealthy() {
            return unhealthy;
        }

        public void setUnhealthy(int unhealthy) {
            this.unhealthy = unhealthy;
        }
    }
}