package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务配置管理器
 * 使用强类型 DTO 处理服务配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceConfigManager {

    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;

    /**
     * 获取所有服务配置
     */
    public List<ServiceConfigDTO> getAllServiceConfigs() {
        List<ServiceConfigEntity> entities = serviceConfigRepository.findAllByIsLatestTrue();
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定服务类型的配置
     */
    public Optional<ServiceConfigDTO> getServiceConfig(String serviceType) {
        return serviceConfigRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .map(this::toDTO);
    }

    /**
     * 保存服务配置
     */
    @Transactional
    public ServiceConfigDTO saveServiceConfig(String serviceType, CreateServiceConfigRequest request) {
        log.info("保存服务配置: serviceType={}", serviceType);

        ServiceConfigEntity entity = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(serviceType)
                .adapter(request.getAdapter())
                .loadBalanceType(request.getLoadBalanceType())
                .version(1)
                .isLatest(true)
                .build();

        ServiceConfigEntity saved = serviceConfigRepository.save(entity);
        log.info("服务配置保存成功: id={}", saved.getId());

        return toDTO(saved);
    }

    /**
     * 删除服务配置
     */
    @Transactional
    public void deleteServiceConfig(String serviceType) {
        log.info("删除服务配置: serviceType={}", serviceType);
        serviceConfigRepository.deleteAllByServiceType(serviceType);
    }

    /**
     * 更新服务配置（不含实例）
     */
    @Transactional
    public void updateServiceConfig(String serviceType, UpdateServiceConfigRequest request) {
        log.info("更新服务配置: serviceType={}, adapter={}", 
                serviceType, request.getAdapter());

        ServiceConfigEntity entity = serviceConfigRepository
                .findFirstByServiceTypeAndIsLatestTrue(serviceType)
                .orElseGet(() -> ServiceConfigEntity.builder()
                        .configKey("model-router-config")
                        .serviceType(serviceType)
                        .version(1)
                        .isLatest(true)
                        .build());

        // 更新适配器
        if (request.getAdapter() != null) {
            entity.setAdapter(request.getAdapter());
        }

        // 更新负载均衡类型
        LoadBalanceConfig lb = request.getLoadBalance();
        if (lb != null && lb.getType() != null) {
            entity.setLoadBalanceType(lb.getType());
        }

        serviceConfigRepository.save(entity);
        log.info("服务配置更新成功: serviceType={}", serviceType);
    }

    private ServiceConfigDTO toDTO(ServiceConfigEntity entity) {
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