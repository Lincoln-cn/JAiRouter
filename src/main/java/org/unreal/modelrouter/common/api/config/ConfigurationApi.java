package org.unreal.modelrouter.common.api.config;

import java.util.List;
import java.util.Map;

/**
 * 配置管理接口 - 其他模块通过此接口访问配置服务。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口获取服务/实例配置</li>
 *   <li>monitor 模块通过此接口获取配置变更通知</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 config-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface ConfigurationApi {

    /**
     * 获取服务配置。
     *
     * @param serviceId 服务 ID
     * @return 服务配置，不存在时返回 null
     */
    ServiceConfigSnapshot getServiceConfig(String serviceId);

    /**
     * 获取实例配置。
     *
     * @param instanceId 实例 ID
     * @return 实例配置，不存在时返回 null
     */
    ServiceInstanceSnapshot getInstanceConfig(String instanceId);

    /**
     * 获取所有服务配置。
     *
     * @return 服务配置列表
     */
    List<ServiceConfigSnapshot> getAllServiceConfigs();

    /**
     * 获取服务下的所有实例。
     *
     * @param serviceId 服务 ID
     * @return 实例配置列表
     */
    List<ServiceInstanceSnapshot> getServiceInstances(String serviceId);

    /**
     * 获取配置版本信息。
     *
     * @param serviceId 服务 ID
     * @return 当前版本号
     */
    int getCurrentVersion(String serviceId);

    // === 内部 DTO ===

    /**
     * 服务配置快照。
     */
    class ServiceConfigSnapshot {
        private String serviceId;
        private String serviceType;
        private String name;
        private String description;
        private int currentVersion;
        private Map<String, Object> loadBalanceConfig;
        private Map<String, Object> circuitBreakerConfig;
        private Map<String, Object> rateLimitConfig;
        private Map<String, Object> metadata;

        public ServiceConfigSnapshot() {
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(int currentVersion) {
            this.currentVersion = currentVersion;
        }

        public Map<String, Object> getLoadBalanceConfig() {
            return loadBalanceConfig;
        }

        public void setLoadBalanceConfig(Map<String, Object> loadBalanceConfig) {
            this.loadBalanceConfig = loadBalanceConfig;
        }

        public Map<String, Object> getCircuitBreakerConfig() {
            return circuitBreakerConfig;
        }

        public void setCircuitBreakerConfig(Map<String, Object> circuitBreakerConfig) {
            this.circuitBreakerConfig = circuitBreakerConfig;
        }

        public Map<String, Object> getRateLimitConfig() {
            return rateLimitConfig;
        }

        public void setRateLimitConfig(Map<String, Object> rateLimitConfig) {
            this.rateLimitConfig = rateLimitConfig;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * 服务实例快照。
     */
    class ServiceInstanceSnapshot {
        private String instanceId;
        private String serviceId;
        private String baseUrl;
        private String status;
        private int weight;
        private String healthStatus;
        private Map<String, Object> instanceConfig;
        private Map<String, Object> metadata;

        public ServiceInstanceSnapshot() {
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public String getHealthStatus() {
            return healthStatus;
        }

        public void setHealthStatus(String healthStatus) {
            this.healthStatus = healthStatus;
        }

        public Map<String, Object> getInstanceConfig() {
            return instanceConfig;
        }

        public void setInstanceConfig(Map<String, Object> instanceConfig) {
            this.instanceConfig = instanceConfig;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}