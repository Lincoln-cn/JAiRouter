package org.unreal.modelrouter.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtAccountProperties;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.util.JacksonHelper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
    private final JwtAccountRepository jwtAccountRepository;
    private final ModelRouterProperties modelRouterProperties;
    private final ConfigConverterHelper configConverterHelper;
    private final ApiKeyService apiKeyService;
    private final SecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initialize() {
        log.info("Initializing JPA database...");

        // 1. 初始化模型配置
        initializeModelConfig();

        // 2. 数据迁移：为 instanceId 为空的实例生成 UUID
        migrateInstanceIds();

        // 3. 初始化 API Keys
        initializeApiKeys();

        // 4. 初始化 JWT 账户
        initializeJwtAccounts();

        log.info("JPA database initialization completed");
    }

    /**
     * 数据迁移：为 instanceId 为空的实例生成 UUID
     * v2.x: 修复旧数据没有 instanceId 导致熔断器 key 不一致的问题
     */
    private void migrateInstanceIds() {
        try {
            log.info("Starting instanceId migration check...");
            List<ServiceInstanceEntity> allInstances = serviceInstanceRepository.findAll();
            log.info("Total instances found: {}", allInstances.size());
            
            List<ServiceInstanceEntity> instancesWithoutId = allInstances.stream()
                    .filter(inst -> inst.getInstanceId() == null || inst.getInstanceId().isEmpty())
                    .toList();

            if (instancesWithoutId.isEmpty()) {
                log.info("All instances already have instanceId, migration skipped");
                return;
            }

            log.info("Migrating {} instances without instanceId...", instancesWithoutId.size());
            for (ServiceInstanceEntity entity : instancesWithoutId) {
                String newUuid = java.util.UUID.randomUUID().toString();
                entity.setInstanceId(newUuid);
                serviceInstanceRepository.save(entity);
                log.info("Generated instanceId {} for instance: {}", newUuid, entity.getInstanceName());
            }
            log.info("InstanceId migration completed for {} instances", instancesWithoutId.size());
        } catch (Exception e) {
            log.error("Failed to migrate instanceIds: {}", e.getMessage(), e);
        }
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
     * v1.5.4: 添加元数据用于版本管理显示
     */
    private void saveModelRouterConfig() {
        Map<String, Object> configMap = configConverterHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        
        // 添加初始化元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "init");
        metadata.put("operationDetail", "系统初始化配置");
        metadata.put("timestamp", System.currentTimeMillis());
        configMap.put("_metadata", metadata);

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
                    .adapter(serviceConfig.getAdapter() != null
                            ? serviceConfig.getAdapter()
                            : modelRouterProperties.getAdapter())
                    .loadBalanceType(serviceConfig.getLoadBalance() != null
                            ? serviceConfig.getLoadBalance().getType()
                            : modelRouterProperties.getLoadBalance().getType())
                    .version(1)
                    .isLatest(true)
                    .build();

            ServiceConfigEntity savedConfig = serviceConfigRepository.save(configEntity);
            log.debug("Saved service config for type: {} with id: {}", serviceType, savedConfig.getId());

            // 保存实例
            if (serviceConfig.getInstances() != null) {
                for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                    // v2.x: 如果 instanceId 为空，生成 UUID
                    String instanceUuid = instance.getInstanceId();
                    if (instanceUuid == null || instanceUuid.isEmpty()) {
                        instanceUuid = java.util.UUID.randomUUID().toString();
                        log.info("Generated instanceId {} for instance: {}", instanceUuid, instance.getName());
                    }
                    
                    ServiceInstanceEntity instanceEntity = ServiceInstanceEntity.builder()
                            .serviceConfigId(savedConfig.getId())
                            .instanceName(instance.getName())
                            .instanceId(instanceUuid)
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
     * v2.x 修复：支持环境变量强制从 YAML 初始化，并合并 YAML 和持久化数据
     */
    private void initializeApiKeys() {
        try {
            log.info("Initializing API Keys...");
            
            // 检查环境变量是否强制从 YAML 初始化
            String forceInitFromYaml = System.getenv("FORCE_API_KEY_INIT_FROM_YAML");
            boolean forceInit = "true".equalsIgnoreCase(forceInitFromYaml);
            
            if (forceInit) {
                log.info("FORCE_API_KEY_INIT_FROM_YAML=true, forcing initialization from YAML...");
                apiKeyService.initializeApiKeyFromYaml();
            } else if (apiKeyService.hasPersistedAccountConfig()) {
                log.info("Loading persisted API Keys configuration and merging with YAML...");
                // 加载持久化配置并与 YAML 合并（YAML 优先）
                apiKeyService.loadAndMergeWithYaml();
            } else {
                log.info("No persisted API Keys found, initializing from YAML...");
                apiKeyService.initializeApiKeyFromYaml();
            }
            log.info("API Keys initialization completed");
        } catch (Exception e) {
            log.error("Failed to initialize API Keys: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化 JWT 账户
     * 从 YAML 配置加载账户到数据库
     */
    private void initializeJwtAccounts() {
        try {
            log.info("Initializing JWT accounts...");

            // 检查数据库是否已有账户
            long accountCount = jwtAccountRepository.count();
            if (accountCount > 0) {
                log.info("Database already has {} JWT accounts, skipping initialization", accountCount);
                return;
            }

            // 从 YAML 配置获取账户列表
            List<JwtAccountProperties> accounts = securityProperties.getJwt().getAccounts();
            if (accounts == null || accounts.isEmpty()) {
                log.warn("No JWT accounts configured in YAML, skipping initialization");
                return;
            }

            // 保存账户到数据库
            int savedCount = 0;
            for (JwtAccountProperties account : accounts) {
                if (jwtAccountRepository.existsByUsername(account.getUsername())) {
                    log.debug("JWT account {} already exists, skipping", account.getUsername());
                    continue;
                }

                // 处理密码（支持 {noop} 前缀表示明文密码）
                String password = account.getPassword();
                String encodedPassword;
                if (password != null && password.startsWith("{noop}")) {
                    // 明文密码，需要加密存储
                    encodedPassword = passwordEncoder.encode(password.substring(6));
                } else {
                    // 已经是明文，直接加密
                    encodedPassword = passwordEncoder.encode(password);
                }

                // 将 roles 转为 JSON 字符串存储
                String rolesJson;
                try {
                    rolesJson = objectMapper.writeValueAsString(account.getRoles());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize roles for account {}: {}", account.getUsername(), e.getMessage());
                    rolesJson = "[]";
                }

                JwtAccountEntity entity = JwtAccountEntity.builder()
                        .username(account.getUsername())
                        .password(encodedPassword)
                        .roles(rolesJson)
                        .enabled(account.isEnabled())
                        .build();

                jwtAccountRepository.save(entity);
                savedCount++;
                log.info("Created JWT account: {} with roles: {}", account.getUsername(), account.getRoles());
            }

            log.info("JWT accounts initialization completed, saved {} accounts", savedCount);
        } catch (Exception e) {
            log.error("Failed to initialize JWT accounts: {}", e.getMessage(), e);
        }
    }
}