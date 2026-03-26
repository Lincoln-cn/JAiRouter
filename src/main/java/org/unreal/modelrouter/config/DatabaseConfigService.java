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
}
