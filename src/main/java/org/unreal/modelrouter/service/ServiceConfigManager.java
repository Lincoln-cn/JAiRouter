package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务配置管理器
 * v1.5.2: 使用 JPA 实现服务配置管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceConfigManager {

    private final ServiceConfigRepository serviceConfigRepository;

    /**
     * 获取所有服务配置
     */
    public List<Map<String, Object>> getAllServiceConfigs() {
        return serviceConfigRepository.findAllByIsLatestTrue()
                .stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的服务配置
     */
    public Optional<Map<String, Object>> getServiceConfig(String serviceType) {
        return serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .map(this::convertToMap);
    }

    /**
     * 保存或更新服务配置
     */
    @Transactional
    public Map<String, Object> saveServiceConfig(String serviceType, Map<String, Object> config) {
        // 标记旧版本为非最新
        serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .ifPresent(oldConfig -> {
                    oldConfig.setIsLatest(false);
                    serviceConfigRepository.save(oldConfig);
                });

        // 创建新配置
        ServiceConfigEntity entity = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(serviceType)
                .adapter((String) config.get("adapter"))
                .loadBalanceType((String) config.get("loadBalanceType"))
                .version(getNextVersion(serviceType))
                .isLatest(true)
                .build();

        ServiceConfigEntity saved = serviceConfigRepository.save(entity);
        log.info("Saved service config for type: {} with version: {}", serviceType, saved.getVersion());
        
        return convertToMap(saved);
    }

    /**
     * 删除服务配置
     */
    @Transactional
    public void deleteServiceConfig(String serviceType) {
        serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .ifPresent(config -> {
                    config.setIsLatest(false);
                    serviceConfigRepository.save(config);
                    log.info("Deleted service config for type: {}", serviceType);
                });
    }

    private Integer getNextVersion(String serviceType) {
        return serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .map(config -> config.getVersion() + 1)
                .orElse(1);
    }

    private Map<String, Object> convertToMap(ServiceConfigEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "serviceType", entity.getServiceType(),
                "adapter", entity.getAdapter() != null ? entity.getAdapter() : "",
                "loadBalanceType", entity.getLoadBalanceType() != null ? entity.getLoadBalanceType() : "random",
                "version", entity.getVersion(),
                "createdAt", entity.getCreatedAt(),
                "updatedAt", entity.getUpdatedAt()
        );
    }
}
