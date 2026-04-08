package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务配置管理器
 * v1.5.2: 使用 JPA 实现服务配置管理，使用 DTO 替代 Map
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceConfigManager {

    private final ServiceConfigRepository serviceConfigRepository;

    /**
     * 获取所有服务配置
     */
    public List<ServiceConfigDTO> getAllServiceConfigs() {
        return serviceConfigRepository.findAllByIsLatestTrue()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的服务配置
     */
    public Optional<ServiceConfigDTO> getServiceConfig(String serviceType) {
        return serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .map(this::convertToDTO);
    }

    /**
     * 保存或更新服务配置
     */
    @Transactional
    public ServiceConfigDTO saveServiceConfig(String serviceType, CreateServiceConfigRequest request) {
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
                .adapter(request.getAdapter())
                .loadBalanceType(request.getLoadBalanceType())
                .version(getNextVersion(serviceType))
                .isLatest(true)
                .build();

        ServiceConfigEntity saved = serviceConfigRepository.save(entity);
        log.info("Saved service config for type: {} with version: {}", serviceType, saved.getVersion());
        
        return convertToDTO(saved);
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

    private ServiceConfigDTO convertToDTO(ServiceConfigEntity entity) {
        return ServiceConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .serviceType(entity.getServiceType())
                .adapter(entity.getAdapter())
                .loadBalanceType(entity.getLoadBalanceType())
                .version(entity.getVersion())
                .isLatest(entity.getIsLatest())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
