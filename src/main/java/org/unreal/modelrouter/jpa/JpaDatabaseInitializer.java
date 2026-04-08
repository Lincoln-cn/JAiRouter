package org.unreal.modelrouter.jpa;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.jpa.repository.ConfigRepository;
import org.unreal.modelrouter.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.security.service.ApiKeyService;
import org.unreal.modelrouter.util.JacksonHelper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA 数据库初始化器
 * v1.5.x: 纯 JPA 方式初始化数据库，加载 YAML 配置到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn({"apiKeyService"})
public class JpaDatabaseInitializer {

    private final ConfigRepository configRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ModelRouterProperties modelRouterProperties;
    private final ConfigurationHelper configurationHelper;
    private final ApiKeyService apiKeyService;

    @PostConstruct
    public void initialize() {
        log.info("Initializing JPA database...");

        // 1. 初始化模型配置
        initializeModelConfig();

        // 2. 初始化 API Keys
        initializeApiKeys();

        log.info("JPA database initialization completed");
    }

    /**
     * 初始化模型配置（直接执行，不使用事务注解）
     */
    private void initializeModelConfig() {
        // 检查是否需要初始化
        long configCount = configRepository.count();
        long serviceConfigCount = serviceConfigRepository.count();

        if (configCount == 0 && serviceConfigCount == 0) {
            log.info("Database is empty, initializing from YAML configuration...");
            initializeFromYaml();
        } else {
            log.info("Database already initialized with {} config records, {} service configs",
                    configCount, serviceConfigCount);
        }
    }

    /**
     * 从 YAML 配置初始化数据库
     */
    private void initializeFromYaml() {
        try {
            // 1. 保存完整的模型路由配置
            saveModelRouterConfig();

            // 2. 保存服务配置和实例
            saveServiceConfigs();

            log.info("YAML configuration successfully persisted to database");
        } catch (Exception e) {
            log.error("Failed to initialize database from YAML: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * 保存完整的模型路由配置到 config_data 表
     */
    private void saveModelRouterConfig() {
        Map<String, Object> configMap = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);

        String configValue;
        try {
            configValue = JacksonHelper.getObjectMapper().writeValueAsString(configMap);
        } catch (Exception e) {
            log.error("Failed to serialize config: {}", e.getMessage());
            throw new RuntimeException("Config serialization failed", e);
        }

        ConfigEntity configEntity = ConfigEntity.builder()
                .configKey("model-router-config")
                .configValue(configValue)
                .version(1)
                .isLatest(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        configRepository.save(configEntity);
        log.info("Saved model-router-config with {} service types",
                modelRouterProperties.getServices() != null ? modelRouterProperties.getServices().size() : 0);
    }

    /**
     * 保存服务配置和实例到 service_config 和 service_instance 表
     */
    private void saveServiceConfigs() {
        Map<String, ModelRouterProperties.ServiceConfig> services = modelRouterProperties.getServices();

        if (services == null || services.isEmpty()) {
            log.warn("No services configured in YAML");
            return;
        }

        int totalInstances = 0;

        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : services.entrySet()) {
            String serviceType = entry.getKey();
            ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

            // 创建服务配置实体
            ServiceConfigEntity configEntity = ServiceConfigEntity.builder()
                    .configKey("model-router-config")
                    .serviceType(serviceType)
                    .adapter(serviceConfig.getAdapter() != null ? serviceConfig.getAdapter() : modelRouterProperties.getAdapter())
                    .loadBalanceType(serviceConfig.getLoadBalance() != null ?
                            serviceConfig.getLoadBalance().getType() :
                            modelRouterProperties.getLoadBalance().getType())
                    .version(1)
                    .isLatest(true)
                    .build();

            ServiceConfigEntity savedConfig = serviceConfigRepository.save(configEntity);
            log.debug("Saved service config for type: {} with id: {}", serviceType, savedConfig.getId());

            // 保存实例
            if (serviceConfig.getInstances() != null) {
                for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                    ServiceInstanceEntity instanceEntity = ServiceInstanceEntity.builder()
                            .serviceConfigId(savedConfig.getId())
                            .instanceName(instance.getName())
                            .baseUrl(instance.getBaseUrl())
                            .path(instance.getPath())
                            .weight(instance.getWeight())
                            .status("ACTIVE")
                            .healthStatus("UNKNOWN")
                            .build();

                    serviceInstanceRepository.save(instanceEntity);
                    totalInstances++;
                    log.debug("Saved instance: {} for service: {}", instance.getName(), serviceType);
                }
            }
        }

        log.info("Saved {} service configs and {} instances", services.size(), totalInstances);
    }

    /**
     * 初始化 API Keys
     */
    private void initializeApiKeys() {
        try {
            log.info("Initializing API Keys...");
            if (apiKeyService.hasPersistedAccountConfig()) {
                log.info("Loading persisted API Keys configuration...");
                apiKeyService.loadLatestApiKeyConfig();
            } else {
                log.info("No persisted API Keys found, initializing from YAML...");
                apiKeyService.initializeApiKeyFromYaml();
            }
            log.info("API Keys initialization completed");
        } catch (Exception e) {
            log.error("Failed to initialize API Keys: {}", e.getMessage(), e);
        }
    }
}