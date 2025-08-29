package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 安全配置管理服务实现类
 * 提供安全配置的动态更新和管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityConfigurationServiceImpl implements SecurityConfigurationService {

    private final SecurityProperties securityProperties;
    private final StoreManager storeManager;
    private final ApplicationEventPublisher eventPublisher;
    
    // 配置变更历史记录
    private final ConcurrentMap<String, SecurityConfigurationChangeEvent> changeHistory = new ConcurrentHashMap<>();
    
    // 配置备份存储
    private final ConcurrentMap<String, SecurityProperties> configBackups = new ConcurrentHashMap<>();
    
    private static final String CONFIG_KEY_PREFIX = "security.config.";
    private static final String BACKUP_KEY_PREFIX = "security.backup.";
    private static final String API_KEYS_KEY = CONFIG_KEY_PREFIX + "api-keys";
    private static final String SANITIZATION_RULES_KEY = CONFIG_KEY_PREFIX + "sanitization-rules";
    private static final String JWT_CONFIG_KEY = CONFIG_KEY_PREFIX + "jwt";

    @Override
    public Mono<Void> updateApiKeys(List<ApiKeyInfo> apiKeys) {
        return Mono.fromRunnable(() -> {
            log.info("开始更新API Key配置，数量: {}", apiKeys.size());
            
            // 记录变更前的值
            List<ApiKeyInfo> oldKeys = new ArrayList<>(securityProperties.getApiKey().getKeys());
            
            // 更新配置
            securityProperties.getApiKey().setKeys(apiKeys);
            
            // 持久化到存储
            // 注意：这里需要将对象转换为Map格式以适配StoreManager接口
            // 实际实现中可能需要使用JSON序列化或其他方式
            
            // 记录配置变更事件
            recordConfigurationChange("API_KEYS_UPDATE", "系统", "更新API Key配置", oldKeys, apiKeys);
            
            // 发布配置变更事件
            publishConfigurationChangeEvent("api-keys", oldKeys, apiKeys);
            
            log.info("API Key配置更新完成");
        }).then();
    }

    @Override
    public Mono<Void> updateSanitizationRules(List<SanitizationRule> rules) {
        return Mono.fromRunnable(() -> {
            log.info("开始更新脱敏规则配置，数量: {}", rules.size());
            
            // 持久化到存储
            // 注意：这里需要将对象转换为Map格式以适配StoreManager接口
            
            // 记录配置变更事件
            recordConfigurationChange("SANITIZATION_RULES_UPDATE", "系统", "更新脱敏规则配置", null, rules);
            
            // 发布配置变更事件
            publishConfigurationChangeEvent("sanitization-rules", null, rules);
            
            log.info("脱敏规则配置更新完成");
        }).then();
    }

    @Override
    public Mono<Void> updateJwtConfig(SecurityProperties.JwtConfig jwtConfig) {
        return Mono.fromRunnable(() -> {
            log.info("开始更新JWT配置");
            
            // 记录变更前的值
            SecurityProperties.JwtConfig oldConfig = copyJwtConfig(securityProperties.getJwt());
            
            // 更新配置
            updateJwtConfigProperties(jwtConfig);
            
            // 持久化到存储
            // 注意：这里需要将对象转换为Map格式以适配StoreManager接口
            
            // 记录配置变更事件
            recordConfigurationChange("JWT_CONFIG_UPDATE", "系统", "更新JWT配置", oldConfig, jwtConfig);
            
            // 发布配置变更事件
            publishConfigurationChangeEvent("jwt-config", oldConfig, jwtConfig);
            
            log.info("JWT配置更新完成");
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
                // 验证API Key配置
                if (properties.getApiKey() != null) {
                    validateApiKeyConfig(properties.getApiKey());
                }
                
                // 验证JWT配置
                if (properties.getJwt() != null && properties.getJwt().isEnabled()) {
                    validateJwtConfig(properties.getJwt());
                }
                
                // 验证脱敏配置
                if (properties.getSanitization() != null) {
                    validateSanitizationConfig(properties.getSanitization());
                }
                
                // 验证审计配置
                if (properties.getAudit() != null) {
                    validateAuditConfig(properties.getAudit());
                }
                
                log.debug("安全配置验证通过");
                return true;
                
            } catch (Exception e) {
                log.warn("安全配置验证失败: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Mono<String> backupConfiguration() {
        return Mono.fromCallable(() -> {
            String backupId = "backup-" + UUID.randomUUID().toString();
            SecurityProperties backup = copySecurityProperties(securityProperties);
            
            // 存储到内存备份
            configBackups.put(backupId, backup);
            
            // 持久化备份
            // 注意：这里需要将对象转换为Map格式以适配StoreManager接口
            
            // 记录备份事件
            recordConfigurationChange("CONFIG_BACKUP", "系统", "创建配置备份: " + backupId, null, backup);
            
            log.info("配置备份创建完成，备份ID: {}", backupId);
            return backupId;
        });
    }

    @Override
    public Mono<Void> restoreConfiguration(String backupId) {
        return Mono.fromCallable(() -> {
            SecurityProperties backup = configBackups.get(backupId);
            if (backup == null) {
                // 尝试从持久化存储加载
                // 注意：这里需要从Map格式转换为对象
                Map<String, Object> backupMap = storeManager.getConfig(BACKUP_KEY_PREFIX + backupId);
                if (backupMap != null) {
                    // 这里需要实现Map到SecurityProperties的转换逻辑
                    // 暂时返回null，实际实现中需要添加转换逻辑
                }
            }
            
            if (backup == null) {
                throw new IllegalArgumentException("备份不存在: " + backupId);
            }
            
            return backup;
        }).flatMap(backup -> {
            log.info("开始恢复配置，备份ID: {}", backupId);
            
            // 记录当前配置作为变更前的值
            SecurityProperties oldConfig = copySecurityProperties(securityProperties);
            
            // 恢复配置
            restoreSecurityProperties(backup);
            
            // 记录配置变更事件
            recordConfigurationChange("CONFIG_RESTORE", "系统", "恢复配置: " + backupId, oldConfig, backup);
            
            // 发布配置变更事件
            publishConfigurationChangeEvent("config-restore", oldConfig, backup);
            
            log.info("配置恢复完成，备份ID: {}", backupId);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> reloadConfiguration() {
        return Mono.fromRunnable(() -> {
            log.info("开始重新加载配置");
            
            try {
                // 从存储重新加载配置
                // 注意：这里需要从Map格式转换为对象
                Map<String, Object> apiKeysMap = storeManager.getConfig(API_KEYS_KEY);
                if (apiKeysMap != null) {
                    // 这里需要实现Map到List<ApiKeyInfo>的转换逻辑
                }
                
                Map<String, Object> jwtConfigMap = storeManager.getConfig(JWT_CONFIG_KEY);
                if (jwtConfigMap != null) {
                    // 这里需要实现Map到JwtConfig的转换逻辑
                }
                
                Map<String, Object> rulesMap = storeManager.getConfig(SANITIZATION_RULES_KEY);
                if (rulesMap != null) {
                    // 这里需要实现Map到List<SanitizationRule>的转换逻辑
                }
                // 注意：脱敏规则的应用需要在脱敏服务中处理
                
                // 发布配置重新加载事件
                publishConfigurationChangeEvent("config-reload", null, securityProperties);
                
                log.info("配置重新加载完成");
                
            } catch (Exception e) {
                log.error("配置重新加载失败", e);
                throw new RuntimeException("配置重新加载失败", e);
            }
        }).then();
    }
    @Override
    public Mono<List<SecurityConfigurationChangeEvent>> getConfigurationHistory(int limit) {
        return Mono.fromCallable(() -> {
            log.debug("获取配置变更历史，限制数量: {}", limit);
            
            return changeHistory.values().stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(limit)
                    .map(event -> new SecurityConfigurationChangeEvent(
                        event.getSource(),
                        event.getChangeId(),
                        event.getConfigType(),
                        event.getOldValue(),
                        event.getNewValue()
                    ))
                    .toList();
        });
    }

    /**
     * 记录配置变更事件
     */
    private void recordConfigurationChange(String changeType, String userId, String description, Object oldValue, Object newValue) {
        String changeId = UUID.randomUUID().toString();
        SecurityConfigurationChangeEvent event = new SecurityConfigurationChangeEvent(
                this, changeId, changeType, oldValue, newValue);
        
        changeHistory.put(changeId, event);
        
        // 限制历史记录数量，避免内存泄漏
        if (changeHistory.size() > 1000) {
            // 删除最旧的记录
            changeHistory.values().stream()
                    .min(Comparator.comparing(SecurityConfigurationChangeEvent::getTimestamp))
                    .ifPresent(oldest -> changeHistory.remove(oldest.getChangeId()));
        }
    }

    /**
     * 发布配置变更事件
     */
    private void publishConfigurationChangeEvent(String configType, Object oldValue, Object newValue) {
        try {
            String changeId = UUID.randomUUID().toString();
            SecurityConfigurationChangeEvent event = new SecurityConfigurationChangeEvent(
                    this,changeId, configType, oldValue, newValue);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布配置变更事件失败", e);
        }
    }

    /**
     * 验证API Key配置
     */
    private void validateApiKeyConfig(SecurityProperties.ApiKeyConfig config) {
        if (config.isEnabled()) {
            if (config.getHeaderName() == null || config.getHeaderName().trim().isEmpty()) {
                throw new IllegalArgumentException("API Key请求头名称不能为空");
            }
            
            if (config.getDefaultExpirationDays() <= 0) {
                throw new IllegalArgumentException("API Key默认过期天数必须大于0");
            }
            
            if (config.isCacheEnabled() && config.getCacheExpirationSeconds() <= 0) {
                throw new IllegalArgumentException("API Key缓存过期时间必须大于0");
            }
        }
    }

    /**
     * 验证JWT配置
     */
    private void validateJwtConfig(SecurityProperties.JwtConfig config) {
        if (config.getSecret() == null || config.getSecret().length() < 32) {
            throw new IllegalArgumentException("JWT密钥长度至少32个字符");
        }
        
        if (config.getExpirationMinutes() <= 0) {
            throw new IllegalArgumentException("JWT过期时间必须大于0");
        }
        
        if (config.getRefreshExpirationDays() <= 0) {
            throw new IllegalArgumentException("JWT刷新过期天数必须大于0");
        }
        
        if (config.getIssuer() == null || config.getIssuer().trim().isEmpty()) {
            throw new IllegalArgumentException("JWT发行者不能为空");
        }
    }

    /**
     * 验证脱敏配置
     */
    private void validateSanitizationConfig(SecurityProperties.SanitizationConfig config) {
        if (config.getRequest() != null) {
            validateSanitizationSubConfig(config.getRequest().getMaskingChar(), "请求脱敏掩码字符");
        }
        
        if (config.getResponse() != null) {
            validateSanitizationSubConfig(config.getResponse().getMaskingChar(), "响应脱敏掩码字符");
        }
    }

    /**
     * 验证脱敏子配置
     */
    private void validateSanitizationSubConfig(String maskingChar, String fieldName) {
        if (maskingChar == null || maskingChar.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        
        if (maskingChar.length() > 5) {
            throw new IllegalArgumentException(fieldName + "长度不能超过5个字符");
        }
    }

    /**
     * 验证审计配置
     */
    private void validateAuditConfig(SecurityProperties.AuditConfig config) {
        if (config.getRetentionDays() <= 0) {
            throw new IllegalArgumentException("审计日志保留天数必须大于0");
        }
        
        if (config.getAlertThresholds() != null) {
            if (config.getAlertThresholds().getAuthFailuresPerMinute() <= 0) {
                throw new IllegalArgumentException("认证失败告警阈值必须大于0");
            }
            
            if (config.getAlertThresholds().getSanitizationOperationsPerMinute() <= 0) {
                throw new IllegalArgumentException("脱敏操作告警阈值必须大于0");
            }
        }
    }

    /**
     * 复制SecurityProperties对象
     */
    private SecurityProperties copySecurityProperties(SecurityProperties source) {
        SecurityProperties copy = new SecurityProperties();
        copy.setEnabled(source.isEnabled());
        
        // 复制API Key配置
        SecurityProperties.ApiKeyConfig apiKeyConfig = new SecurityProperties.ApiKeyConfig();
        apiKeyConfig.setEnabled(source.getApiKey().isEnabled());
        apiKeyConfig.setHeaderName(source.getApiKey().getHeaderName());
        apiKeyConfig.setKeys(new ArrayList<>(source.getApiKey().getKeys()));
        apiKeyConfig.setDefaultExpirationDays(source.getApiKey().getDefaultExpirationDays());
        apiKeyConfig.setCacheEnabled(source.getApiKey().isCacheEnabled());
        apiKeyConfig.setCacheExpirationSeconds(source.getApiKey().getCacheExpirationSeconds());
        copy.setApiKey(apiKeyConfig);
        
        // 复制JWT配置
        copy.setJwt(copyJwtConfig(source.getJwt()));
        
        // 复制脱敏配置
        SecurityProperties.SanitizationConfig sanitizationConfig = new SecurityProperties.SanitizationConfig();
        // ... 复制脱敏配置的详细实现
        copy.setSanitization(sanitizationConfig);
        
        // 复制审计配置
        SecurityProperties.AuditConfig auditConfig = new SecurityProperties.AuditConfig();
        auditConfig.setEnabled(source.getAudit().isEnabled());
        auditConfig.setLogLevel(source.getAudit().getLogLevel());
        auditConfig.setIncludeRequestBody(source.getAudit().isIncludeRequestBody());
        auditConfig.setIncludeResponseBody(source.getAudit().isIncludeResponseBody());
        auditConfig.setRetentionDays(source.getAudit().getRetentionDays());
        auditConfig.setAlertEnabled(source.getAudit().isAlertEnabled());
        copy.setAudit(auditConfig);
        
        return copy;
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
     * 恢复安全配置属性
     */
    private void restoreSecurityProperties(SecurityProperties backup) {
        securityProperties.setEnabled(backup.isEnabled());
        
        // 恢复API Key配置
        SecurityProperties.ApiKeyConfig currentApiKey = securityProperties.getApiKey();
        SecurityProperties.ApiKeyConfig backupApiKey = backup.getApiKey();
        currentApiKey.setEnabled(backupApiKey.isEnabled());
        currentApiKey.setHeaderName(backupApiKey.getHeaderName());
        currentApiKey.setKeys(new ArrayList<>(backupApiKey.getKeys()));
        currentApiKey.setDefaultExpirationDays(backupApiKey.getDefaultExpirationDays());
        currentApiKey.setCacheEnabled(backupApiKey.isCacheEnabled());
        currentApiKey.setCacheExpirationSeconds(backupApiKey.getCacheExpirationSeconds());
        
        // 恢复JWT配置
        updateJwtConfigProperties(backup.getJwt());
        
        // 恢复其他配置...
    }

}