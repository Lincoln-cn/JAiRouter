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
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

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
     * 构建实例 Map
     */
    private Map<String, Object> buildInstanceMap(final ServiceInstanceEntity instance) {
        Map<String, Object> instanceData = new HashMap<>();
        instanceData.put("name", instance.getInstanceName());
        instanceData.put("baseUrl", instance.getBaseUrl());
        instanceData.put("path", instance.getPath());
        instanceData.put("weight", instance.getWeight());
        instanceData.put("status", instance.getStatus());
        instanceData.put("healthStatus", instance.getHealthStatus());

        // 解析 headers
        if (instance.getHeaders() != null && !instance.getHeaders().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = objectMapper.readValue(
                        instance.getHeaders(), Map.class);
                instanceData.put("headers", headers);
            } catch (JsonProcessingException e) {
                log.warn("解析实例 headers 失败：{}", e.getMessage());
            }
        }

        // 实例限流配置
        if (instance.getRateLimitEnabled() != null && instance.getRateLimitEnabled()) {
            Map<String, Object> rateLimit = new HashMap<>();
            rateLimit.put("enabled", instance.getRateLimitEnabled());
            rateLimit.put("algorithm", instance.getRateLimitAlgorithm());
            rateLimit.put("capacity", instance.getRateLimitCapacity());
            rateLimit.put("rate", instance.getRateLimitRate());
            rateLimit.put("scope", instance.getRateLimitScope());
            instanceData.put("rateLimit", rateLimit);
        }

        return instanceData;
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
}
