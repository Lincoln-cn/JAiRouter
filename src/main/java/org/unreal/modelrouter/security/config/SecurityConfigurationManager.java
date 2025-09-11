package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 安全配置管理器
 * 参考 ConfigurationService 的实现模式，提供完整的安全配置管理功能
 * 支持配置的增删改查和版本管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityConfigurationManager implements SecurityConfigurationService {

    private final SecurityProperties securityProperties;
    private final StoreManager storeManager;
    private final SecurityConfigMergeService configMergeService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityConfigurationValidator validator;

    private static final String SECURITY_CONFIG_KEY = "security-config";

    // ==================== 版本管理 ====================

    /**
     * 获取所有安全配置版本号
     * @return 版本号列表
     */
    public List<Integer> getAllSecurityVersions() {
        return storeManager.getConfigVersions(SECURITY_CONFIG_KEY);
    }

    /**
     * 获取指定版本的安全配置
     * @param version 版本号，0表示YAML原始配置
     * @return 配置内容
     */
    public Map<String, Object> getSecurityVersionConfig(int version) {
        if (version == 0) {
            return configMergeService.getDefaultSecurityConfig(); // YAML 原始配置
        }
        return storeManager.getConfigByVersion(SECURITY_CONFIG_KEY, version);
    }

    /**
     * 保存当前安全配置为新版本
     * @param config 配置内容
     * @return 新版本号
     */
    public int saveSecurityAsNewVersion(Map<String, Object> config) {
        int version = getNextSecurityVersion();
        storeManager.saveConfigVersion(SECURITY_CONFIG_KEY, config, version);
        log.info("已保存安全配置为新版本：{}", version);
        return version;
    }

    /**
     * 应用指定版本的安全配置
     * @param version 版本号
     */
    public void applySecurityVersion(int version) {
        Map<String, Object> config = getSecurityVersionConfig(version);
        if (config == null) {
            throw new IllegalArgumentException("安全配置版本不存在: " + version);
        }
        
        // 验证配置
        if (!validateSecurityConfig(config)) {
            throw new IllegalArgumentException("安全配置版本 " + version + " 验证失败");
        }
        
        storeManager.saveConfig(SECURITY_CONFIG_KEY, new HashMap<>(config));
        refreshSecurityRuntimeConfig(config);
        
        log.info("已应用安全配置版本：{}", version);
    }

    /**
     * 获取当前最新安全配置版本号
     * @return 当前版本号
     */
    public int getCurrentSecurityVersion() {
        List<Integer> versions = getAllSecurityVersions();
        return versions.isEmpty() ? 0 : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * 获取下一个版本号
     * @return 下一个版本号
     */
    private int getNextSecurityVersion() {
        return getAllSecurityVersions().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    // ==================== SecurityConfigurationService 实现 ====================

    @Override
    public Mono<Void> updateApiKeys(List<ApiKeyInfo> apiKeys) {
        return Mono.fromRunnable(() -> {
            log.info("更新API Keys配置，数量: {}", apiKeys.size());
            
            try {
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedSecurityConfig();
                
                // 更新API Keys配置
                Map<String, Object> apiKeyConfig = getOrCreateApiKeyConfig(currentConfig);
                apiKeyConfig.put("keys", apiKeys);
                
                // 保存为新版本
                saveSecurityAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                securityProperties.getApiKey().setKeys(apiKeys);
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("api-keys", null, apiKeys);
                
                log.info("API Keys配置更新成功");
                
            } catch (Exception e) {
                log.error("更新API Keys配置失败", e);
                throw new RuntimeException("更新API Keys配置失败", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> updateSanitizationRules(List<SanitizationRule> rules) {
        return Mono.fromRunnable(() -> {
            log.info("更新脱敏规则配置，数量: {}", rules.size());
            
            try {
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedSecurityConfig();
                
                // 更新脱敏规则配置
                Map<String, Object> sanitizationConfig = getOrCreateSanitizationConfig(currentConfig);
                sanitizationConfig.put("rules", rules);
                
                // 保存为新版本
                saveSecurityAsNewVersion(currentConfig);
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("sanitization-rules", null, rules);
                
                log.info("脱敏规则配置更新成功");
                
            } catch (Exception e) {
                log.error("更新脱敏规则配置失败", e);
                throw new RuntimeException("更新脱敏规则配置失败", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> updateJwtConfig(SecurityProperties.JwtConfig jwtConfig) {
        return Mono.fromRunnable(() -> {
            log.info("更新JWT配置");
            
            try {
                // 验证JWT配置
                validateJwtConfig(jwtConfig);
                
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedSecurityConfig();
                
                // 记录变更前的值
                SecurityProperties.JwtConfig oldConfig = copyJwtConfig(securityProperties.getJwt());
                
                // 更新JWT配置
                Map<String, Object> jwtConfigMap = convertJwtConfigToMap(jwtConfig);
                currentConfig.put("jwt", jwtConfigMap);
                
                // 保存为新版本
                saveSecurityAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                updateJwtConfigProperties(jwtConfig);
                
                // 发布配置变更事件
                publishConfigurationChangeEvent("jwt-config", oldConfig, jwtConfig);
                
                log.info("JWT配置更新成功");
                
            } catch (Exception e) {
                log.error("更新JWT配置失败", e);
                throw new RuntimeException("更新JWT配置失败", e);
            }
        }).then();
    }

    @Override
    public Mono<SecurityProperties> getCurrentConfiguration() {
        return Mono.fromCallable(() -> {
            log.debug("获取当前安全配置");
            return copySecurityProperties(securityProperties);
        });
    }

    @Override
    public Mono<Boolean> validateConfiguration(SecurityProperties properties) {
        return Mono.fromCallable(() -> {
            log.debug("验证安全配置");
            
            try {
                SecurityConfigurationValidator.ValidationResult result = 
                        validator.validateConfiguration(properties);
                
                if (!result.isValid()) {
                    log.warn("安全配置验证失败: {}", result.getErrors());
                }
                
                return result.isValid();
                
            } catch (Exception e) {
                log.warn("安全配置验证失败", e);
                return false;
            }
        });
    }

    @Override
    public Mono<String> backupConfiguration() {
        return Mono.fromCallable(() -> {
            log.info("创建安全配置备份");
            
            try {
                String backupId = "security-backup-" + System.currentTimeMillis();
                Map<String, Object> currentConfig = getCurrentPersistedSecurityConfig();
                
                // 创建备份（可以扩展为更复杂的备份逻辑）
                storeManager.saveConfig("security-backup-" + backupId, currentConfig);
                
                log.info("安全配置备份创建完成: {}", backupId);
                return backupId;
                
            } catch (Exception e) {
                log.error("创建安全配置备份失败", e);
                throw new RuntimeException("创建安全配置备份失败", e);
            }
        });
    }

    @Override
    public Mono<Void> restoreConfiguration(String backupId) {
        return Mono.fromRunnable(() -> {
            log.info("恢复安全配置，备份ID: {}", backupId);
            
            try {
                Map<String, Object> backupConfig = storeManager.getConfig("security-backup-" + backupId);
                if (backupConfig == null) {
                    throw new IllegalArgumentException("备份不存在: " + backupId);
                }
                
                // 验证备份配置
                if (!validateSecurityConfig(backupConfig)) {
                    throw new IllegalArgumentException("备份配置验证失败: " + backupId);
                }
                
                // 保存为新版本
                saveSecurityAsNewVersion(backupConfig);
                
                // 刷新运行时配置
                refreshSecurityRuntimeConfig(backupConfig);
                
                log.info("安全配置恢复完成，备份ID: {}", backupId);
                
            } catch (Exception e) {
                log.error("恢复安全配置失败，备份ID: " + backupId, e);
                throw new RuntimeException("恢复安全配置失败", e);
            }
        }).then();
    }

    @Override
    public Mono<Void> reloadConfiguration() {
        return Mono.fromRunnable(() -> {
            log.info("重新加载安全配置");
            
            try {
                Map<String, Object> latestConfig = configMergeService.getMergedSecurityConfig();
                refreshSecurityRuntimeConfig(latestConfig);
                
                // 发布配置重新加载事件
                publishConfigurationChangeEvent("config-reload", null, securityProperties);
                
                log.info("安全配置重新加载完成");
                
            } catch (Exception e) {
                log.error("重新加载安全配置失败", e);
                throw new RuntimeException("重新加载安全配置失败", e);
            }
        }).then();
    }

    @Override
    public Mono<List<SecurityConfigurationChangeEvent>> getConfigurationHistory(int limit) {
        return Mono.fromCallable(() -> {
            log.debug("获取安全配置变更历史，限制数量: {}", limit);
            
            // 这里可以从版本历史中构建变更事件列表
            // 暂时返回空列表
            return List.of();
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前持久化的安全配置
     */
    private Map<String, Object> getCurrentPersistedSecurityConfig() {
        Map<String, Object> config = configMergeService.getPersistedSecurityConfig();
        if (config.isEmpty()) {
            // 如果没有持久化配置，使用默认配置
            config = configMergeService.getDefaultSecurityConfig();
        }
        return new HashMap<>(config);
    }

    /**
     * 获取或创建API Key配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateApiKeyConfig(Map<String, Object> config) {
        return (Map<String, Object>) config.computeIfAbsent("apiKey", k -> new HashMap<>());
    }

    /**
     * 获取或创建脱敏配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateSanitizationConfig(Map<String, Object> config) {
        return (Map<String, Object>) config.computeIfAbsent("sanitization", k -> new HashMap<>());
    }

    /**
     * 验证安全配置
     */
    private boolean validateSecurityConfig(Map<String, Object> config) {
        try {
            // 这里可以添加具体的验证逻辑
            return config != null && !config.isEmpty();
        } catch (Exception e) {
            log.error("验证安全配置失败", e);
            return false;
        }
    }

    /**
     * 刷新运行时安全配置
     */
    private void refreshSecurityRuntimeConfig(Map<String, Object> config) {
        try {
            // 更新SecurityProperties中的配置
            updateSecurityPropertiesFromMap(config);
            
            log.debug("运行时安全配置已刷新");
        } catch (Exception e) {
            log.warn("刷新运行时安全配置失败", e);
        }
    }

    /**
     * 从Map更新SecurityProperties
     */
    @SuppressWarnings("unchecked")
    private void updateSecurityPropertiesFromMap(Map<String, Object> config) {
        // 更新API Key配置
        if (config.containsKey("apiKey")) {
            Map<String, Object> apiKeyConfig = (Map<String, Object>) config.get("apiKey");
            if (apiKeyConfig.containsKey("keys")) {
                List<ApiKeyInfo> keys = (List<ApiKeyInfo>) apiKeyConfig.get("keys");
                securityProperties.getApiKey().setKeys(keys);
            }
        }
        
        // 更新JWT配置
        if (config.containsKey("jwt")) {
            Map<String, Object> jwtConfig = (Map<String, Object>) config.get("jwt");
            updateJwtConfigFromMap(jwtConfig);
        }
        
        // 可以继续添加其他配置的更新逻辑
    }

    /**
     * 从Map更新JWT配置
     */
    private void updateJwtConfigFromMap(Map<String, Object> jwtConfigMap) {
        SecurityProperties.JwtConfig jwtConfig = securityProperties.getJwt();
        
        if (jwtConfigMap.containsKey("enabled")) {
            jwtConfig.setEnabled((Boolean) jwtConfigMap.get("enabled"));
        }
        if (jwtConfigMap.containsKey("secret")) {
            jwtConfig.setSecret((String) jwtConfigMap.get("secret"));
        }
        if (jwtConfigMap.containsKey("algorithm")) {
            jwtConfig.setAlgorithm((String) jwtConfigMap.get("algorithm"));
        }
        // 继续添加其他字段的更新
    }

    /**
     * 将JWT配置转换为Map
     */
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

    /**
     * 验证JWT配置
     */
    private void validateJwtConfig(SecurityProperties.JwtConfig jwtConfig) {
        if (jwtConfig.isEnabled()) {
            if (jwtConfig.getSecret() == null || jwtConfig.getSecret().length() < 32) {
                throw new IllegalArgumentException("JWT密钥长度至少32个字符");
            }
            if (jwtConfig.getExpirationMinutes() <= 0) {
                throw new IllegalArgumentException("JWT过期时间必须大于0");
            }
        }
    }

    /**
     * 复制JWT配置
     */
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

    /**
     * 更新JWT配置属性
     */
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

    /**
     * 复制SecurityProperties
     */
    private SecurityProperties copySecurityProperties(SecurityProperties source) {
        // 这里可以实现深拷贝逻辑
        // 暂时返回原对象
        return source;
    }

    /**
     * 发布配置变更事件
     */
    private void publishConfigurationChangeEvent(String configType, Object oldValue, Object newValue) {
        try {
            SecurityConfigurationChangeEvent event = new SecurityConfigurationChangeEvent(
                this, 
                "change-" + System.currentTimeMillis(), 
                configType, 
                oldValue, 
                newValue
            );
            eventPublisher.publishEvent(event);
            log.debug("已发布安全配置变更事件: {}", configType);
        } catch (Exception e) {
            log.warn("发布安全配置变更事件失败", e);
        }
    }
}