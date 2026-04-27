package org.unreal.modelrouter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.jpa.entity.InstanceCircuitBreakerEntity;
import org.unreal.modelrouter.jpa.entity.InstanceRateLimitEntity;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.jpa.repository.InstanceRateLimitRepository;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.util.SecurityUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 服务实例管理器
 * v1.5.2: 使用 JPA 实现服务实例管理，使用 DTO 替代 Map
 * v1.5.3: 添加版本管理支持，同步更新配置表
 * v1.7.1: 添加运行时配置刷新，确保实例变更后立即生效
 */
@Slf4j
@Service
public class ServiceInstanceManager {

    private final ServiceInstanceRepository serviceInstanceRepository;
    private final InstanceRateLimitRepository rateLimitRepository;
    private final InstanceCircuitBreakerRepository circuitBreakerRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final ConfigurationService configurationService;
    private final ModelServiceRegistry modelServiceRegistry;

    public ServiceInstanceManager(
            final ServiceInstanceRepository serviceInstanceRepository,
            final InstanceRateLimitRepository rateLimitRepository,
            final InstanceCircuitBreakerRepository circuitBreakerRepository,
            final ServiceConfigRepository serviceConfigRepository,
            @Lazy ConfigurationService configurationService,
            final ModelServiceRegistry modelServiceRegistry) {
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.circuitBreakerRepository = circuitBreakerRepository;
        this.serviceConfigRepository = serviceConfigRepository;
        this.configurationService = configurationService;
        this.modelServiceRegistry = modelServiceRegistry;
    }

    /**
     * 获取服务下的所有实例
     */
    public List<ServiceInstanceDTO> getInstancesByServiceConfigId(final Long serviceConfigId) {
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
    public Optional<ServiceInstanceDTO> getInstance(final Long id) {
        return serviceInstanceRepository.findById(id)
                .map(this::convertToDTO);
    }

    /**
     * 创建实例
     */
    @Transactional
    public ServiceInstanceDTO createInstance(final Long serviceConfigId,final CreateServiceInstanceRequest request) {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .serviceConfigId(serviceConfigId)
                .instanceName(request.getName())
                .baseUrl(request.getBaseUrl())
                .path(request.getPath())
                .weight(request.getWeight() != null ? request.getWeight() : 1)
                .status(request.getStatus() != null ? request.getStatus().toUpperCase() : "ACTIVE")
                .healthStatus("UNKNOWN")
                .adapter(request.getAdapter())
                .headers(request.getHeaders())
                .build();

        ServiceInstanceEntity saved = serviceInstanceRepository.save(entity);
        log.info("Created service instance: {} for config: {}", saved.getId(), serviceConfigId);
        
        // 保存版本
        saveVersion("添加服务实例: " + request.getName());
        
        return convertToDTO(saved);
    }

    /**
     * 更新实例
     */
    @Transactional
    public ServiceInstanceDTO updateInstance(final Long id,final CreateServiceInstanceRequest request) {
        ServiceInstanceEntity entity = serviceInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + id));

        String instanceName = entity.getInstanceName();

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
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus().toUpperCase());
        }
        if (request.getAdapter() != null) {
            entity.setAdapter(request.getAdapter());
        }
        if (request.getHeaders() != null) {
            entity.setHeaders(request.getHeaders());
        }

        ServiceInstanceEntity saved = serviceInstanceRepository.save(entity);
        log.info("Updated service instance: {}", id);
        
        // 保存版本
        saveVersion("更新服务实例: " + instanceName);
        
        return convertToDTO(saved);
    }

    /**
     * 删除实例
     */
    @Transactional
    public void deleteInstance(final Long id) {
        // 获取实例名称用于版本描述
        ServiceInstanceEntity entity = serviceInstanceRepository.findById(id)
                .orElse(null);
        String instanceName = entity != null ? entity.getInstanceName() : String.valueOf(id);
        
        // 同时删除关联的限流器和熔断器配置
        rateLimitRepository.deleteByInstanceId(id);
        circuitBreakerRepository.deleteByInstanceId(id);
        serviceInstanceRepository.deleteById(id);
        log.info("Deleted service instance: {}", id);
        
        // 保存版本
        saveVersion("删除服务实例: " + instanceName);
    }

    /**
     * 更新健康状态
     */
    @Transactional
    public void updateHealthStatus(final Long id,final String healthStatus,final String errorMessage) {
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
    public Optional<InstanceRateLimitDTO> getRateLimitConfig(final Long instanceId) {
        return rateLimitRepository.findByInstanceId(instanceId)
                .map(this::convertRateLimitToDTO);
    }

    /**
     * 保存或更新限流器配置
     */
    @Transactional
    public InstanceRateLimitDTO saveRateLimitConfig(final Long instanceId,final InstanceRateLimitDTO dto) {
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
    public void deleteRateLimitConfig(final Long instanceId) {
        rateLimitRepository.deleteByInstanceId(instanceId);
        log.info("Deleted rate limit config for instance: {}", instanceId);
    }

    // ==================== 熔断器配置管理 ====================

    /**
     * 获取实例的熔断器配置
     */
    public Optional<InstanceCircuitBreakerDTO> getCircuitBreakerConfig(final Long instanceId) {
        return circuitBreakerRepository.findByInstanceId(instanceId)
                .map(this::convertCircuitBreakerToDTO);
    }

    /**
     * 保存或更新熔断器配置
     */
    @Transactional
    public InstanceCircuitBreakerDTO saveCircuitBreakerConfig(final Long instanceId,final InstanceCircuitBreakerDTO dto) {
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
    public void deleteCircuitBreakerConfig(final Long instanceId) {
        circuitBreakerRepository.deleteByInstanceId(instanceId);
        log.info("Deleted circuit breaker config for instance: {}", instanceId);
    }

    // ==================== 转换方法 ====================

    private ServiceInstanceDTO convertToDTO(final ServiceInstanceEntity entity) {
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
                .adapter(entity.getAdapter())
                .headers(entity.getHeaders())
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

    private InstanceRateLimitDTO convertRateLimitToDTO(final InstanceRateLimitEntity entity) {
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

    private InstanceCircuitBreakerDTO convertCircuitBreakerToDTO(final InstanceCircuitBreakerEntity entity) {
        return InstanceCircuitBreakerDTO.builder()
                .id(entity.getId())
                .instanceId(entity.getInstanceId())
                .enabled(entity.getEnabled())
                .failureThreshold(entity.getFailureThreshold())
                .timeout(entity.getTimeoutMs())
                .successThreshold(entity.getSuccessThreshold())
                .build();
    }

    /**
     * 保存版本
     * v1.5.3: 在实例变更后保存版本
     * v1.5.4: 添加元数据字段用于版本列表显示
     * 从数据库构建完整配置（包含实例数据），然后保存到 config_data 表
     */
    private void saveVersion(final String description) {
        try {
            // 从数据库构建完整配置
            Map<String, Object> fullConfig = buildFullConfigFromDatabase();
            
            // 添加元数据（用于版本列表显示操作类型和操作详情）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("operation", "instanceChange");
            metadata.put("operationDetail", description);
            metadata.put("timestamp", System.currentTimeMillis());
            fullConfig.put("_metadata", metadata);
            
            // 保存为新版本
            String userId = SecurityUtils.getCurrentUserId();
            configurationService.saveAsNewVersion(fullConfig, description, userId);
            log.info("版本已保存: {}", description);

            // 刷新运行时配置，确保实例变更立即生效
            modelServiceRegistry.refreshFromMergedConfig();
            log.info("运行时配置已刷新");
        } catch (Exception e) {
            log.warn("保存版本失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库构建完整配置
     * 包含所有服务配置和实例数据
     */
    private Map<String, Object> buildFullConfigFromDatabase() {
        // 获取基础配置
        Map<String, Object> baseConfig = configurationService.getAllConfigurations();
        Map<String, Object> result = new HashMap<>(baseConfig);
        
        // 获取所有服务配置
        List<ServiceConfigEntity> serviceConfigs = serviceConfigRepository.findAllByIsLatestTrue();
        
        // 构建服务配置 Map
        Map<String, Object> servicesMap = new HashMap<>();
        
        for (ServiceConfigEntity serviceConfig : serviceConfigs) {
            Map<String, Object> serviceMap = new HashMap<>();
            serviceMap.put("adapter", serviceConfig.getAdapter());
            
            // 负载均衡配置
            Map<String, Object> loadBalanceMap = new HashMap<>();
            loadBalanceMap.put("type", serviceConfig.getLoadBalanceType() != null ? 
                    serviceConfig.getLoadBalanceType() : "round-robin");
            loadBalanceMap.put("hashAlgorithm", "md5");
            serviceMap.put("loadBalance", loadBalanceMap);
            
            // 获取该服务的所有实例
            List<ServiceInstanceEntity> instances = serviceInstanceRepository
                    .findByServiceConfigId(serviceConfig.getId());
            
            List<Map<String, Object>> instancesList = new ArrayList<>();
            for (ServiceInstanceEntity instance : instances) {
                Map<String, Object> instanceMap = new HashMap<>();
                instanceMap.put("name", instance.getInstanceName());
                instanceMap.put("baseUrl", instance.getBaseUrl());
                instanceMap.put("path", instance.getPath());
                instanceMap.put("weight", instance.getWeight());
                instanceMap.put("status", instance.getStatus());
                instanceMap.put("instanceId", String.valueOf(instance.getId()));
                
                // 添加限流器配置
                rateLimitRepository.findByInstanceId(instance.getId())
                        .ifPresent(rateLimit -> {
                            Map<String, Object> rateLimitMap = new HashMap<>();
                            rateLimitMap.put("enabled", rateLimit.getEnabled());
                            rateLimitMap.put("algorithm", rateLimit.getAlgorithm());
                            rateLimitMap.put("capacity", rateLimit.getCapacity());
                            rateLimitMap.put("rate", rateLimit.getRate());
                            rateLimitMap.put("scope", rateLimit.getScope());
                            rateLimitMap.put("key", rateLimit.getRateLimitKey());
                            rateLimitMap.put("clientIpEnable", rateLimit.getClientIpEnable());
                            instanceMap.put("rateLimit", rateLimitMap);
                        });
                
                // 添加熔断器配置
                circuitBreakerRepository.findByInstanceId(instance.getId())
                        .ifPresent(cb -> {
                            Map<String, Object> cbMap = new HashMap<>();
                            cbMap.put("enabled", cb.getEnabled());
                            cbMap.put("failureThreshold", cb.getFailureThreshold());
                            cbMap.put("timeout", cb.getTimeoutMs());
                            cbMap.put("successThreshold", cb.getSuccessThreshold());
                            instanceMap.put("circuitBreaker", cbMap);
                        });
                
                instancesList.add(instanceMap);
            }
            
            serviceMap.put("instances", instancesList);
            servicesMap.put(serviceConfig.getServiceType(), serviceMap);
        }
        
        result.put("services", servicesMap);
        return result;
    }
}