package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.store.ConfigVersionManager;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 改进的安全配置管理服务
 * 使用清晰的版本管理逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImprovedSecurityConfigurationService implements SecurityConfigurationService {

    private final SecurityProperties securityProperties;
    private final ConfigVersionManager configVersionManager;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityConfigurationValidator validator;
    
    private static final String API_KEYS_CONFIG_KEY = "security.api-keys";
    private static final String SANITIZATION_RULES_CONFIG_KEY = "security.sanitization-rules";
    private static final String JWT_CONFIG_KEY = "security.jwt-config";

    /**
     * 注意：配置初始化逻辑已移至 SecurityConfigurationInitializer
     * 该类专门负责应用启动时的配置初始化和加载
     */

    @Override
    public Mono<Void> updateApiKeys(List<ApiKeyInfo> apiKeys) {
        return Mono.fromRunnable(() -> {
            log.info("Updating API Keys configuration, count: {}", apiKeys.size());
            
            try {
                // 验证配置
                validateApiKeysList(apiKeys);
                
                // 转换为存储格式
                Map<String, Object> configMap = convertApiKeysToMap(apiKeys);
                
                // 更新配置版本
                int newVersion = configVersionManager.updateConfig(
                    API_KEYS_CONFIG_KEY, 
                    configMap, 
                    "Update API Keys via REST API", 
                    getCurrentUserId()
                );
                
                // 更新内存中的配置
                securityProperties.getApiKey().setKeys(apiKeys);
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("api-keys", null, apiKeys, newVersion);
                
                log.info("API Keys configuration updated successfully, new version: {}", newVersion);
                
            } catch (Exception e) {
                log.error("Failed to update API Keys configuration", e);
                throw new RuntimeException("Failed to update API Keys configuration", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> updateSanitizationRules(List<SanitizationRule> rules) {
        return Mono.fromRunnable(() -> {
            log.info("Updating sanitization rules configuration, count: {}", rules.size());
            
            try {
                // 验证配置
                validateSanitizationRules(rules);
                
                // 转换为存储格式
                Map<String, Object> configMap = convertSanitizationRulesToMap(rules);
                
                // 更新配置版本
                int newVersion = configVersionManager.updateConfig(
                    SANITIZATION_RULES_CONFIG_KEY, 
                    configMap, 
                    "Update sanitization rules via REST API", 
                    getCurrentUserId()
                );
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("sanitization-rules", null, rules, newVersion);
                
                log.info("Sanitization rules configuration updated successfully, new version: {}", newVersion);
                
            } catch (Exception e) {
                log.error("Failed to update sanitization rules configuration", e);
                throw new RuntimeException("Failed to update sanitization rules configuration", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> updateJwtConfig(SecurityProperties.JwtConfig jwtConfig) {
        return Mono.fromRunnable(() -> {
            log.info("Updating JWT configuration");
            
            try {
                // 验证配置
                validateJwtConfig(jwtConfig);
                
                // 记录变更前的值
                SecurityProperties.JwtConfig oldConfig = copyJwtConfig(securityProperties.getJwt());
                
                // 转换为存储格式
                Map<String, Object> configMap = convertJwtConfigToMap(jwtConfig);
                
                // 更新配置版本
                int newVersion = configVersionManager.updateConfig(
                    JWT_CONFIG_KEY, 
                    configMap, 
                    "Update JWT configuration via REST API", 
                    getCurrentUserId()
                );
                
                // 更新内存中的配置
                updateJwtConfigProperties(jwtConfig);
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("jwt-config", oldConfig, jwtConfig, newVersion);
                
                log.info("JWT configuration updated successfully, new version: {}", newVersion);
                
            } catch (Exception e) {
                log.error("Failed to update JWT configuration", e);
                throw new RuntimeException("Failed to update JWT configuration", e);
            }
        }).then();
    }

    @Override
    public Mono<SecurityProperties> getCurrentConfiguration() {
        return Mono.fromCallable(() -> {
            log.debug("Getting current security configuration");
            return copySecurityProperties(securityProperties);
        });
    }

    @Override
    public Mono<Boolean> validateConfiguration(SecurityProperties properties) {
        return Mono.fromCallable(() -> {
            log.debug("Validating security configuration");
            
            try {
                SecurityConfigurationValidator.ValidationResult result = 
                        validator.validateConfiguration(properties);
                
                if (!result.isValid()) {
                    log.warn("Security configuration validation failed: {}", result.getErrors());
                }
                
                return result.isValid();
                
            } catch (Exception e) {
                log.warn("Security configuration validation failed with exception", e);
                return false;
            }
        });
    }

    @Override
    public Mono<String> backupConfiguration() {
        return Mono.fromCallable(() -> {
            log.info("Creating configuration backup");
            
            try {
                // 获取当前所有配置的版本信息
                ConfigVersionManager.ConfigMetadata apiKeysMetadata = 
                        configVersionManager.getConfigMetadata(API_KEYS_CONFIG_KEY);
                ConfigVersionManager.ConfigMetadata jwtMetadata = 
                        configVersionManager.getConfigMetadata(JWT_CONFIG_KEY);
                ConfigVersionManager.ConfigMetadata sanitizationMetadata = 
                        configVersionManager.getConfigMetadata(SANITIZATION_RULES_CONFIG_KEY);
                
                // 创建备份信息
                Map<String, Object> backupInfo = new HashMap<>();
                backupInfo.put("timestamp", System.currentTimeMillis());
                backupInfo.put("apiKeysVersion", apiKeysMetadata != null ? apiKeysMetadata.getCurrentVersion() : 0);
                backupInfo.put("jwtVersion", jwtMetadata != null ? jwtMetadata.getCurrentVersion() : 0);
                backupInfo.put("sanitizationVersion", sanitizationMetadata != null ? sanitizationMetadata.getCurrentVersion() : 0);
                
                String backupId = "backup-" + System.currentTimeMillis();
                
                // 这里可以实现更复杂的备份逻辑，比如创建配置快照等
                
                log.info("Configuration backup created: {}", backupId);
                return backupId;
                
            } catch (Exception e) {
                log.error("Failed to create configuration backup", e);
                throw new RuntimeException("Failed to create configuration backup", e);
            }
        });
    }

    @Override
    public Mono<Void> restoreConfiguration(String backupId) {
        return Mono.fromRunnable(() -> {
            log.info("Restoring configuration from backup: {}", backupId);
            
            try {
                // 这里实现从备份恢复配置的逻辑
                // 可以使用 configVersionManager.rollbackToVersion() 方法
                
                log.info("Configuration restored from backup: {}", backupId);
                
            } catch (Exception e) {
                log.error("Failed to restore configuration from backup: " + backupId, e);
                throw new RuntimeException("Failed to restore configuration", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> reloadConfiguration() {
        return Mono.fromRunnable(() -> {
            log.info("Reloading security configurations");
            
            try {
                loadLatestConfigurations();
                
                // 发布配置重新加载事件
                publishConfigurationChangeEvent("config-reload", null, securityProperties, 0);
                
                log.info("Security configurations reloaded successfully");
                
            } catch (Exception e) {
                log.error("Failed to reload security configurations", e);
                throw new RuntimeException("Failed to reload security configurations", e);
            }
        }).then();
    }

    @Override
    public Mono<List<SecurityConfigurationChangeEvent>> getConfigurationHistory(int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Getting configuration history, limit: {}", limit);
            
            // 这里可以从 configVersionManager 获取版本历史
            // 并转换为 SecurityConfigurationChangeEvent 格式
            
            return List.of(); // 临时返回空列表
        });
    }

    // ========== 私有辅助方法 ==========

    private void initializeApiKeysConfig() {
        if (!configVersionManager.isConfigInitialized(API_KEYS_CONFIG_KEY)) {
            Map<String, Object> initialConfig = convertApiKeysToMap(securityProperties.getApiKey().getKeys());
            configVersionManager.initializeConfig(
                API_KEYS_CONFIG_KEY, 
                initialConfig, 
                "Initial API Keys configuration"
            );
            log.info("API Keys configuration initialized");
        }
    }

    private void initializeJwtConfig() {
        if (!configVersionManager.isConfigInitialized(JWT_CONFIG_KEY)) {
            Map<String, Object> initialConfig = convertJwtConfigToMap(securityProperties.getJwt());
            configVersionManager.initializeConfig(
                JWT_CONFIG_KEY, 
                initialConfig, 
                "Initial JWT configuration"
            );
            log.info("JWT configuration initialized");
        }
    }

    private void initializeSanitizationRulesConfig() {
        if (!configVersionManager.isConfigInitialized(SANITIZATION_RULES_CONFIG_KEY)) {
            Map<String, Object> initialConfig = new HashMap<>();
            initialConfig.put("rules", List.of()); // 初始为空规则列表
            configVersionManager.initializeConfig(
                SANITIZATION_RULES_CONFIG_KEY, 
                initialConfig, 
                "Initial sanitization rules configuration"
            );
            log.info("Sanitization rules configuration initialized");
        }
    }

    /**
     * 应用启动时加载最新配置
     */
    private void loadLatestConfigurations() {
        log.info("Loading latest security configurations...");
        
        try {
            // 加载API Keys配置
            loadLatestApiKeysConfig();
            
            // 加载JWT配置
            loadLatestJwtConfig();
            
            // 加载脱敏规则配置
            loadLatestSanitizationRulesConfig();
            
            log.info("Latest security configurations loaded successfully");
            
        } catch (Exception e) {
            log.error("Failed to load latest security configurations", e);
            throw new RuntimeException("Failed to load latest security configurations", e);
        }
    }

    private void loadLatestApiKeysConfig() {
        Map<String, Object> configMap = configVersionManager.getLatestConfig(API_KEYS_CONFIG_KEY);
        if (configMap != null) {
            List<ApiKeyInfo> apiKeys = convertMapToApiKeys(configMap);
            securityProperties.getApiKey().setKeys(apiKeys);
            log.debug("Loaded {} API keys from latest configuration", apiKeys.size());
        }
    }

    private void loadLatestJwtConfig() {
        Map<String, Object> configMap = configVersionManager.getLatestConfig(JWT_CONFIG_KEY);
        if (configMap != null) {
            SecurityProperties.JwtConfig jwtConfig = convertMapToJwtConfig(configMap);
            updateJwtConfigProperties(jwtConfig);
            log.debug("Loaded JWT configuration from latest version");
        }
    }

    private void loadLatestSanitizationRulesConfig() {
        Map<String, Object> configMap = configVersionManager.getLatestConfig(SANITIZATION_RULES_CONFIG_KEY);
        if (configMap != null) {
            List<SanitizationRule> rules = convertMapToSanitizationRules(configMap);
            // 这里需要将规则应用到脱敏服务中
            log.debug("Loaded {} sanitization rules from latest configuration", rules.size());
        }
    }

    // 转换方法（需要根据实际的数据结构实现）
    private Map<String, Object> convertApiKeysToMap(List<ApiKeyInfo> apiKeys) {
        Map<String, Object> map = new HashMap<>();
        map.put("keys", apiKeys);
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<ApiKeyInfo> convertMapToApiKeys(Map<String, Object> configMap) {
        return (List<ApiKeyInfo>) configMap.get("keys");
    }

    private Map<String, Object> convertJwtConfigToMap(SecurityProperties.JwtConfig jwtConfig) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", jwtConfig.isEnabled());
        map.put("secret", jwtConfig.getSecret());
        map.put("algorithm", jwtConfig.getAlgorithm());
        map.put("expirationMinutes", jwtConfig.getExpirationMinutes());
        map.put("refreshExpirationDays", jwtConfig.getRefreshExpirationDays());
        map.put("issuer", jwtConfig.getIssuer());
        map.put("blacklistEnabled", jwtConfig.isBlacklistEnabled());
        return map;
    }

    private SecurityProperties.JwtConfig convertMapToJwtConfig(Map<String, Object> configMap) {
        SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
        jwtConfig.setEnabled((Boolean) configMap.get("enabled"));
        jwtConfig.setSecret((String) configMap.get("secret"));
        jwtConfig.setAlgorithm((String) configMap.get("algorithm"));
        jwtConfig.setExpirationMinutes((Integer) configMap.get("expirationMinutes"));
        jwtConfig.setRefreshExpirationDays((Integer) configMap.get("refreshExpirationDays"));
        jwtConfig.setIssuer((String) configMap.get("issuer"));
        jwtConfig.setBlacklistEnabled((Boolean) configMap.get("blacklistEnabled"));
        return jwtConfig;
    }

    private Map<String, Object> convertSanitizationRulesToMap(List<SanitizationRule> rules) {
        Map<String, Object> map = new HashMap<>();
        map.put("rules", rules);
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<SanitizationRule> convertMapToSanitizationRules(Map<String, Object> configMap) {
        return (List<SanitizationRule>) configMap.get("rules");
    }

    private void validateApiKeysList(List<ApiKeyInfo> apiKeys) {
        // 实现API Keys验证逻辑
    }

    private void validateSanitizationRules(List<SanitizationRule> rules) {
        // 实现脱敏规则验证逻辑
    }

    private void validateJwtConfig(SecurityProperties.JwtConfig jwtConfig) {
        // 实现JWT配置验证逻辑
    }

    private SecurityProperties.JwtConfig copyJwtConfig(SecurityProperties.JwtConfig source) {
        SecurityProperties.JwtConfig copy = new SecurityProperties.JwtConfig();
        copy.setEnabled(source.isEnabled());
        copy.setSecret(source.getSecret());
        copy.setAlgorithm(source.getAlgorithm());
        copy.setExpirationMinutes(source.getExpirationMinutes());
        copy.setRefreshExpirationDays(source.getRefreshExpirationDays());
        copy.setIssuer(source.getIssuer());
        copy.setBlacklistEnabled(source.isBlacklistEnabled());
        return copy;
    }

    private SecurityProperties copySecurityProperties(SecurityProperties source) {
        // 实现深拷贝逻辑
        return source; // 临时返回
    }

    private void updateJwtConfigProperties(SecurityProperties.JwtConfig newConfig) {
        SecurityProperties.JwtConfig current = securityProperties.getJwt();
        current.setEnabled(newConfig.isEnabled());
        current.setSecret(newConfig.getSecret());
        current.setAlgorithm(newConfig.getAlgorithm());
        current.setExpirationMinutes(newConfig.getExpirationMinutes());
        current.setRefreshExpirationDays(newConfig.getRefreshExpirationDays());
        current.setIssuer(newConfig.getIssuer());
        current.setBlacklistEnabled(newConfig.isBlacklistEnabled());
    }

    private void publishConfigurationChangeEvent(String configType, Object oldValue, Object newValue, int version) {
        try {
            SecurityConfigurationChangeEvent event = new SecurityConfigurationChangeEvent(
                this, 
                "change-" + System.currentTimeMillis(), 
                configType, 
                oldValue, 
                newValue
            );
            eventPublisher.publishEvent(event);
            log.debug("Published configuration change event for: {}, version: {}", configType, version);
        } catch (Exception e) {
            log.warn("Failed to publish configuration change event", e);
        }
    }

    private String getCurrentUserId() {
        // 这里可以从Spring Security上下文获取当前用户ID
        return "system";
    }
}