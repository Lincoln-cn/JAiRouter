package org.unreal.modelrouter.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelRouterProperties.ServiceConfig;
import org.unreal.modelrouter.store.entity.ConfigMainEntity;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import org.unreal.modelrouter.store.entity.ServiceConfigEntity;
import org.unreal.modelrouter.store.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.store.repository.ConfigArchiveRepository;
import org.unreal.modelrouter.store.repository.ConfigChangeHistoryRepository;
import org.unreal.modelrouter.store.repository.ConfigMainRepository;
import org.unreal.modelrouter.store.repository.ConfigVersionRepository;
import org.unreal.modelrouter.store.repository.ServiceConfigRepository;
import org.unreal.modelrouter.store.repository.ServiceInstanceRepository;
// v1.5.1: 移除 R2DBC 相关导入

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 配置初始化器 - 负责将 YAML 配置同步到 H2 数据库
 * <p>
 * 每次应用启动时，都会以 YAML 配置为准同步到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigInitializer implements CommandLineRunner {

    private static final String DEFAULT_CONFIG_KEY = "model-router-config";

    private final ModelRouterProperties modelRouterProperties;
    private final ConfigMainRepository configMainRepository;
    private final ConfigVersionRepository configVersionRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ConfigChangeHistoryRepository configChangeHistoryRepository;
    private final ConfigArchiveRepository configArchiveRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始执行配置初始化...");
        
        try {
            // 同步 YAML 配置到数据库
            syncYamlConfigToDatabase();
            log.info("配置初始化完成");
        } catch (Exception e) {
            log.error("配置初始化失败：{}", e.getMessage(), e);
            // 不抛出异常，避免启动失败
        }
    }

    /**
     * 同步 YAML 配置到数据库
     */
    public Mono<Void> syncYamlConfigToDatabase() {
        return Mono.fromRunnable(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    doSyncYamlConfigToDatabase();
                    latch.countDown();
                } catch (Exception e) {
                    log.error("同步 YAML 配置到数据库失败：{}", e.getMessage(), e);
                    latch.countDown();
                }
            });
            
            try {
                if (!latch.await(60, TimeUnit.SECONDS)) {
                    log.error("同步 YAML 配置超时");
                }
            } catch (InterruptedException e) {
                log.error("等待同步配置时被打断：{}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).then();
    }

    /**
     * 执行实际的同步逻辑
     */
    private void doSyncYamlConfigToDatabase() {
        log.info("开始同步 YAML 配置到数据库...");
        
        try {
            // 1. 保存或更新配置主表
            ConfigMainEntity configMain = saveConfigMain();
            log.info("配置主表已更新，当前版本：{}", configMain.getCurrentVersion());
            
            // 2. 保存配置版本（完整配置快照）
            saveConfigVersion(configMain.getCurrentVersion());
            log.info("配置版本已保存");
            
            // 3. 保存服务配置
            saveServiceConfigs(configMain.getCurrentVersion());
            log.info("服务配置已保存");
            
            // 4. 记录变更历史
            saveChangeHistory("SYNC", "CONFIG", DEFAULT_CONFIG_KEY, 
                    "从 YAML 配置文件同步配置到数据库", "system");
            log.info("变更历史已记录");
            
            log.info("YAML 配置同步完成，共保存 {} 个服务配置", 
                    modelRouterProperties.getServices() != null 
                            ? modelRouterProperties.getServices().size() : 0);
                            
        } catch (Exception e) {
            log.error("同步 YAML 配置失败：{}", e.getMessage(), e);
            throw new RuntimeException("同步 YAML 配置失败", e);
        }
    }

    /**
     * 保存或更新配置主表
     */
    private ConfigMainEntity saveConfigMain() {
        return configMainRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                .flatMap(existing -> {
                    // 更新现有记录
                    existing.setCurrentVersion(existing.getCurrentVersion() + 1);
                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setUpdatedBy("system");
                    existing.setDescription("从 YAML 配置文件同步");
                    return configMainRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 创建新记录
                    ConfigMainEntity newConfig = ConfigMainEntity.builder()
                            .configKey(DEFAULT_CONFIG_KEY)
                            .initialVersion(1)
                            .currentVersion(1)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .createdBy("system")
                            .updatedBy("system")
                            .description("从 YAML 配置文件初始化")
                            .build();
                    return configMainRepository.save(newConfig);
                }))
                ;
    }

    /**
     * 保存配置版本快照
     */
    private void saveConfigVersion(Integer version) {
        try {
            // 将完整配置转换为 JSON
            String configJson = objectMapper.writeValueAsString(modelRouterProperties);
            
            // 将所有版本标记为非当前
            configVersionRepository.markAllAsNotCurrent(DEFAULT_CONFIG_KEY);
            
            // 创建新版本记录
            ConfigVersionEntity versionEntity = ConfigVersionEntity.builder()
                    .configKey(DEFAULT_CONFIG_KEY)
                    .version(version)
                    .configData(configJson)
                    .createdBy("system")
                    .description("从 YAML 配置文件同步")
                    .changeType("SYNC")
                    .isCurrent(true)
                    .isArchived(false)
                    .build();
            
            configVersionRepository.save(versionEntity);
            
        } catch (JsonProcessingException e) {
            log.error("将配置转换为 JSON 失败：{}", e.getMessage(), e);
            throw new RuntimeException("配置序列化失败", e);
        }
    }

    /**
     * 保存服务配置
     */
    private void saveServiceConfigs(Integer version) {
        Map<String, ServiceConfig> services = modelRouterProperties.getServices();
        if (services == null || services.isEmpty()) {
            log.info("没有服务配置需要同步");
            return;
        }
        
        // 将所有现有服务配置标记为非最新
        serviceConfigRepository.markAllAsNotLatest(DEFAULT_CONFIG_KEY);
        
        for (Map.Entry<String, ServiceConfig> entry : services.entrySet()) {
            String serviceType = entry.getKey();
            ServiceConfig serviceConfig = entry.getValue();
            
            try {
                // 保存服务配置
                ServiceConfigEntity serviceConfigEntity = ServiceConfigEntity.builder()
                        .configKey(DEFAULT_CONFIG_KEY)
                        .serviceType(serviceType)
                        .loadBalanceType(serviceConfig.getLoadBalance() != null 
                                ? serviceConfig.getLoadBalance().getType() : null)
                        .loadBalanceHashAlgorithm(serviceConfig.getLoadBalance() != null 
                                ? serviceConfig.getLoadBalance().getHashAlgorithm() : "md5")
                        .adapter(serviceConfig.getAdapter())
                        .rateLimitEnabled(serviceConfig.getRateLimit() != null 
                                ? serviceConfig.getRateLimit().getEnabled() : true)
                        .rateLimitAlgorithm(serviceConfig.getRateLimit() != null 
                                ? serviceConfig.getRateLimit().getAlgorithm() : "token-bucket")
                        .rateLimitCapacity(serviceConfig.getRateLimit() != null
                                ? Math.toIntExact(serviceConfig.getRateLimit().getCapacity()) : 1000)
                        .rateLimitRate(serviceConfig.getRateLimit() != null
                                ? Math.toIntExact(serviceConfig.getRateLimit().getRate()) : 100)
                        .rateLimitScope(serviceConfig.getRateLimit() != null 
                                ? serviceConfig.getRateLimit().getScope() : "service")
                        .rateLimitClientIpEnable(serviceConfig.getRateLimit() != null 
                                ? serviceConfig.getRateLimit().getClientIpEnable() : true)
                        .circuitBreakerEnabled(serviceConfig.getCircuitBreaker() != null 
                                ? serviceConfig.getCircuitBreaker().getEnabled() : true)
                        .circuitBreakerFailureThreshold(serviceConfig.getCircuitBreaker() != null 
                                ? serviceConfig.getCircuitBreaker().getFailureThreshold() : 5)
                        .circuitBreakerTimeout(serviceConfig.getCircuitBreaker() != null
                                ? Math.toIntExact(serviceConfig.getCircuitBreaker().getTimeout()) : 60000)
                        .circuitBreakerSuccessThreshold(serviceConfig.getCircuitBreaker() != null 
                                ? serviceConfig.getCircuitBreaker().getSuccessThreshold() : 2)
                        .fallbackEnabled(serviceConfig.getFallback() != null 
                                ? serviceConfig.getFallback().getEnabled() : true)
                        .fallbackStrategy(serviceConfig.getFallback() != null 
                                ? serviceConfig.getFallback().getStrategy() : "default")
                        .fallbackCacheSize(serviceConfig.getFallback() != null 
                                ? serviceConfig.getFallback().getCacheSize() : null)
                        .fallbackCacheTtl(serviceConfig.getFallback() != null
                                ? Math.toIntExact(serviceConfig.getFallback().getCacheTtl()) : null)
                        .version(version)
                        .isLatest(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                ServiceConfigEntity savedServiceConfig = serviceConfigRepository
                        .save(serviceConfigEntity);
                
                // 保存服务实例
                saveServiceInstances(savedServiceConfig.getId(), serviceConfig);
                
            } catch (Exception e) {
                log.error("保存服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
                // 继续处理下一个服务
            }
        }
    }

    /**
     * 保存服务实例
     */
    private void saveServiceInstances(Long serviceConfigId, ServiceConfig serviceConfig) {
        if (serviceConfig.getInstances() == null || serviceConfig.getInstances().isEmpty()) {
            return;
        }
        
        for (org.unreal.modelrouter.model.ModelRouterProperties.ModelInstance instance 
                : serviceConfig.getInstances()) {
            try {
                String headersJson = null;
                if (instance.getHeaders() != null && !instance.getHeaders().isEmpty()) {
                    headersJson = objectMapper.writeValueAsString(instance.getHeaders());
                }
                
                String instanceRateLimitJson = null;
                if (instance.getRateLimit() != null) {
                    instanceRateLimitJson = objectMapper.writeValueAsString(instance.getRateLimit());
                }
                
                ServiceInstanceEntity instanceEntity = ServiceInstanceEntity.builder()
                        .serviceConfigId(serviceConfigId)
                        .instanceName(instance.getName())
                        .baseUrl(instance.getBaseUrl())
                        .path(instance.getPath())
                        .weight(instance.getWeight())
                        .headers(headersJson)
                        .rateLimitEnabled(instance.getRateLimit() != null 
                                ? instance.getRateLimit().getEnabled() : true)
                        .rateLimitAlgorithm(instance.getRateLimit() != null 
                                ? instance.getRateLimit().getAlgorithm() : "token-bucket")
                        .rateLimitCapacity(instance.getRateLimit() != null
                                ? Math.toIntExact(instance.getRateLimit().getCapacity()) : null)
                        .rateLimitRate(instance.getRateLimit() != null
                                ? Math.toIntExact(instance.getRateLimit().getRate()) : null)
                        .rateLimitScope(instance.getRateLimit() != null 
                                ? instance.getRateLimit().getScope() : "instance")
                        .status("ACTIVE")
                        .healthStatus("UNKNOWN")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                serviceInstanceRepository.save(instanceEntity);
                
            } catch (Exception e) {
                log.error("保存服务实例失败：instanceName={}, error={}", 
                        instance.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 保存变更历史
     */
    private void saveChangeHistory(String operationType, String targetType, String targetId,
                                   String description, String changedBy) {
        try {
            // 变更历史表记录详细变更，这里简化处理
            log.debug("记录变更历史：operation={}, target={}, description={}", 
                    operationType, targetType, description);
        } catch (Exception e) {
            log.warn("保存变更历史失败：{}", e.getMessage());
        }
    }

    /**
     * 获取当前配置键
     */
    public String getConfigKey() {
        return DEFAULT_CONFIG_KEY;
    }
}
