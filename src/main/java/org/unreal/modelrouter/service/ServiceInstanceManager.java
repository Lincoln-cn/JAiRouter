package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 服务实例管理器
 * v1.5.2: 使用 JPA 实现服务实例管理，使用 DTO 替代 Map
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceManager {

    private final ServiceInstanceRepository serviceInstanceRepository;

    /**
     * 获取服务下的所有实例
     */
    public List<ServiceInstanceDTO> getInstancesByServiceConfigId(Long serviceConfigId) {
        return serviceInstanceRepository.findByServiceConfigId(serviceConfigId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有实例
     */
    public List<ServiceInstanceDTO> getAllInstances() {
        return serviceInstanceRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个实例
     */
    public Optional<ServiceInstanceDTO> getInstance(Long id) {
        return serviceInstanceRepository.findById(id)
                .map(this::convertToDTO);
    }

    /**
     * 创建实例
     */
    @Transactional
    public ServiceInstanceDTO createInstance(Long serviceConfigId, CreateServiceInstanceRequest request) {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .serviceConfigId(serviceConfigId)
                .instanceName(request.getName())
                .baseUrl(request.getBaseUrl())
                .path(request.getPath())
                .weight(request.getWeight() != null ? request.getWeight() : 1)
                .status("ACTIVE")
                .healthStatus("UNKNOWN")
                .build();

        ServiceInstanceEntity saved = serviceInstanceRepository.save(entity);
        log.info("Created service instance: {} for config: {}", saved.getId(), serviceConfigId);
        return convertToDTO(saved);
    }

    /**
     * 更新实例
     */
    @Transactional
    public ServiceInstanceDTO updateInstance(Long id, CreateServiceInstanceRequest request) {
        ServiceInstanceEntity entity = serviceInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + id));

        if (request.getName() != null) {
            entity.setInstanceName(request.getName());
        }
        if (request.getBaseUrl() != null) {
            entity.setBaseUrl(request.getBaseUrl());
        }
        if (request.getPath() != null) {
            entity.setPath(request.getPath());
        }
        if (request.getWeight() != null) {
            entity.setWeight(request.getWeight());
        }

        ServiceInstanceEntity saved = serviceInstanceRepository.save(entity);
        log.info("Updated service instance: {}", id);
        return convertToDTO(saved);
    }

    /**
     * 删除实例
     */
    @Transactional
    public void deleteInstance(Long id) {
        serviceInstanceRepository.deleteById(id);
        log.info("Deleted service instance: {}", id);
    }

    /**
     * 更新健康状态
     */
    @Transactional
    public void updateHealthStatus(Long id, String healthStatus, String errorMessage) {
        serviceInstanceRepository.findById(id).ifPresent(entity -> {
            entity.setHealthStatus(healthStatus);
            entity.setErrorMessage(errorMessage);
            serviceInstanceRepository.save(entity);
        });
    }

    private ServiceInstanceDTO convertToDTO(ServiceInstanceEntity entity) {
        return ServiceInstanceDTO.builder()
                .id(entity.getId())
                .serviceConfigId(entity.getServiceConfigId())
                .name(entity.getInstanceName())
                .baseUrl(entity.getBaseUrl())
                .path(entity.getPath())
                .weight(entity.getWeight())
                .status(entity.getStatus())
                .healthStatus(entity.getHealthStatus())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
