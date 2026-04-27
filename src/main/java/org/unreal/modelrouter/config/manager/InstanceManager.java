package org.unreal.modelrouter.config.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.config.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实例管理器 - v2.1.0 重构版
 * 
 * 负责服务实例的 CRUD 操作，使用强类型 DTO 替代 Map。
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */
@Component("configInstanceManager")
public class InstanceManager {

    private static final Logger logger = LoggerFactory.getLogger(InstanceManager.class);

    private final StoreManager storeManager;
    private final ConfigurationHelper configurationHelper;
    private final ConfigMergeService configMergeService;

    private static final String CURRENT_KEY = "model-router-config";

    // 实例更新锁，防止同一实例的并发更新
    private final ConcurrentHashMap<String, Object> instanceUpdateLocks = new ConcurrentHashMap<>();

    // 请求去重缓存
    private final ConcurrentHashMap<String, Long> recentUpdateRequests = new ConcurrentHashMap<>();
    private static final long REQUEST_DEDUP_WINDOW_MS = 1000;

    public InstanceManager(final StoreManager storeManager,
                           final ConfigurationHelper configurationHelper,
                           final ConfigMergeService configMergeService) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
    }

    /**
     * 获取服务的所有实例
     *
     * @param serviceType 服务类型
     * @return 实例列表
     */
    public List<ModelInstanceConfiguration> getServiceInstances(final String serviceType) {
        try {
            Map<String, Object> config = getCurrentConfig();
            if (config == null) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
            
            if (serviceConfig == null) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instancesMap = 
                (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());
            
            return ModelInstanceConfiguration.fromMapList(instancesMap);
        } catch (Exception e) {
            logger.error("获取服务实例失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取服务的所有实例（原始 Map 形式，保持向后兼容）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getServiceInstancesAsMap(final String serviceType) {
        try {
            Map<String, Object> config = getCurrentConfig();
            if (config == null) {
                return new ArrayList<>();
            }

            Map<String, Object> services = (Map<String, Object>) config.get("services");
            Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
            
            if (serviceConfig == null) {
                return new ArrayList<>();
            }

            return (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());
        } catch (Exception e) {
            logger.error("获取服务实例失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定实例的详情（强类型）
     *
     * @param serviceType 服务类型
     * @param instanceId 实例 ID
     * @return 实例配置，不存在返回 null
     */
    public ModelInstanceConfiguration getServiceInstance(final String serviceType, final String instanceId) {
        try {
            List<ModelInstanceConfiguration> instances = getServiceInstances(serviceType);
            
            for (ModelInstanceConfiguration instance : instances) {
                if (instanceId.equals(instance.instanceId()) || 
                    (instance.instanceId() == null && instanceId.equals(instance.name()))) {
                    return instance;
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.error("获取实例详情失败：serviceType={}, instanceId={}, error={}", 
                    serviceType, instanceId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 更新服务实例
     *
     * @param serviceType 服务类型
     * @param instanceId 实例 ID
     * @param instanceConfig 实例配置（强类型）
     */
    public void updateServiceInstance(final String serviceType, final String instanceId, 
                                      final ModelInstanceConfiguration instanceConfig) {
        if (instanceConfig == null) {
            throw new IllegalArgumentException("实例配置不能为空");
        }

        // 创建请求唯一标识，用于去重
        String requestKey = serviceType + ":" + instanceId + ":" +
                (instanceConfig.status() != null ? instanceConfig.status() : "null");

        // 检查是否为重复请求
        long currentTime = System.currentTimeMillis();
        Long lastRequestTime = recentUpdateRequests.get(requestKey);
        if (lastRequestTime != null && (currentTime - lastRequestTime) < REQUEST_DEDUP_WINDOW_MS) {
            logger.info("检测到重复的更新请求，忽略：serviceType={}, instanceId={}", serviceType, instanceId);
            return;
        }

        // 记录当前请求时间
        recentUpdateRequests.put(requestKey, currentTime);
        cleanupExpiredRequests(currentTime);

        // 获取实例级别的锁
        Object instanceLock = instanceUpdateLocks.computeIfAbsent(instanceId, k -> new Object());

        synchronized (instanceLock) {
            try {
                logger.info("更新服务 {} 的实例 {} - 线程：{}", serviceType, instanceId, Thread.currentThread().getName());
                updateServiceInstanceInternal(serviceType, instanceId, instanceConfig);
            } finally {
                // 清理实例锁
                instanceUpdateLocks.remove(instanceId, instanceLock);
            }
        }
    }

    /**
     * 更新服务实例（从 ModelRouterProperties.ModelInstance 转换）
     */
    public void updateServiceInstance(final String serviceType, final String instanceId,
                                      final ModelRouterProperties.ModelInstance instanceConfig) {
        if (instanceConfig == null) {
            throw new IllegalArgumentException("实例配置不能为空");
        }

        // 转换为强类型 DTO
        ModelInstanceConfiguration dto = new ModelInstanceConfiguration(
            instanceConfig.getName(),
            instanceConfig.getBaseUrl(),
            instanceConfig.getPath(),
            instanceConfig.getAdapter(),
            instanceConfig.getWeight(),
            instanceConfig.getStatus(),
            null, // rateLimit
            null, // circuitBreaker
            null, // fallback
            instanceConfig.getHeaders() != null ? instanceConfig.getHeaders() : Map.of(),
            instanceId
        );

        updateServiceInstance(serviceType, instanceId, dto);
    }

    /**
     * 删除服务实例
     *
     * @param serviceType 服务类型
     * @param instanceId 实例 ID
     */
    public void deleteServiceInstance(final String serviceType, final String instanceId) {
        logger.info("删除服务 {} 的实例 {}", serviceType, instanceId);

        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("无效的服务类型：" + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = 
            (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        // 删除匹配的实例
        boolean removed = instances.removeIf(instance -> {
            String currentInstanceId = instance.containsKey("instanceId") ? 
                (String) instance.get("instanceId") : (String) instance.get("name");
            return instanceId.equals(currentInstanceId);
        });

        if (!removed) {
            throw new IllegalArgumentException("实例不存在：" + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "deleteInstance");
        metadata.put("operationDetail", "删除服务实例：" + instanceId);
        metadata.put("serviceType", serviceType);
        metadata.put("instanceId", instanceId);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        saveConfig(currentConfig);

        logger.info("实例 {} 删除成功", instanceId);
    }

    /**
     * 批量更新服务实例
     *
     * @param serviceType 服务类型
     * @param operations 批量操作
     */
    public void batchUpdateServiceInstances(final String serviceType, final List<InstanceOperation> operations) {
        logger.info("批量更新服务 {} 的实例，共 {} 个操作", serviceType, operations.size());

        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("无效的服务类型：" + serviceType);
        }

        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("批量操作不能为空");
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = 
            (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        List<String> operationDetails = new ArrayList<>();

        // 执行批量操作
        for (InstanceOperation operation : operations) {
            switch (operation.getType()) {
                case UPDATE:
                    updateInstanceInList(instances, operation.getInstanceId(), 
                            operation.getInstanceConfig(), operationDetails);
                    break;
                case DELETE:
                    deleteInstanceFromList(instances, operation.getInstanceId(), operationDetails);
                    break;
                case ADD:
                    addInstanceToList(instances, operation.getInstanceConfig(), operationDetails);
                    break;
            }
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "batchUpdateInstances");
        metadata.put("operationDetail", "批量更新实例：" + String.join(", ", operationDetails));
        metadata.put("serviceType", serviceType);
        metadata.put("operationCount", operations.size());
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        saveConfig(currentConfig);

        logger.info("批量实例更新成功，执行操作：{}", String.join(", ", operationDetails));
    }

    // ==================== 内部方法 ====================

    /**
     * 内部实例更新方法
     */
    @SuppressWarnings("unchecked")
    private void updateServiceInstanceInternal(final String serviceType, final String instanceId, 
                                                final ModelInstanceConfiguration instanceConfig) {

        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("无效的服务类型：" + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = 
            (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        boolean found = false;
        int targetIndex = -1;
        Map<String, Object> oldInstance = null;

        // 查找实例位置
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = instance.containsKey("instanceId") ? 
                (String) instance.get("instanceId") : (String) instance.get("name");
            if (instanceId.equals(currentInstanceId)) {
                targetIndex = i;
                oldInstance = instance;
                found = true;
                break;
            }
        }

        if (found) {
            // 合并配置
            Map<String, Object> newInstanceMap = mergeInstanceConfig(oldInstance, instanceConfig.toMap());
            instances.set(targetIndex, newInstanceMap);

            // 更新服务配置
            serviceConfig.put("instances", instances);
            services.put(serviceType, serviceConfig);
            currentConfig.put("services", services);

            // 添加版本元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("operation", "updateInstance");
            metadata.put("operationDetail", "更新服务实例：" + instanceConfig.name() + "@" + instanceConfig.baseUrl());
            metadata.put("serviceType", serviceType);
            metadata.put("instanceId", instanceId);
            metadata.put("instanceName", instanceConfig.name());
            metadata.put("instanceUrl", instanceConfig.baseUrl());
            metadata.put("timestamp", System.currentTimeMillis());
            currentConfig.put("_metadata", metadata);

            saveConfig(currentConfig);

            logger.info("实例 {} 更新成功", instanceId);
        } else {
            throw new IllegalArgumentException("实例不存在：" + instanceId);
        }
    }

    /**
     * 清理过期的请求记录
     */
    private void cleanupExpiredRequests(final long currentTime) {
        recentUpdateRequests.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > REQUEST_DEDUP_WINDOW_MS * 2);
    }

    /**
     * 获取当前配置
     */
    private Map<String, Object> getCurrentConfig() {
        return storeManager.getConfig(CURRENT_KEY);
    }

    /**
     * 保存配置
     */
    private void saveConfig(final Map<String, Object> config) {
        storeManager.saveConfig(CURRENT_KEY, config);
    }

    /**
     * 合并实例配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeInstanceConfig(final Map<String, Object> existing, 
                                                     final Map<String, Object> updates) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);

        if (updates != null) {
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String key = entry.getKey();
                Object newValue = entry.getValue();

                if (merged.containsKey(key)) {
                    Object oldValue = merged.get(key);
                    if (oldValue instanceof Map && newValue instanceof Map) {
                        merged.put(key, mergeInstanceConfig(
                                (Map<String, Object>) oldValue,
                                (Map<String, Object>) newValue));
                    } else {
                        merged.put(key, newValue);
                    }
                } else {
                    merged.put(key, newValue);
                }
            }
        }

        return merged;
    }

    /**
     * 在列表中更新实例
     */
    @SuppressWarnings("unchecked")
    private void updateInstanceInList(final List<Map<String, Object>> instances, final String instanceId,
                                       final ModelInstanceConfiguration instanceConfig,
                                       final List<String> operationDetails) {
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = instance.containsKey("instanceId") ? 
                (String) instance.get("instanceId") : (String) instance.get("name");
            if (instanceId.equals(currentInstanceId)) {
                Map<String, Object> merged = mergeInstanceConfig(instance, instanceConfig.toMap());
                instances.set(i, merged);
                operationDetails.add("UPDATE:" + instanceId);
                return;
            }
        }
        throw new IllegalArgumentException("实例不存在：" + instanceId);
    }

    /**
     * 从列表中删除实例
     */
    private void deleteInstanceFromList(final List<Map<String, Object>> instances, final String instanceId,
                                         final List<String> operationDetails) {
        boolean removed = instances.removeIf(instance -> {
            String currentInstanceId = instance.containsKey("instanceId") ? 
                (String) instance.get("instanceId") : (String) instance.get("name");
            return instanceId.equals(currentInstanceId);
        });
        if (removed) {
            operationDetails.add("DELETE:" + instanceId);
        } else {
            throw new IllegalArgumentException("实例不存在：" + instanceId);
        }
    }

    /**
     * 添加实例到列表
     */
    @SuppressWarnings("unchecked")
    private void addInstanceToList(final List<Map<String, Object>> instances,
                                    final ModelInstanceConfiguration instanceConfig,
                                    final List<String> operationDetails) {
        instances.add(instanceConfig.toMap());
        operationDetails.add("ADD:" + instanceConfig.name());
    }

    // ==================== 内部类 ====================

    /**
     * 实例操作类型
     */
    public enum OperationType {
        UPDATE,
        DELETE,
        ADD
    }

    /**
     * 实例操作
     */
    public static class InstanceOperation {
        private final OperationType type;
        private final String instanceId;
        private ModelInstanceConfiguration instanceConfig;

        public InstanceOperation(final OperationType type, final String instanceId) {
            this.type = type;
            this.instanceId = instanceId;
        }

        public InstanceOperation(final OperationType type, final String instanceId, 
                                  final ModelInstanceConfiguration instanceConfig) {
            this.type = type;
            this.instanceId = instanceId;
            this.instanceConfig = instanceConfig;
        }

        public OperationType getType() {
            return type;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public ModelInstanceConfiguration getInstanceConfig() {
            return instanceConfig;
        }

        public void setInstanceConfig(final ModelInstanceConfiguration instanceConfig) {
            this.instanceConfig = instanceConfig;
        }
    }
}
