package org.unreal.modelrouter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.jpa.repository.InstanceRateLimitRepository;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置同步服务
 * v1.5.6: 将配置版本回滚时同步实例数据到数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigSyncService {

    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final InstanceRateLimitRepository instanceRateLimitRepository;
    private final InstanceCircuitBreakerRepository instanceCircuitBreakerRepository;

    /**
     * 将配置中的服务实例同步到数据库
     * 用于版本回滚时恢复实例状态
     *
     * @param config 配置Map
     */
    @Transactional
    public void syncInstancesToDatabase(Map<String, Object> config) {
        log.info("开始同步配置实例到数据库...");

        if (config == null || !config.containsKey("services")) {
            log.warn("配置中没有服务定义，跳过同步");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) config.get("services");
        if (services == null || services.isEmpty()) {
            log.warn("服务配置为空，跳过同步");
            return;
        }

        int addedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        // 获取所有服务类型
        List<String> configServiceTypes = new ArrayList<>(services.keySet());

        // 获取数据库中所有服务配置
        List<ServiceConfigEntity> existingConfigs = serviceConfigRepository.findAllByIsLatestTrue();
        List<String> existingServiceTypes = existingConfigs.stream()
                .map(ServiceConfigEntity::getServiceType)
                .toList();

        // 处理配置中的每个服务
        for (String serviceType : configServiceTypes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);

            if (serviceConfig == null) {
                continue;
            }

            // 获取或创建服务配置实体
            Optional<ServiceConfigEntity> configEntityOpt = existingConfigs.stream()
                    .filter(c -> c.getServiceType().equals(serviceType))
                    .findFirst();

            ServiceConfigEntity configEntity;
            if (configEntityOpt.isPresent()) {
                configEntity = configEntityOpt.get();
                // 更新服务配置属性
                updateServiceConfigFromMap(configEntity, serviceConfig);
                configEntity = serviceConfigRepository.save(configEntity);
            } else {
                // 创建新的服务配置
                configEntity = createServiceConfig(serviceType, serviceConfig);
                log.info("创建新的服务配置: {}", serviceType);
            }

            // 同步实例
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instancesConfig = (List<Map<String, Object>>) serviceConfig.get("instances");

            if (instancesConfig != null) {
                int[] counts = syncServiceInstances(configEntity.getId(), instancesConfig);
                addedCount += counts[0];
                updatedCount += counts[1];
                deletedCount += counts[2];
            }
        }

        // 删除配置中不存在的服务（如果需要完全回滚）
        // 注意：这里我们只更新/添加实例，不删除服务，以保留用户可能手动添加的服务

        log.info("配置同步完成: 新增 {} 个实例, 更新 {} 个实例, 删除 {} 个实例",
                addedCount, updatedCount, deletedCount);
    }

    /**
     * 同步单个服务的实例
     */
    private int[] syncServiceInstances(Long serviceConfigId, List<Map<String, Object>> instancesConfig) {
        int addedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        // 获取该服务当前的所有实例
        List<ServiceInstanceEntity> existingInstances = serviceInstanceRepository.findByServiceConfigId(serviceConfigId);

        // 用于跟踪配置中的实例名称
        List<String> configInstanceNames = new ArrayList<>();

        // 处理配置中的每个实例
        for (Map<String, Object> instanceConfig : instancesConfig) {
            String instanceName = (String) instanceConfig.get("name");
            if (instanceName == null || instanceName.isEmpty()) {
                continue;
            }

            configInstanceNames.add(instanceName);

            // 查找现有实例
            Optional<ServiceInstanceEntity> existingInstanceOpt = existingInstances.stream()
                    .filter(i -> i.getInstanceName().equals(instanceName))
                    .findFirst();

            if (existingInstanceOpt.isPresent()) {
                // 更新现有实例
                ServiceInstanceEntity instance = existingInstanceOpt.get();
                updateInstanceFromConfig(instance, instanceConfig);
                serviceInstanceRepository.save(instance);
                updatedCount++;
                log.debug("更新实例: {}", instanceName);
            } else {
                // 创建新实例
                ServiceInstanceEntity newInstance = createInstanceFromConfig(serviceConfigId, instanceConfig);
                serviceInstanceRepository.save(newInstance);
                addedCount++;
                log.info("创建实例: {}", instanceName);
            }
        }

        // 删除配置中不存在的实例（这些是后来添加的，回滚时需要删除）
        for (ServiceInstanceEntity existingInstance : existingInstances) {
            if (!configInstanceNames.contains(existingInstance.getInstanceName())) {
                // 先删除关联的限流器和熔断器配置
                instanceRateLimitRepository.deleteByInstanceId(existingInstance.getId());
                instanceCircuitBreakerRepository.deleteByInstanceId(existingInstance.getId());
                // 删除实例
                serviceInstanceRepository.delete(existingInstance);
                deletedCount++;
                log.info("删除实例: {} (回滚时清理)", existingInstance.getInstanceName());
            }
        }

        return new int[]{addedCount, updatedCount, deletedCount};
    }

    /**
     * 创建服务配置实体
     */
    private ServiceConfigEntity createServiceConfig(String serviceType, Map<String, Object> serviceConfig) {
        ServiceConfigEntity entity = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(serviceType)
                .adapter((String) serviceConfig.get("adapter"))
                .isLatest(true)
                .build();

        // 设置负载均衡类型
        @SuppressWarnings("unchecked")
        Map<String, Object> loadBalance = (Map<String, Object>) serviceConfig.get("loadBalance");
        if (loadBalance != null) {
            entity.setLoadBalanceType((String) loadBalance.get("type"));
        }

        return serviceConfigRepository.save(entity);
    }

    /**
     * 更新服务配置实体
     */
    private void updateServiceConfigFromMap(ServiceConfigEntity entity, Map<String, Object> serviceConfig) {
        if (serviceConfig.get("adapter") != null) {
            entity.setAdapter((String) serviceConfig.get("adapter"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> loadBalance = (Map<String, Object>) serviceConfig.get("loadBalance");
        if (loadBalance != null && loadBalance.get("type") != null) {
            entity.setLoadBalanceType((String) loadBalance.get("type"));
        }
    }

    /**
     * 从配置创建实例实体
     */
    private ServiceInstanceEntity createInstanceFromConfig(Long serviceConfigId, Map<String, Object> instanceConfig) {
        return ServiceInstanceEntity.builder()
                .serviceConfigId(serviceConfigId)
                .instanceName((String) instanceConfig.get("name"))
                .baseUrl((String) instanceConfig.get("baseUrl"))
                .path((String) instanceConfig.get("path"))
                .weight(instanceConfig.get("weight") != null ? ((Number) instanceConfig.get("weight")).intValue() : 1)
                .status("ACTIVE")
                .healthStatus("UNKNOWN")
                .build();
    }

    /**
     * 从配置更新实例实体
     */
    private void updateInstanceFromConfig(ServiceInstanceEntity entity, Map<String, Object> instanceConfig) {
        if (instanceConfig.get("baseUrl") != null) {
            entity.setBaseUrl((String) instanceConfig.get("baseUrl"));
        }
        if (instanceConfig.get("path") != null) {
            entity.setPath((String) instanceConfig.get("path"));
        }
        if (instanceConfig.get("weight") != null) {
            entity.setWeight(((Number) instanceConfig.get("weight")).intValue());
        }
        // 保持现有状态，不覆盖
    }
}