package org.unreal.modelrouter.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.entity.ConfigMainEntity;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import org.unreal.modelrouter.store.entity.ServiceConfigEntity;
import org.unreal.modelrouter.store.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.store.repository.ConfigMainRepository;
import org.unreal.modelrouter.store.repository.ConfigVersionRepository;
import org.unreal.modelrouter.store.repository.ServiceConfigRepository;
import org.unreal.modelrouter.store.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.store.repository.ConfigArchiveRepository;
import org.unreal.modelrouter.store.repository.ConfigChangeHistoryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.vo.ServiceInstanceVO;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 数据库配置管理服务 - 基于数据库的配置管理核心服务
 * <p>
 * 提供配置的增删改查、版本管理、服务管理等功能
 * 所有配置操作都通过数据库进行，YAML 配置仅作为初始化使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseConfigService {

    private static final String DEFAULT_CONFIG_KEY = "model-router-config";

    private final ConfigMainRepository configMainRepository;
    private final ConfigVersionRepository configVersionRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ObjectMapper objectMapper;
    private final ConfigArchiveRepository configArchiveRepository;
    private final ConfigChangeHistoryRepository configChangeHistoryRepository;

    // ==================== 配置读取 ====================

    /**
     * 获取当前配置（从数据库读取最新版本）
     *
     * @return 配置 Map
     */
    public Map<String, Object> getCurrentConfig() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doGetCurrentConfig();
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(30, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("获取当前配置超时");
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("获取当前配置时被打断", e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 获取当前配置的实际实现
     */
    private Map<String, Object> doGetCurrentConfig() {
        try {
            // 获取当前版本号
            Integer currentVersion = configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                    .map(ConfigMainEntity::getCurrentVersion)
                    .block();

            if (currentVersion == null) {
                log.warn("配置主表不存在，返回空配置");
                return new HashMap<>();
            }

            // 获取当前版本的配置数据
            ConfigVersionEntity versionEntity = configVersionRepository
                    .findByConfigKeyAndVersion(DEFAULT_CONFIG_KEY, currentVersion)
                    .block();

            if (versionEntity == null || versionEntity.getConfigData() == null) {
                log.warn("版本配置数据不存在：version={}", currentVersion);
                return new HashMap<>();
            }

            // 解析 JSON 配置数据
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(
                    versionEntity.getConfigData(), Map.class);

            log.debug("成功获取当前配置，版本：{}, 配置项数：{}", currentVersion, config.size());
            return config;

        } catch (Exception e) {
            log.error("获取当前配置失败：{}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 获取所有服务配置
     *
     * @return 服务配置 Map
     */
    public Map<String, Object> getAllServiceConfigs() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doGetAllServiceConfigs();
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(30, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("获取所有服务配置超时");
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("获取所有服务配置时被打断", e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 获取所有服务配置的实际实现
     */
    private Map<String, Object> doGetAllServiceConfigs() {
        Map<String, Object> result = new HashMap<>();

        try {
            List<ServiceConfigEntity> serviceConfigs = serviceConfigRepository
                    .findAllLatestByConfigKey(DEFAULT_CONFIG_KEY)
                    .collectList()
                    .block();

            if (serviceConfigs == null || serviceConfigs.isEmpty()) {
                log.info("没有找到服务配置");
                return result;
            }

            for (ServiceConfigEntity serviceConfig : serviceConfigs) {
                String serviceType = serviceConfig.getServiceType();
                Map<String, Object> serviceData = buildServiceConfigMap(serviceConfig);
                result.put(serviceType, serviceData);
            }

            log.info("获取到 {} 个服务配置", result.size());
            return result;

        } catch (Exception e) {
            log.error("获取所有服务配置失败：{}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * 获取指定服务配置
     *
     * @param serviceType 服务类型
     * @return 服务配置 Map
     */
    public Map<String, Object> getServiceConfig(String serviceType) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doGetServiceConfig(serviceType);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(30, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("获取服务配置超时：{}", serviceType);
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("获取服务配置时被打断：{}", serviceType, e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 获取指定服务配置的实际实现
     */
    private Map<String, Object> doGetServiceConfig(final String serviceType) {
        try {
            ServiceConfigEntity serviceConfig = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (serviceConfig == null) {
                log.warn("服务配置不存在：{}", serviceType);
                return new HashMap<>();
            }

            return buildServiceConfigMap(serviceConfig);

        } catch (Exception e) {
            log.error("获取服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 构建服务配置 Map
     */
    private Map<String, Object> buildServiceConfigMap(final ServiceConfigEntity serviceConfig) {
        Map<String, Object> serviceData = new HashMap<>();

        // 负载均衡配置
        Map<String, Object> loadBalance = new HashMap<>();
        loadBalance.put("type", serviceConfig.getLoadBalanceType());
        loadBalance.put("hashAlgorithm", serviceConfig.getLoadBalanceHashAlgorithm());
        serviceData.put("loadBalance", loadBalance);

        // 适配器
        serviceData.put("adapter", serviceConfig.getAdapter());

        // 限流配置
        Map<String, Object> rateLimit = new HashMap<>();
        rateLimit.put("enabled", serviceConfig.getRateLimitEnabled());
        rateLimit.put("algorithm", serviceConfig.getRateLimitAlgorithm());
        rateLimit.put("capacity", serviceConfig.getRateLimitCapacity());
        rateLimit.put("rate", serviceConfig.getRateLimitRate());
        rateLimit.put("scope", serviceConfig.getRateLimitScope());
        rateLimit.put("clientIpEnable", serviceConfig.getRateLimitClientIpEnable());
        serviceData.put("rateLimit", rateLimit);

        // 熔断配置
        Map<String, Object> circuitBreaker = new HashMap<>();
        circuitBreaker.put("enabled", serviceConfig.getCircuitBreakerEnabled());
        circuitBreaker.put("failureThreshold", serviceConfig.getCircuitBreakerFailureThreshold());
        circuitBreaker.put("timeout", serviceConfig.getCircuitBreakerTimeout());
        circuitBreaker.put("successThreshold", serviceConfig.getCircuitBreakerSuccessThreshold());
        serviceData.put("circuitBreaker", circuitBreaker);

        // 降级配置
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("enabled", serviceConfig.getFallbackEnabled());
        fallback.put("strategy", serviceConfig.getFallbackStrategy());
        fallback.put("cacheSize", serviceConfig.getFallbackCacheSize());
        fallback.put("cacheTtl", serviceConfig.getFallbackCacheTtl());
        serviceData.put("fallback", fallback);

        // 实例列表
        Flux<ServiceInstanceEntity> instancesFlux = serviceInstanceRepository
                .findAllByServiceConfigId(serviceConfig.getId());
        
        List<ServiceInstanceEntity> instances = instancesFlux != null 
                ? instancesFlux.collectList().block() 
                : null;

        if (instances != null && !instances.isEmpty()) {
            List<Map<String, Object>> instanceList = new ArrayList<>();
            for (ServiceInstanceEntity instance : instances) {
                Map<String, Object> instanceData = buildInstanceMap(instance);
                instanceList.add(instanceData);
            }
            serviceData.put("instances", instanceList);
        }

        return serviceData;
    }

    /**
     * 构建实例 VO
     */
    private ServiceInstanceVO buildInstanceVO(final ServiceInstanceEntity instance) {
        // 构建限流配置 VO
        ServiceInstanceVO.RateLimitVO rateLimitVO = null;
        if (instance.getRateLimitEnabled() != null && instance.getRateLimitEnabled()) {
            rateLimitVO = ServiceInstanceVO.RateLimitVO.builder()
                    .enabled(instance.getRateLimitEnabled())
                    .algorithm(instance.getRateLimitAlgorithm())
                    .capacity(instance.getRateLimitCapacity())
                    .rate(instance.getRateLimitRate())
                    .scope(instance.getRateLimitScope())
                    .build();
        }
        
        // 构建熔断配置 VO
        ServiceInstanceVO.CircuitBreakerVO circuitBreakerVO = null;
        if (instance.getCircuitBreakerEnabled() != null && instance.getCircuitBreakerEnabled()) {
            circuitBreakerVO = ServiceInstanceVO.CircuitBreakerVO.builder()
                    .enabled(instance.getCircuitBreakerEnabled())
                    .failureThreshold(instance.getCircuitBreakerFailureThreshold())
                    .timeout(instance.getCircuitBreakerTimeout())
                    .successThreshold(instance.getCircuitBreakerSuccessThreshold())
                    .build();
        }
        
        // 解析 headers
        Object headers = null;
        if (instance.getHeaders() != null && !instance.getHeaders().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> headersMap = objectMapper.readValue(
                        instance.getHeaders(), Map.class);
                headers = headersMap;
            } catch (JsonProcessingException e) {
                log.warn("解析实例 headers 失败：{}", e.getMessage());
            }
        }
        
        // 构建实例 VO
        String status = instance.getStatus();
        log.info("buildInstanceVO: instanceId={}, rateLimitEnabled={}, circuitBreakerEnabled={}", 
            instance.getId(), instance.getRateLimitEnabled(), instance.getCircuitBreakerEnabled());
        
        return ServiceInstanceVO.builder()
                .instanceId(instance.getId() != null ? instance.getId().toString() : null)
                .name(instance.getInstanceName())
                .baseUrl(instance.getBaseUrl())
                .path(instance.getPath())
                .weight(instance.getWeight())
                .status(status != null ? status.toLowerCase() : "active")
                .healthStatus(instance.getHealthStatus())
                .headers(headers)
                .rateLimit(rateLimitVO)
                .circuitBreaker(circuitBreakerVO)
                .build();
    }

    /**
     * 构建实例 Map（保留向后兼容）
     * @deprecated 使用 buildInstanceVO 替代
     */
    @Deprecated
    private Map<String, Object> buildInstanceMap(final ServiceInstanceEntity instance) {
        ServiceInstanceVO vo = buildInstanceVO(instance);
        Map<String, Object> result = new HashMap<>();
        result.put("instanceId", vo.getInstanceId());
        result.put("name", vo.getName());
        result.put("baseUrl", vo.getBaseUrl());
        result.put("path", vo.getPath());
        result.put("weight", vo.getWeight());
        result.put("status", vo.getStatus());
        result.put("healthStatus", vo.getHealthStatus());
        result.put("headers", vo.getHeaders());
        result.put("rateLimit", vo.getRateLimit());
        result.put("circuitBreaker", vo.getCircuitBreaker());
        return result;
    }

    // ==================== 服务管理 ====================

    /**
     * 更新服务配置（合并模式）
     *
     * @param serviceType  服务类型
     * @param serviceConfig 新的服务配置（将与现有配置合并）
     * @return 更新后的完整服务配置
     */
    public Map<String, Object> updateServiceConfig(String serviceType, Map<String, Object> serviceConfig) {
        log.info("updateServiceConfig 被调用：serviceType={}", serviceType);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            log.info("准备调度异步任务：serviceType={}", serviceType);
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    log.info("异步任务开始执行：serviceType={}", serviceType);
                    resultHolder[0] = doUpdateServiceConfig(serviceType, serviceConfig);
                    log.info("异步任务执行完成：serviceType={}, result={}", serviceType, resultHolder[0] != null ? "not null" : "null");
                } catch (Exception e) {
                    log.error("异步任务执行异常：serviceType={}, error={}", serviceType, e.getMessage(), e);
                    resultHolder[0] = null;
                } finally {
                    latch.countDown();
                    log.info("异步任务 latch.countDown: serviceType={}", serviceType);
                }
            });

            log.info("等待异步任务完成：serviceType={}", serviceType);
            if (latch.await(60, TimeUnit.SECONDS)) {
                log.info("updateServiceConfig 返回结果：serviceType={}, result={}", serviceType, resultHolder[0] != null ? "not null" : "null");
                return resultHolder[0];
            } else {
                log.error("更新服务配置超时：{}", serviceType);
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("更新服务配置时被打断：{}", serviceType, e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 更新服务配置的实际实现
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doUpdateServiceConfig(String serviceType, Map<String, Object> serviceConfig) {
        try {
            log.info("开始更新服务配置：serviceType={}, serviceConfig={}", serviceType, serviceConfig);
            
            // 1. 检查服务是否存在
            Boolean exists = serviceConfigRepository
                    .existsByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (Boolean.FALSE.equals(exists)) {
                throw new IllegalArgumentException("服务类型不存在：" + serviceType);
            }

            // 2. 获取现有服务配置
            ServiceConfigEntity existingEntity = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (existingEntity == null) {
                throw new IllegalArgumentException("服务配置不存在：" + serviceType);
            }
            
            log.info("现有服务配置：adapter={}, loadBalanceType={}", 
                existingEntity.getAdapter(), existingEntity.getLoadBalanceType());

            // 3. 合并配置
            Map<String, Object> existingConfig = buildServiceConfigMap(existingEntity);
            log.info("existingConfig: adapter={}", existingConfig.get("adapter"));
            
            Map<String, Object> mergedConfig = mergeServiceConfig(existingConfig, serviceConfig);
            log.info("mergedConfig: adapter={}", mergedConfig.get("adapter"));

            // 4. 更新服务配置实体
            ServiceConfigEntity updatedEntity = buildServiceConfigEntityFromMap(
                    serviceType, mergedConfig, existingEntity.getVersion());
            
            log.info("updatedEntity: adapter={}", updatedEntity.getAdapter());

            updatedEntity.setId(existingEntity.getId());
            updatedEntity.setVersion(existingEntity.getVersion());
            updatedEntity.setIsLatest(true);

            // 5. 保存更新 - 使用自定义 UPDATE 方法
            serviceConfigRepository.updateServiceConfig(updatedEntity).block();
            
            log.info("服务配置更新成功：{}", serviceType);
            
            // 6. 处理实例列表（如果有）
            if (mergedConfig.containsKey("instances")) {
                updateServiceInstances(updatedEntity.getId(),
                        (List<Map<String, Object>>) mergedConfig.get("instances"));
            }
            
            // 重新读取更新后的实体
            ServiceConfigEntity resultEntity = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();
            
            return buildServiceConfigMap(resultEntity);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("更新服务配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 添加新服务
     *
     * @param serviceType  服务类型
     * @param serviceConfig 服务配置
     * @return 创建后的服务配置
     */
    public Map<String, Object> addService(String serviceType, Map<String, Object> serviceConfig) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doAddService(serviceType, serviceConfig);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("添加服务超时：{}", serviceType);
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("添加服务时被打断：{}", serviceType, e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 添加新服务的实际实现
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doAddService(String serviceType, Map<String, Object> serviceConfig) {
        try {
            // 1. 检查服务是否已存在
            Boolean exists = serviceConfigRepository
                    .existsByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (Boolean.TRUE.equals(exists)) {
                throw new IllegalArgumentException("服务已存在：" + serviceType);
            }

            // 2. 获取当前最大版本号
            Integer currentVersion = getCurrentVersion();
            Integer newVersion = currentVersion != null ? currentVersion + 1 : 1;

            // 3. 构建服务配置实体
            ServiceConfigEntity newEntity = buildServiceConfigEntityFromMap(serviceType, serviceConfig, newVersion);
            newEntity.setIsLatest(true);

            // 4. 保存新服务配置
            ServiceConfigEntity savedEntity = serviceConfigRepository.save(newEntity).block();

            // 5. 处理实例列表（如果有）
            if (serviceConfig.containsKey("instances")) {
                updateServiceInstances(savedEntity.getId(),
                        (List<Map<String, Object>>) serviceConfig.get("instances"));
            }

            // 6. 更新配置主表的版本号（如果需要）
            updateConfigMainVersion(newVersion);

            log.info("服务添加成功：{}", serviceType);
            return buildServiceConfigMap(savedEntity);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加服务失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("添加服务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除服务
     *
     * @param serviceType 服务类型
     * @return 是否删除成功
     */
    public boolean deleteService(String serviceType) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Boolean[] resultHolder = new Boolean[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doDeleteService(serviceType);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("删除服务超时：{}", serviceType);
                return false;
            }
        } catch (InterruptedException e) {
            log.error("删除服务时被打断：{}", serviceType, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 删除服务的实际实现
     */
    private Boolean doDeleteService(String serviceType) {
        try {
            // 1. 检查服务是否存在
            Boolean exists = serviceConfigRepository
                    .existsByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (Boolean.FALSE.equals(exists)) {
                throw new IllegalArgumentException("服务类型不存在：" + serviceType);
            }

            // 2. 获取服务配置
            ServiceConfigEntity existingEntity = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (existingEntity == null) {
                throw new IllegalArgumentException("服务配置不存在：" + serviceType);
            }

            // 3. 删除关联的实例
            serviceInstanceRepository.deleteAllByServiceConfigId(existingEntity.getId()).block();

            // 4. 删除服务配置
            serviceConfigRepository.deleteByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType).block();

            log.info("服务删除成功：{}", serviceType);
            return true;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除服务失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("删除服务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 添加服务实例
     *
     * @param serviceType  服务类型
     * @param instanceConfig 实例配置
     * @return 创建后的实例配置
     */
    public Map<String, Object> addServiceInstance(String serviceType, Map<String, Object> instanceConfig) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doAddServiceInstance(serviceType, instanceConfig);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("添加服务实例超时：{}", serviceType);
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("添加服务实例时被打断：{}", serviceType, e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 添加服务实例的实际实现
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doAddServiceInstance(String serviceType, Map<String, Object> instanceConfig) {
        try {
            // 1. 获取服务配置
            ServiceConfigEntity serviceConfig = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (serviceConfig == null) {
                throw new IllegalArgumentException("服务类型不存在：" + serviceType);
            }

            // 2. 验证实例配置
            Map<String, Object> validatedInstance = validateInstanceConfig(instanceConfig);
            String instanceName = (String) validatedInstance.get("name");
            String baseUrl = (String) validatedInstance.get("baseUrl");

            // 3. 检查实例是否已存在
            Boolean instanceExists = serviceInstanceRepository
                    .findByServiceConfigIdAndInstanceName(serviceConfig.getId(), instanceName)
                    .hasElement()
                    .block();

            if (Boolean.TRUE.equals(instanceExists)) {
                throw new IllegalArgumentException("实例已存在：" + instanceName);
            }

            // 4. 构建实例实体
            ServiceInstanceEntity newInstance = buildInstanceEntityFromMap(
                    serviceConfig.getId(), validatedInstance);

            // 5. 保存实例
            ServiceInstanceEntity savedInstance = serviceInstanceRepository.save(newInstance).block();

            log.info("服务实例添加成功：{}@{}", serviceType, instanceName);
            return buildInstanceMap(savedInstance);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加服务实例失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("添加服务实例失败：" + e.getMessage(), e);
        }
    }

    /**
     * 更新服务实例
     *
     * @param serviceType  服务类型
     * @param instanceId   实例 ID
     * @param instanceConfig 新的实例配置
     * @return 更新后的实例配置
     */
    public Map<String, Object> updateServiceInstance(String serviceType, String instanceId,
                                                      Map<String, Object> instanceConfig) {
        log.info("更新服务实例：serviceType={}, instanceId={}", serviceType, instanceId);
        return doUpdateServiceInstance(serviceType, instanceId, instanceConfig);
    }

    /**
     * 更新服务实例的实际实现
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doUpdateServiceInstance(String serviceType, String instanceId,
                                                         Map<String, Object> instanceConfig) {
        try {
            // 1. 获取服务配置
            ServiceConfigEntity serviceConfig = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (serviceConfig == null) {
                throw new IllegalArgumentException("服务类型不存在：" + serviceType);
            }

            // 2. 查找实例
            ServiceInstanceEntity existingInstance = serviceInstanceRepository
                    .findById(Long.parseLong(instanceId))
                    .block();

            if (existingInstance == null) {
                throw new IllegalArgumentException("实例不存在：" + instanceId);
            }

            // 3. 验证实例属于该服务
            if (!existingInstance.getServiceConfigId().equals(serviceConfig.getId())) {
                throw new IllegalArgumentException("实例不属于该服务：" + instanceId);
            }

            // 4. 合并配置
            Map<String, Object> existingConfig = buildInstanceMap(existingInstance);
            Map<String, Object> mergedConfig = mergeInstanceConfig(existingConfig, instanceConfig);
            Map<String, Object> validatedConfig = validateInstanceConfig(mergedConfig);

            // 5. 更新实例实体
            ServiceInstanceEntity updatedInstance = buildInstanceEntityFromMap(
                    serviceConfig.getId(), validatedConfig);
            updatedInstance.setId(existingInstance.getId());

            // 6. 保存更新 - 使用 updateInstanceFull 更新所有字段
            log.info("更新实例：id={}, rateLimitEnabled={}, circuitBreakerEnabled={}", 
                updatedInstance.getId(), updatedInstance.getRateLimitEnabled(), updatedInstance.getCircuitBreakerEnabled());
            int rows = serviceInstanceRepository.updateInstanceFull(
                updatedInstance.getId(),
                updatedInstance.getServiceConfigId(),
                updatedInstance.getInstanceName(),
                updatedInstance.getBaseUrl(),
                updatedInstance.getPath(),
                updatedInstance.getWeight(),
                updatedInstance.getHeaders(),
                updatedInstance.getRateLimitEnabled(),
                updatedInstance.getRateLimitAlgorithm(),
                updatedInstance.getRateLimitCapacity(),
                updatedInstance.getRateLimitRate(),
                updatedInstance.getRateLimitScope(),
                updatedInstance.getRateLimitKey(),
                updatedInstance.getRateLimitClientIpEnable(),
                updatedInstance.getCircuitBreakerEnabled(),
                updatedInstance.getCircuitBreakerFailureThreshold(),
                updatedInstance.getCircuitBreakerTimeout(),
                updatedInstance.getCircuitBreakerSuccessThreshold(),
                updatedInstance.getStatus(),
                updatedInstance.getHealthStatus()
            ).block();
            log.info("更新结果：rows={}", rows);
            
            // 重新读取更新后的实例
            ServiceInstanceEntity savedInstance = serviceInstanceRepository.findById(updatedInstance.getId()).block();

            log.info("服务实例更新成功：{}#{}", serviceType, instanceId);
            return buildInstanceMap(savedInstance);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新服务实例失败：serviceType={}, instanceId={}, error={}",
                    serviceType, instanceId, e.getMessage(), e);
            throw new RuntimeException("更新服务实例失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除服务实例
     *
     * @param serviceType 服务类型
     * @param instanceId  实例 ID
     * @return 是否删除成功
     */
    public boolean deleteServiceInstance(String serviceType, String instanceId) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Boolean[] resultHolder = new Boolean[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doDeleteServiceInstance(serviceType, instanceId);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("删除服务实例超时：{}#{}", serviceType, instanceId);
                return false;
            }
        } catch (InterruptedException e) {
            log.error("删除服务实例时被打断：{}#{}", serviceType, instanceId, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 删除服务实例的实际实现
     */
    private Boolean doDeleteServiceInstance(String serviceType, String instanceId) {
        try {
            // 1. 获取服务配置
            ServiceConfigEntity serviceConfig = serviceConfigRepository
                    .findLatestByConfigKeyAndServiceType(DEFAULT_CONFIG_KEY, serviceType)
                    .block();

            if (serviceConfig == null) {
                throw new IllegalArgumentException("服务类型不存在：" + serviceType);
            }

            // 2. 查找实例
            ServiceInstanceEntity existingInstance = serviceInstanceRepository
                    .findById(Long.parseLong(instanceId))
                    .block();

            if (existingInstance == null) {
                throw new IllegalArgumentException("实例不存在：" + instanceId);
            }

            // 3. 验证实例属于该服务
            if (!existingInstance.getServiceConfigId().equals(serviceConfig.getId())) {
                throw new IllegalArgumentException("实例不属于该服务：" + instanceId);
            }

            // 4. 删除实例
            serviceInstanceRepository.deleteById(existingInstance.getId()).block();

            log.info("服务实例删除成功：{}#{}", serviceType, instanceId);
            return true;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除服务实例失败：serviceType={}, instanceId={}, error={}",
                    serviceType, instanceId, e.getMessage(), e);
            throw new RuntimeException("删除服务实例失败：" + e.getMessage(), e);
        }
    }

    // ==================== 配置元数据 ====================

    /**
     * 获取配置元数据
     *
     * @return 配置元数据 Map
     */
    public Map<String, Object> getConfigMetadata() {
        try {
            ConfigMainEntity configMain = configMainRepository
                    .findByConfigKey(DEFAULT_CONFIG_KEY)
                    .block();

            if (configMain == null) {
                return new HashMap<>();
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("configKey", configMain.getConfigKey());
            metadata.put("currentVersion", configMain.getCurrentVersion());
            metadata.put("initialVersion", configMain.getInitialVersion());
            metadata.put("createdAt", configMain.getCreatedAt());
            metadata.put("updatedAt", configMain.getUpdatedAt());
            metadata.put("createdBy", configMain.getCreatedBy());
            metadata.put("updatedBy", configMain.getUpdatedBy());
            metadata.put("description", configMain.getDescription());

            return metadata;

        } catch (Exception e) {
            log.error("获取配置元数据失败：{}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 获取所有版本号
     *
     * @return 版本号列表
     */
    public List<Integer> getAllVersions() {
        try {
            return configVersionRepository
                    .findAllVersionNumbers(DEFAULT_CONFIG_KEY)
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("获取所有版本号失败：{}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取当前版本号
     *
     * @return 当前版本号
     */
    public Integer getCurrentVersion() {
        try {
            Integer version = configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                    .map(ConfigMainEntity::getCurrentVersion)
                    .block();
            return version != null ? version : 0;
        } catch (Exception e) {
            log.error("获取当前版本号失败：{}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 获取配置键
     *
     * @return 配置键
     */
    public String getConfigKey() {
        return DEFAULT_CONFIG_KEY;
    }

    /**
     * 构建服务配置 Map（包级可见，用于测试）
     */
    Map<String, Object> buildServiceConfigMapForTest(final ServiceConfigEntity serviceConfig) {
        return buildServiceConfigMap(serviceConfig);
    }

    /**
     * 构建实例 Map（包级可见，用于测试）
     */
    Map<String, Object> buildInstanceMapForTest(final ServiceInstanceEntity instance) {
        return buildInstanceMap(instance);
    }

    /**
     * 合并服务配置（包级可见，用于测试）
     */
    Map<String, Object> mergeServiceConfigForTest(Map<String, Object> existing, Map<String, Object> updates) {
        return mergeServiceConfig(existing, updates);
    }

    /**
     * 合并实例配置（包级可见，用于测试）
     */
    Map<String, Object> mergeInstanceConfigForTest(Map<String, Object> existing, Map<String, Object> updates) {
        return mergeInstanceConfig(existing, updates);
    }

    /**
     * 验证实例配置（包级可见，用于测试）
     */
    Map<String, Object> validateInstanceConfigForTest(Map<String, Object> instanceConfig) {
        return validateInstanceConfig(instanceConfig);
    }

    /**
     * 安全获取 Boolean 值
     */
    private Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return defaultValue;
    }

    /**
     * 安全获取 Integer 值
     */
    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 安全获取 Boolean 值（包级可见，用于测试）
     */
    Boolean getBooleanForTest(Map<String, Object> map, String key, Boolean defaultValue) {
        return getBoolean(map, key, defaultValue);
    }

    /**
     * 安全获取 Integer 值（包级可见，用于测试）
     */
    Integer getIntegerForTest(Map<String, Object> map, String key, Integer defaultValue) {
        return getInteger(map, key, defaultValue);
    }

    // ==================== 版本管理 ====================

    /**
     * 获取指定版本的配置
     *
     * @param version 版本号
     * @return 配置 Map
     */
    public Map<String, Object> getVersionConfig(final Integer version) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Map<String, Object>[] resultHolder = new Map[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    resultHolder[0] = doGetVersionConfig(version);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(30, TimeUnit.SECONDS)) {
                return resultHolder[0];
            } else {
                log.error("获取版本配置超时：version={}", version);
                return new HashMap<>();
            }
        } catch (InterruptedException e) {
            log.error("获取版本配置时被打断：version={}", version, e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        }
    }

    /**
     * 获取指定版本配置的实际实现
     */
    private Map<String, Object> doGetVersionConfig(final Integer version) {
        try {
            ConfigVersionEntity versionEntity = configVersionRepository
                    .findByConfigKeyAndVersion(DEFAULT_CONFIG_KEY, version)
                    .block();

            if (versionEntity == null || versionEntity.getConfigData() == null) {
                log.warn("版本配置不存在：version={}", version);
                return new HashMap<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(
                    versionEntity.getConfigData(), Map.class);

            log.debug("成功获取版本配置：version={}, 配置项数={}", version, config.size());
            return config;

        } catch (Exception e) {
            log.error("获取版本配置失败：version={}, error={}", version, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 保存当前配置为新版本
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户 ID
     * @return 新版本号
     */
    public Integer saveAsNewVersion(final Map<String, Object> config, final String description, final String userId) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Integer[] newVersionHolder = new Integer[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    newVersionHolder[0] = doSaveAsNewVersion(config, description, userId);
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                return newVersionHolder[0];
            } else {
                log.error("保存新版本超时");
                return null;
            }
        } catch (InterruptedException e) {
            log.error("保存新版本时被打断", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 保存新版本的实际实现
     */
    private Integer doSaveAsNewVersion(final Map<String, Object> config, final String description, final String userId) {
        try {
            // 获取或创建配置主表记录
            ConfigMainEntity configMain = configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY).block();
            
            int newVersion;
            if (configMain == null) {
                // 创建新的配置主表记录
                configMain = ConfigMainEntity.builder()
                        .configKey(DEFAULT_CONFIG_KEY)
                        .initialVersion(1)
                        .currentVersion(1)
                        .createdAt(LocalDateTime.now())
                        .createdBy(userId)
                        .description(description)
                        .build();
                configMain = configMainRepository.save(configMain).block();
                newVersion = 1;
            } else {
                newVersion = configMain.getCurrentVersion() + 1;
            }

            // 将所有版本标记为非当前（首次创建时可能为 null）
            Mono<Integer> markAllResult = configVersionRepository.markAllAsNotCurrent(DEFAULT_CONFIG_KEY);
            if (markAllResult != null) {
                markAllResult.block();
            }

            // 序列化配置数据
            String configJson = objectMapper.writeValueAsString(config);

            // 创建新版本记录
            ConfigVersionEntity versionEntity = ConfigVersionEntity.builder()
                    .configKey(DEFAULT_CONFIG_KEY)
                    .version(newVersion)
                    .configData(configJson)
                    .createdBy(userId)
                    .description(description != null ? description : "配置更新")
                    .changeType("UPDATE")
                    .isCurrent(true)
                    .isArchived(false)
                    .build();

            configVersionRepository.save(versionEntity).block();

            // 更新配置主表
            configMainRepository.updateCurrentVersion(DEFAULT_CONFIG_KEY, newVersion, userId).block();

            // 记录变更历史
            saveChangeHistory("CREATE", "VERSION", String.valueOf(newVersion), 
                    description, userId);

            log.info("已保存配置为新版本：version={}, description={}", newVersion, description);
            return newVersion;

        } catch (Exception e) {
            log.error("保存新版本失败", e);
            throw new RuntimeException("保存新版本失败", e);
        }
    }

    /**
     * 应用指定版本的配置
     *
     * @param version 版本号
     * @param userId  用户 ID
     */
    public void applyVersion(final Integer version, final String userId) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Exception[] errorHolder = new Exception[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    doApplyVersion(version, userId);
                } catch (Exception e) {
                    errorHolder[0] = e;
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                if (errorHolder[0] != null) {
                    throw new RuntimeException(errorHolder[0]);
                }
            } else {
                throw new RuntimeException("应用版本配置超时");
            }
        } catch (InterruptedException e) {
            log.error("应用版本配置时被打断：version={}", version, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("应用版本配置失败", e);
        }
    }

    /**
     * 应用版本的实际实现
     */
    private void doApplyVersion(final Integer version, final String userId) throws Exception {
        // 1. 验证版本存在性
        Boolean exists = configVersionRepository.existsByVersion(DEFAULT_CONFIG_KEY, version).block();
        if (Boolean.FALSE.equals(exists)) {
            List<Integer> availableVersions = getAllVersions();
            throw new IllegalArgumentException(
                    String.format("版本 %d 不存在。可用版本：%s", version, availableVersions));
        }

        // 2. 获取版本配置
        Map<String, Object> config = doGetVersionConfig(version);
        if (config == null || config.isEmpty()) {
            throw new IllegalStateException(
                    String.format("无法读取版本 %d 的配置内容，配置可能已损坏", version));
        }

        // 3. 将所有版本标记为非当前
        configVersionRepository.markAllAsNotCurrent(DEFAULT_CONFIG_KEY).block();

        // 4. 标记指定版本为当前版本
        configVersionRepository.markAsCurrent(DEFAULT_CONFIG_KEY, version).block();

        // 5. 更新配置主表
        configMainRepository.updateCurrentVersion(DEFAULT_CONFIG_KEY, version, userId).block();

        // 6. 记录变更历史
        saveChangeHistory("APPLY_VERSION", "VERSION", String.valueOf(version), 
                "应用配置版本：" + version, userId);

        log.info("成功应用配置版本：{}", version);
    }

    /**
     * 删除指定版本的配置
     *
     * @param version 版本号
     * @param userId  用户 ID
     */
    public void deleteVersion(final Integer version, final String userId) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final Exception[] errorHolder = new Exception[1];

            Schedulers.boundedElastic().schedule(() -> {
                try {
                    doDeleteVersion(version, userId);
                } catch (Exception e) {
                    errorHolder[0] = e;
                } finally {
                    latch.countDown();
                }
            });

            if (latch.await(60, TimeUnit.SECONDS)) {
                if (errorHolder[0] != null) {
                    throw new RuntimeException(errorHolder[0]);
                }
            } else {
                throw new RuntimeException("删除版本配置超时");
            }
        } catch (InterruptedException e) {
            log.error("删除版本配置时被打断：version={}", version, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("删除版本配置失败", e);
        }
    }

    /**
     * 删除版本的实际实现
     */
    private void doDeleteVersion(final Integer version, final String userId) throws Exception {
        // 1. 验证版本存在性
        Boolean exists = configVersionRepository.existsByVersion(DEFAULT_CONFIG_KEY, version).block();
        if (Boolean.FALSE.equals(exists)) {
            List<Integer> availableVersions = getAllVersions();
            throw new IllegalArgumentException(
                    String.format("版本 %d 不存在。可用版本：%s", version, availableVersions));
        }

        // 2. 检查是否为当前版本
        ConfigMainEntity configMain = configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY).block();
        if (configMain != null && configMain.getCurrentVersion() != null && configMain.getCurrentVersion() == version) {
            throw new IllegalStateException(
                    String.format("不能删除当前版本 %d。请先应用其他版本后再删除此版本", version));
        }

        // 3. 验证至少保留一个版本
        List<Integer> allVersions = getAllVersions();
        if (allVersions.size() <= 1) {
            throw new IllegalStateException("不能删除最后一个版本，系统至少需要保留一个配置版本");
        }

        // 4. 检查版本是否已归档
        ConfigVersionEntity versionEntity = configVersionRepository
                .findByConfigKeyAndVersion(DEFAULT_CONFIG_KEY, version).block();
        
        if (versionEntity != null && Boolean.TRUE.equals(versionEntity.getIsArchived())) {
            throw new IllegalStateException(
                    String.format("版本 %d 已归档，无法删除。如需删除请先从归档中恢复", version));
        }

        // 5. 执行删除
        configVersionRepository.deleteByVersion(DEFAULT_CONFIG_KEY, version).block();

        // 6. 记录变更历史
        saveChangeHistory("DELETE", "VERSION", String.valueOf(version), 
                "删除配置版本：" + version, userId);

        log.info("成功删除配置版本：{}", version);
    }

    /**
     * 获取版本详细信息
     *
     * @param version 版本号
     * @return 版本信息 Map
     */
    public Map<String, Object> getVersionInfo(final Integer version) {
        try {
            ConfigVersionEntity versionEntity = configVersionRepository
                    .findByConfigKeyAndVersion(DEFAULT_CONFIG_KEY, version)
                    .block();

            if (versionEntity == null) {
                return new HashMap<>();
            }

            Map<String, Object> info = new HashMap<>();
            info.put("version", version);
            info.put("createdAt", versionEntity.getCreatedAt());
            info.put("createdBy", versionEntity.getCreatedBy());
            info.put("description", versionEntity.getDescription());
            info.put("changeType", versionEntity.getChangeType());
            info.put("isCurrent", versionEntity.getIsCurrent());
            info.put("isArchived", versionEntity.getIsArchived());
            info.put("archivePath", versionEntity.getArchivePath());

            return info;

        } catch (Exception e) {
            log.error("获取版本信息失败：version={}, error={}", version, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 保存变更历史
     */
    private void saveChangeHistory(final String operationType, final String targetType,
                                   final String targetId, final String description, final String changedBy) {
        try {
            // 简化实现，实际应该保存到 config_change_history 表
            log.debug("记录变更历史：operation={}, target={}, targetId={}, description={}",
                    operationType, targetType, targetId, description);
        } catch (Exception e) {
            log.warn("保存变更历史失败：{}", e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 合并服务配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeServiceConfig(Map<String, Object> existing, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // instances 字段不合并，直接替换
                merged.put(key, value);
            } else if (existing.containsKey(key)
                    && existing.get(key) instanceof Map
                    && value instanceof Map) {
                // 递归合并 Map 类型字段
                Map<String, Object> existingMap = (Map<String, Object>) existing.get(key);
                Map<String, Object> updateMap = (Map<String, Object>) value;
                merged.put(key, mergeServiceConfig(existingMap, updateMap));
            } else {
                merged.put(key, value);
            }
        }

        return merged;
    }

    /**
     * 合并实例配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeInstanceConfig(Map<String, Object> existing, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // status 字段直接覆盖，不合并
            if ("status".equals(key)) {
                merged.put(key, value);
                continue;
            }

            if ("headers".equals(key) && value instanceof Map && existing.get(key) instanceof Map) {
                // headers 字段需要合并
                Map<String, String> existingHeaders = (Map<String, String>) existing.get(key);
                Map<String, String> updateHeaders = (Map<String, String>) value;
                Map<String, String> mergedHeaders = new HashMap<>(existingHeaders);
                mergedHeaders.putAll(updateHeaders);
                merged.put(key, mergedHeaders);
            } else if (existing.containsKey(key)
                    && existing.get(key) instanceof Map
                    && value instanceof Map) {
                // 递归合并 Map 类型字段
                Map<String, Object> existingMap = (Map<String, Object>) existing.get(key);
                Map<String, Object> updateMap = (Map<String, Object>) value;
                merged.put(key, mergeInstanceConfig(existingMap, updateMap));
            } else {
                merged.put(key, value);
            }
        }

        return merged;
    }

    /**
     * 验证实例配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> validateInstanceConfig(Map<String, Object> instanceConfig) {
        Map<String, Object> validated = new HashMap<>();

        // 必填字段
        if (instanceConfig.containsKey("name")) {
            validated.put("name", (String) instanceConfig.get("name"));
        } else {
            throw new IllegalArgumentException("实例配置缺少必填字段：name");
        }

        if (instanceConfig.containsKey("baseUrl")) {
            validated.put("baseUrl", (String) instanceConfig.get("baseUrl"));
        } else {
            throw new IllegalArgumentException("实例配置缺少必填字段：baseUrl");
        }

        // 可选字段
        validated.put("path", instanceConfig.containsKey("path")
                ? (String) instanceConfig.get("path") : "/");
        validated.put("weight", instanceConfig.containsKey("weight")
                ? ((Number) instanceConfig.get("weight")).intValue() : 1);
        // status 字段：支持大小写，统一转为小写
        log.info("validateInstanceConfig: instanceConfig.containsKey(status)={}, status value={}", 
            instanceConfig.containsKey("status"), instanceConfig.get("status"));
        if (instanceConfig.containsKey("status")) {
            String status = (String) instanceConfig.get("status");
            log.info("validateInstanceConfig: 使用传入的 status={}, normalized={}", status, status.toLowerCase());
            validated.put("status", status.toLowerCase());
        } else {
            log.info("validateInstanceConfig: 使用默认 status=active");
            validated.put("status", "active"); // 默认值
        }
        validated.put("healthStatus", instanceConfig.containsKey("healthStatus")
                ? (String) instanceConfig.get("healthStatus") : "UNKNOWN");

        // headers
        if (instanceConfig.containsKey("headers") && instanceConfig.get("headers") instanceof Map) {
            validated.put("headers", instanceConfig.get("headers"));
        }

        // 限流配置
        // 限流器配置 - 支持扁平格式和嵌套格式
        if (instanceConfig.containsKey("rateLimit") && instanceConfig.get("rateLimit") instanceof Map) {
            validated.put("rateLimit", instanceConfig.get("rateLimit"));
        } else {
            // 扁平格式字段
            if (instanceConfig.containsKey("rateLimitEnabled")) {
                validated.put("rateLimitEnabled", instanceConfig.get("rateLimitEnabled"));
            }
            if (instanceConfig.containsKey("rateLimitAlgorithm")) {
                validated.put("rateLimitAlgorithm", instanceConfig.get("rateLimitAlgorithm"));
            }
            if (instanceConfig.containsKey("rateLimitCapacity")) {
                validated.put("rateLimitCapacity", instanceConfig.get("rateLimitCapacity"));
            }
            if (instanceConfig.containsKey("rateLimitRate")) {
                validated.put("rateLimitRate", instanceConfig.get("rateLimitRate"));
            }
            if (instanceConfig.containsKey("rateLimitScope")) {
                validated.put("rateLimitScope", instanceConfig.get("rateLimitScope"));
            }
            if (instanceConfig.containsKey("rateLimitKey")) {
                validated.put("rateLimitKey", instanceConfig.get("rateLimitKey"));
            }
            if (instanceConfig.containsKey("rateLimitClientIpEnable")) {
                validated.put("rateLimitClientIpEnable", instanceConfig.get("rateLimitClientIpEnable"));
            }
        }
        
        // 熔断器配置 - 支持扁平格式和嵌套格式
        if (instanceConfig.containsKey("circuitBreaker") && instanceConfig.get("circuitBreaker") instanceof Map) {
            validated.put("circuitBreaker", instanceConfig.get("circuitBreaker"));
        } else {
            // 扁平格式字段
            if (instanceConfig.containsKey("circuitBreakerEnabled")) {
                validated.put("circuitBreakerEnabled", instanceConfig.get("circuitBreakerEnabled"));
            }
            if (instanceConfig.containsKey("circuitBreakerFailureThreshold")) {
                validated.put("circuitBreakerFailureThreshold", instanceConfig.get("circuitBreakerFailureThreshold"));
            }
            if (instanceConfig.containsKey("circuitBreakerTimeout")) {
                validated.put("circuitBreakerTimeout", instanceConfig.get("circuitBreakerTimeout"));
            }
            if (instanceConfig.containsKey("circuitBreakerSuccessThreshold")) {
                validated.put("circuitBreakerSuccessThreshold", instanceConfig.get("circuitBreakerSuccessThreshold"));
            }
        }

        return validated;
    }

    /**
     * 从 Map 构建 ServiceConfigEntity
     */
    @SuppressWarnings("unchecked")
    private ServiceConfigEntity buildServiceConfigEntityFromMap(
            String serviceType, Map<String, Object> config, Integer version) {

        ServiceConfigEntity.ServiceConfigEntityBuilder builder = ServiceConfigEntity.builder()
                .configKey(DEFAULT_CONFIG_KEY)
                .serviceType(serviceType)
                .version(version);

        // 负载均衡配置
        if (config.containsKey("loadBalance") && config.get("loadBalance") instanceof Map) {
            Map<String, Object> loadBalance = (Map<String, Object>) config.get("loadBalance");
            if (loadBalance.containsKey("type")) {
                builder.loadBalanceType((String) loadBalance.get("type"));
            }
            if (loadBalance.containsKey("hashAlgorithm")) {
                builder.loadBalanceHashAlgorithm((String) loadBalance.get("hashAlgorithm"));
            }
        }

        // 适配器
        if (config.containsKey("adapter")) {
            builder.adapter((String) config.get("adapter"));
        }

        // 限流配置
        if (config.containsKey("rateLimit") && config.get("rateLimit") instanceof Map) {
            Map<String, Object> rateLimit = (Map<String, Object>) config.get("rateLimit");
            builder.rateLimitEnabled(getBoolean(rateLimit, "enabled", false));
            if (rateLimit.containsKey("algorithm")) {
                builder.rateLimitAlgorithm((String) rateLimit.get("algorithm"));
            }
            if (rateLimit.containsKey("capacity")) {
                builder.rateLimitCapacity(getInteger(rateLimit, "capacity", 100));
            }
            if (rateLimit.containsKey("rate")) {
                builder.rateLimitRate(getInteger(rateLimit, "rate", 10));
            }
            if (rateLimit.containsKey("scope")) {
                builder.rateLimitScope((String) rateLimit.get("scope"));
            }
            if (rateLimit.containsKey("clientIpEnable")) {
                builder.rateLimitClientIpEnable(getBoolean(rateLimit, "clientIpEnable", false));
            }
        }

        // 熔断配置
        if (config.containsKey("circuitBreaker") && config.get("circuitBreaker") instanceof Map) {
            Map<String, Object> circuitBreaker = (Map<String, Object>) config.get("circuitBreaker");
            builder.circuitBreakerEnabled(getBoolean(circuitBreaker, "enabled", false));
            if (circuitBreaker.containsKey("failureThreshold")) {
                builder.circuitBreakerFailureThreshold(getInteger(circuitBreaker, "failureThreshold", 5));
            }
            if (circuitBreaker.containsKey("timeout")) {
                builder.circuitBreakerTimeout(getInteger(circuitBreaker, "timeout", 3000));
            }
            if (circuitBreaker.containsKey("successThreshold")) {
                builder.circuitBreakerSuccessThreshold(getInteger(circuitBreaker, "successThreshold", 3));
            }
        }

        // 降级配置
        if (config.containsKey("fallback") && config.get("fallback") instanceof Map) {
            Map<String, Object> fallback = (Map<String, Object>) config.get("fallback");
            builder.fallbackEnabled(getBoolean(fallback, "enabled", false));
            if (fallback.containsKey("strategy")) {
                builder.fallbackStrategy((String) fallback.get("strategy"));
            }
            if (fallback.containsKey("cacheSize")) {
                builder.fallbackCacheSize(getInteger(fallback, "cacheSize", 100));
            }
            if (fallback.containsKey("cacheTtl")) {
                builder.fallbackCacheTtl(getInteger(fallback, "cacheTtl", 300));
            }
        }

        return builder.build();
    }

    /**
     * 从 Map 构建 ServiceInstanceEntity
     */
    @SuppressWarnings("unchecked")
    private ServiceInstanceEntity buildInstanceEntityFromMap(
            Long serviceConfigId, Map<String, Object> instanceConfig) {

        ServiceInstanceEntity.ServiceInstanceEntityBuilder builder = ServiceInstanceEntity.builder()
                .serviceConfigId(serviceConfigId);

        // 必填字段
        if (instanceConfig.containsKey("name")) {
            builder.instanceName((String) instanceConfig.get("name"));
        }
        if (instanceConfig.containsKey("baseUrl")) {
            builder.baseUrl((String) instanceConfig.get("baseUrl"));
        }

        // 可选字段
        if (instanceConfig.containsKey("path")) {
            builder.path((String) instanceConfig.get("path"));
        }
        if (instanceConfig.containsKey("weight")) {
            builder.weight(((Number) instanceConfig.get("weight")).intValue());
        }
        if (instanceConfig.containsKey("status")) {
            builder.status((String) instanceConfig.get("status"));
        }
        if (instanceConfig.containsKey("healthStatus")) {
            builder.healthStatus((String) instanceConfig.get("healthStatus"));
        }

        // headers - 转为 JSON 字符串
        if (instanceConfig.containsKey("headers") && instanceConfig.get("headers") instanceof Map) {
            try {
                builder.headers(objectMapper.writeValueAsString(instanceConfig.get("headers")));
            } catch (JsonProcessingException e) {
                log.warn("序列化 headers 失败：{}", e.getMessage());
            }
        }

        // 限流配置 - 支持扁平格式（新接口）和嵌套格式（旧接口）
        // 扁平格式字段优先处理
        if (instanceConfig.containsKey("rateLimitEnabled")) {
            builder.rateLimitEnabled(getBoolean(instanceConfig, "rateLimitEnabled", false));
        }
        if (instanceConfig.containsKey("rateLimitAlgorithm")) {
            builder.rateLimitAlgorithm((String) instanceConfig.get("rateLimitAlgorithm"));
        }
        if (instanceConfig.containsKey("rateLimitCapacity")) {
            builder.rateLimitCapacity(getInteger(instanceConfig, "rateLimitCapacity", 100));
        }
        if (instanceConfig.containsKey("rateLimitRate")) {
            builder.rateLimitRate(getInteger(instanceConfig, "rateLimitRate", 10));
        }
        if (instanceConfig.containsKey("rateLimitScope")) {
            builder.rateLimitScope((String) instanceConfig.get("rateLimitScope"));
        }
        if (instanceConfig.containsKey("rateLimitKey")) {
            builder.rateLimitKey((String) instanceConfig.get("rateLimitKey"));
        }
        if (instanceConfig.containsKey("rateLimitClientIpEnable")) {
            builder.rateLimitClientIpEnable(getBoolean(instanceConfig, "rateLimitClientIpEnable", false));
        }
        
        // 嵌套格式（兼容旧接口）
        if (instanceConfig.containsKey("rateLimit") && instanceConfig.get("rateLimit") instanceof Map) {
            Map<String, Object> rateLimit = (Map<String, Object>) instanceConfig.get("rateLimit");
            builder.rateLimitEnabled(getBoolean(rateLimit, "enabled", false));
            if (rateLimit.containsKey("algorithm")) {
                builder.rateLimitAlgorithm((String) rateLimit.get("algorithm"));
            }
            if (rateLimit.containsKey("capacity")) {
                builder.rateLimitCapacity(getInteger(rateLimit, "capacity", 100));
            }
            if (rateLimit.containsKey("rate")) {
                builder.rateLimitRate(getInteger(rateLimit, "rate", 10));
            }
            if (rateLimit.containsKey("scope")) {
                builder.rateLimitScope((String) rateLimit.get("scope"));
            }
        }

        // 熔断器配置 - 支持扁平格式（新接口）和嵌套格式（旧接口）
        // 扁平格式字段优先处理
        if (instanceConfig.containsKey("circuitBreakerEnabled")) {
            builder.circuitBreakerEnabled(getBoolean(instanceConfig, "circuitBreakerEnabled", false));
        }
        if (instanceConfig.containsKey("circuitBreakerFailureThreshold")) {
            builder.circuitBreakerFailureThreshold(getInteger(instanceConfig, "circuitBreakerFailureThreshold", 5));
        }
        if (instanceConfig.containsKey("circuitBreakerTimeout")) {
            builder.circuitBreakerTimeout(getInteger(instanceConfig, "circuitBreakerTimeout", 60000));
        }
        if (instanceConfig.containsKey("circuitBreakerSuccessThreshold")) {
            builder.circuitBreakerSuccessThreshold(getInteger(instanceConfig, "circuitBreakerSuccessThreshold", 2));
        }
        
        // 嵌套格式（兼容旧接口）
        if (instanceConfig.containsKey("circuitBreaker") && instanceConfig.get("circuitBreaker") instanceof Map) {
            Map<String, Object> circuitBreaker = (Map<String, Object>) instanceConfig.get("circuitBreaker");
            builder.circuitBreakerEnabled(getBoolean(circuitBreaker, "enabled", false));
            if (circuitBreaker.containsKey("failureThreshold")) {
                builder.circuitBreakerFailureThreshold(getInteger(circuitBreaker, "failureThreshold", 5));
            }
            if (circuitBreaker.containsKey("timeout")) {
                builder.circuitBreakerTimeout(getInteger(circuitBreaker, "timeout", 60000));
            }
            if (circuitBreaker.containsKey("successThreshold")) {
                builder.circuitBreakerSuccessThreshold(getInteger(circuitBreaker, "successThreshold", 2));
            }
        }

        return builder.build();
    }

    /**
     * 更新服务实例列表
     */
    @SuppressWarnings("unchecked")
    private void updateServiceInstances(Long serviceConfigId, List<Map<String, Object>> instances) {
        if (instances == null) {
            return;
        }

        // 1. 删除现有实例
        serviceInstanceRepository.deleteAllByServiceConfigId(serviceConfigId).block();

        // 2. 添加新实例
        for (Map<String, Object> instanceConfig : instances) {
            try {
                Map<String, Object> validatedInstance = validateInstanceConfig(instanceConfig);
                ServiceInstanceEntity newInstance = buildInstanceEntityFromMap(serviceConfigId, validatedInstance);
                serviceInstanceRepository.save(newInstance).block();
            } catch (Exception e) {
                log.warn("添加实例失败：config={}, error={}", instanceConfig, e.getMessage());
            }
        }
    }

    /**
     * 更新配置主表版本号
     */
    private void updateConfigMainVersion(Integer newVersion) {
        try {
            ConfigMainEntity configMain = configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY).block();

            if (configMain == null) {
                // 创建新的配置主记录
                ConfigMainEntity newConfigMain = ConfigMainEntity.builder()
                        .configKey(DEFAULT_CONFIG_KEY)
                        .currentVersion(newVersion)
                        .initialVersion(newVersion)
                        .build();
                configMainRepository.save(newConfigMain).block();
            } else {
                // 更新现有记录
                configMain.setCurrentVersion(newVersion);
                configMain.setUpdatedAt(LocalDateTime.now());
                configMainRepository.save(configMain).block();
            }
        } catch (Exception e) {
            log.error("更新配置主表失败：version={}, error={}", newVersion, e.getMessage(), e);
        }
    }
}
