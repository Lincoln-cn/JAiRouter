package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.jpa.entity.InstanceCircuitBreakerEntity;
import org.unreal.modelrouter.jpa.entity.InstanceRateLimitEntity;
import org.unreal.modelrouter.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.jpa.repository.InstanceRateLimitRepository;
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
    private final InstanceRateLimitRepository rateLimitRepository;
    private final InstanceCircuitBreakerRepository circuitBreakerRepository;

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
        // 同时删除关联的限流器和熔断器配置
        rateLimitRepository.deleteByInstanceId(id);
        circuitBreakerRepository.deleteByInstanceId(id);
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

    // ==================== 限流器配置管理 ====================

    /**
     * 获取实例的限流器配置
     */
    public Optional<InstanceRateLimitDTO> getRateLimitConfig(Long instanceId) {
        return rateLimitRepository.findByInstanceId(instanceId)
                .map(this::convertRateLimitToDTO);
    }

    /**
     * 保存或更新限流器配置
     */
    @Transactional
    public InstanceRateLimitDTO saveRateLimitConfig(Long instanceId, InstanceRateLimitDTO dto) {
        // 确保实例存在
        if (!serviceInstanceRepository.existsById(instanceId)) {
            throw new RuntimeException("Instance not found: " + instanceId);
        }

        InstanceRateLimitEntity entity = rateLimitRepository
                .findByInstanceId(instanceId)
                .orElse(InstanceRateLimitEntity.builder()
                        .instanceId(instanceId)
                        .enabled(false)
                        .algorithm("token-bucket")
                        .capacity(100)
                        .rate(10)
                        .scope("instance")
                        .clientIpEnable(false)
                        .build());

        // 更新字段
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getAlgorithm() != null) {
            entity.setAlgorithm(dto.getAlgorithm());
        }
        if (dto.getCapacity() != null) {
            entity.setCapacity(dto.getCapacity());
        }
        if (dto.getRate() != null) {
            entity.setRate(dto.getRate());
        }
        if (dto.getScope() != null) {
            entity.setScope(dto.getScope());
        }
        if (dto.getKey() != null) {
            entity.setRateLimitKey(dto.getKey());
        }
        if (dto.getClientIpEnable() != null) {
            entity.setClientIpEnable(dto.getClientIpEnable());
        }

        InstanceRateLimitEntity saved = rateLimitRepository.save(entity);
        log.info("Saved rate limit config for instance: {}", instanceId);
        return convertRateLimitToDTO(saved);
    }

    /**
     * 删除限流器配置
     */
    @Transactional
    public void deleteRateLimitConfig(Long instanceId) {
        rateLimitRepository.deleteByInstanceId(instanceId);
        log.info("Deleted rate limit config for instance: {}", instanceId);
    }

    // ==================== 熔断器配置管理 ====================

    /**
     * 获取实例的熔断器配置
     */
    public Optional<InstanceCircuitBreakerDTO> getCircuitBreakerConfig(Long instanceId) {
        return circuitBreakerRepository.findByInstanceId(instanceId)
                .map(this::convertCircuitBreakerToDTO);
    }

    /**
     * 保存或更新熔断器配置
     */
    @Transactional
    public InstanceCircuitBreakerDTO saveCircuitBreakerConfig(Long instanceId, InstanceCircuitBreakerDTO dto) {
        // 确保实例存在
        if (!serviceInstanceRepository.existsById(instanceId)) {
            throw new RuntimeException("Instance not found: " + instanceId);
        }

        InstanceCircuitBreakerEntity entity = circuitBreakerRepository
                .findByInstanceId(instanceId)
                .orElse(InstanceCircuitBreakerEntity.builder()
                        .instanceId(instanceId)
                        .enabled(false)
                        .failureThreshold(5)
                        .timeoutMs(60000)
                        .successThreshold(2)
                        .build());

        // 更新字段
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getFailureThreshold() != null) {
            entity.setFailureThreshold(dto.getFailureThreshold());
        }
        if (dto.getTimeout() != null) {
            entity.setTimeoutMs(dto.getTimeout());
        }
        if (dto.getSuccessThreshold() != null) {
            entity.setSuccessThreshold(dto.getSuccessThreshold());
        }

        InstanceCircuitBreakerEntity saved = circuitBreakerRepository.save(entity);
        log.info("Saved circuit breaker config for instance: {}", instanceId);
        return convertCircuitBreakerToDTO(saved);
    }

    /**
     * 删除熔断器配置
     */
    @Transactional
    public void deleteCircuitBreakerConfig(Long instanceId) {
        circuitBreakerRepository.deleteByInstanceId(instanceId);
        log.info("Deleted circuit breaker config for instance: {}", instanceId);
    }

    // ==================== 转换方法 ====================

    private ServiceInstanceDTO convertToDTO(ServiceInstanceEntity entity) {
        ServiceInstanceDTO dto = ServiceInstanceDTO.builder()
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

        // 加载限流器配置
        rateLimitRepository.findByInstanceId(entity.getId())
                .ifPresent(rateLimit -> dto.setRateLimit(convertRateLimitToDTO(rateLimit)));

        // 加载熔断器配置
        circuitBreakerRepository.findByInstanceId(entity.getId())
                .ifPresent(cb -> dto.setCircuitBreaker(convertCircuitBreakerToDTO(cb)));

        return dto;
    }

    private InstanceRateLimitDTO convertRateLimitToDTO(InstanceRateLimitEntity entity) {
        return InstanceRateLimitDTO.builder()
                .id(entity.getId())
                .instanceId(entity.getInstanceId())
                .enabled(entity.getEnabled())
                .algorithm(entity.getAlgorithm())
                .capacity(entity.getCapacity())
                .rate(entity.getRate())
                .scope(entity.getScope())
                .key(entity.getRateLimitKey())
                .clientIpEnable(entity.getClientIpEnable())
                .build();
    }

    private InstanceCircuitBreakerDTO convertCircuitBreakerToDTO(InstanceCircuitBreakerEntity entity) {
        return InstanceCircuitBreakerDTO.builder()
                .id(entity.getId())
                .instanceId(entity.getInstanceId())
                .enabled(entity.getEnabled())
                .failureThreshold(entity.getFailureThreshold())
                .timeout(entity.getTimeoutMs())
                .successThreshold(entity.getSuccessThreshold())
                .build();
    }
}