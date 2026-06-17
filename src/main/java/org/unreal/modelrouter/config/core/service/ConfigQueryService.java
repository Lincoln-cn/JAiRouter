package org.unreal.modelrouter.config.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.TraceConfig;
import org.unreal.modelrouter.config.core.manager.InstanceManager;
import org.unreal.modelrouter.common.util.InstanceIdUtils;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置查询服务
 *
 * 提供配置查询功能，包括获取所有配置、可用服务类型、可用模型等。
 * 从 ConfigurationService 提取，实现单一职责原则。
 *
 * @since v2.6.14
 */
@Service
public class ConfigQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigQueryService.class);

    private final ConfigMergeService configMergeService;
    private final InstanceManager instanceManager;

    @Autowired(required = false)
    private ServiceInstanceRepository serviceInstanceRepository;

    @Autowired
    public ConfigQueryService(final ConfigMergeService configMergeService,
                              final InstanceManager instanceManager) {
        this.configMergeService = configMergeService;
        this.instanceManager = instanceManager;
    }

    /**
     * 获取所有配置（合并后的最终配置）
     *
     * @return 完整配置Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAllConfigurations() {
        Map<String, Object> configs = configMergeService.getPersistedConfig();

        // 为每个实例添加instanceId和health属性
        if (configs != null && configs.containsKey("services")) {
            Map<String, Object> services = (Map<String, Object>) configs.get("services");
            for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                String serviceType = serviceEntry.getKey();
                Map<String, Object> serviceConfig = (Map<String, Object>) serviceEntry.getValue();
                if (serviceConfig != null && serviceConfig.containsKey("instances")) {
                    List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
                    for (Map<String, Object> instance : instances) {
                        if (instance != null && instance.containsKey("name") && instance.containsKey("baseUrl")) {
                            String name = (String) instance.get("name");
                            // 检查是否已存在instanceId，如果不存在才生成新的
                            String instanceId = null;
                            if (instance.containsKey("instanceId") && instance.get("instanceId") != null) {
                                instanceId = (String) instance.get("instanceId");
                            } else {
                                instanceId = InstanceIdUtils.getInstanceId(instance);
                                instance.put("instanceId", instanceId);
                            }

                            // 从数据库获取健康状态（优先使用 instanceId）
                            String healthStatus = getHealthStatusFromDatabase(name, instanceId);
                            if (healthStatus != null) {
                                instance.put("health", "HEALTHY".equals(healthStatus));
                                instance.put("healthStatus", healthStatus);
                            } else {
                                // 如果数据库中没有记录，默认为未知状态
                                instance.put("health", true);
                                instance.put("healthStatus", "UNKNOWN");
                            }
                        }
                    }
                }
            }
        }
        return configs;
    }

    /**
     * 从数据库获取实例的健康状态
     * v2.x: 优先使用 instanceId 查询，避免同名实例导致的重复记录错误
     *
     * @param instanceName 实例名称
     * @param instanceId 实例唯一ID (UUID)
     * @return 健康状态字符串 (HEALTHY, UNHEALTHY, UNKNOWN)，如果找不到返回 null
     */
    private String getHealthStatusFromDatabase(final String instanceName, final String instanceId) {
        if (serviceInstanceRepository == null) {
            return null;
        }
        try {
            // v2.x: 优先使用 instanceId 查询
            if (instanceId != null && !instanceId.isEmpty()) {
                return serviceInstanceRepository.findByInstanceId(instanceId)
                        .map(entity -> entity.getHealthStatus())
                        .orElse(null);
            }
            
            // v2.x: 如果 instanceId 为空，查询所有同名实例，取第一个的健康状态
            List<ServiceInstanceEntity> entities = serviceInstanceRepository.findAllByInstanceName(instanceName);
            if (!entities.isEmpty()) {
                return entities.get(0).getHealthStatus();
            }
            return null;
        } catch (Exception e) {
            logger.warn("从数据库获取实例健康状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有可用服务类型
     *
     * @return 服务类型列表
     */
    @SuppressWarnings("unchecked")
    public Set<String> getAvailableServiceTypes() {
        Map<String, Object> config = getAllConfigurations();
        Object servicesObj = config.get("services");
        if (servicesObj instanceof Map) {
            return ((Map<String, Object>) servicesObj).keySet();
        }
        return Set.of();
    }

    /**
     * 获取指定服务的所有可用模型名称
     *
     * @param serviceType 服务类型
     * @return 模型名称集合
     */
    public Set<String> getAvailableModels(final String serviceType) {
        List<Map<String, Object>> instances = instanceManager.getServiceInstancesAsMap(serviceType);
        return instances.stream()
                .map(instance -> (String) instance.get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取当前追踪配置
     *
     * @return TraceConfig对象
     */
    public TraceConfig getTraceConfig() {
        Map<String, Object> currentConfig = getAllConfigurations();
        return extractTraceConfig(currentConfig);
    }

    /**
     * 从配置Map中提取追踪配置
     *
     * @param config 配置Map
     * @return TraceConfig对象
     */
    @SuppressWarnings("unchecked")
    private TraceConfig extractTraceConfig(final Map<String, Object> config) {
        if (config.containsKey("trace")) {
            Map<String, Object> traceConfigMap = (Map<String, Object>) config.get("trace");
            return TraceConfig.fromMap(traceConfigMap);
        }
        return new TraceConfig(); // 返回默认配置
    }

    /**
     * 检查是否存在持久化配置
     *
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return configMergeService.hasPersistedConfig();
    }
}
