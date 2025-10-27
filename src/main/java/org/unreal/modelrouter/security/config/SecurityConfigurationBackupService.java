package org.unreal.modelrouter.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 安全配置备份服务
 * 提供配置备份、恢复和管理功能，支持加密存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityConfigurationBackupService {

    private final StoreManager storeManager;
    private final ConfigurationEncryptionService encryptionService;
    private final SecurityConfigurationValidator validator;
    private final ObjectMapper objectMapper;

    // 内存中的备份缓存
    private final ConcurrentMap<String, ConfigurationBackup> backupCache = new ConcurrentHashMap<>();

    private static final String BACKUP_KEY_PREFIX = "security.backup.";
    private static final String BACKUP_INDEX_KEY = "security.backup.index";
    private static final DateTimeFormatter BACKUP_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 创建配置备份
     * @param properties 安全配置
     * @param description 备份描述
     * @return 备份ID
     */
    public String createBackup(SecurityProperties properties, String description) {
        return createBackup(properties, description, "system");
    }

    /**
     * 创建配置备份
     * @param properties 安全配置
     * @param description 备份描述
     * @param userId 用户ID
     * @return 备份ID
     */
    public String createBackup(SecurityProperties properties, String description, String userId) {
        log.info("开始创建配置备份，用户: {}, 描述: {}", userId, description);

        try {
            // 验证配置
            SecurityConfigurationValidator.ValidationResult validationResult = validator.validateConfiguration(properties);
            if (!validationResult.isValid()) {
                log.warn("配置验证失败，仍将创建备份: {}", validationResult.getErrors());
            }

            // 生成备份ID
            String backupId = generateBackupId();

            // 创建备份对象
            ConfigurationBackup backup = new ConfigurationBackup();
            backup.setBackupId(backupId);
            backup.setDescription(description);
            backup.setUserId(userId);
            backup.setCreatedAt(LocalDateTime.now());
            backup.setValidationResult(validationResult);

            // 加密敏感配置
            SecurityProperties encryptedProperties = encryptSensitiveData(properties);
            backup.setConfiguration(encryptedProperties);

            // 存储备份
            storeBackup(backup);

            // 更新备份索引
            updateBackupIndex(backup);

            // 缓存备份
            backupCache.put(backupId, backup);

            log.info("配置备份创建完成，备份ID: {}", backupId);
            return backupId;

        } catch (Exception e) {
            log.error("创建配置备份失败", e);
            throw new ConfigurationBackupException("创建配置备份失败", e);
        }
    }

    /**
     * 恢复配置备份
     * @param backupId 备份ID
     * @return 恢复的配置
     */
    public SecurityProperties restoreBackup(String backupId) {
        log.info("开始恢复配置备份，备份ID: {}", backupId);

        try {
            // 获取备份
            ConfigurationBackup backup = getBackup(backupId);
            if (backup == null) {
                throw new ConfigurationBackupException("备份不存在: " + backupId);
            }

            // 解密敏感配置
            SecurityProperties decryptedProperties = decryptSensitiveData(backup.getConfiguration());

            // 验证恢复的配置
            SecurityConfigurationValidator.ValidationResult validationResult = 
                    validator.validateConfiguration(decryptedProperties);
            if (!validationResult.isValid()) {
                log.warn("恢复的配置验证失败: {}", validationResult.getErrors());
                // 可以选择是否允许恢复无效配置
            }

            log.info("配置备份恢复完成，备份ID: {}", backupId);
            return decryptedProperties;

        } catch (Exception e) {
            log.error("恢复配置备份失败，备份ID: {}", backupId, e);
            throw new ConfigurationBackupException("恢复配置备份失败", e);
        }
    }

    /**
     * 获取备份列表
     * @param limit 限制数量
     * @return 备份列表
     */
    public List<BackupInfo> getBackupList(int limit) {
        log.debug("获取备份列表，限制数量: {}", limit);

        try {
            List<BackupInfo> backupInfos = loadBackupIndex();
            
            // 按创建时间倒序排序
            backupInfos.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            
            // 限制数量
            if (limit > 0 && backupInfos.size() > limit) {
                backupInfos = backupInfos.subList(0, limit);
            }

            return backupInfos;

        } catch (Exception e) {
            log.error("获取备份列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除备份
     * @param backupId 备份ID
     */
    public void deleteBackup(String backupId) {
        log.info("开始删除配置备份，备份ID: {}", backupId);

        try {
            // 从存储中删除
            storeManager.deleteConfig(BACKUP_KEY_PREFIX + backupId);

            // 从缓存中删除
            backupCache.remove(backupId);

            // 更新备份索引
            removeFromBackupIndex(backupId);

            log.info("配置备份删除完成，备份ID: {}", backupId);

        } catch (Exception e) {
            log.error("删除配置备份失败，备份ID: {}", backupId, e);
            throw new ConfigurationBackupException("删除配置备份失败", e);
        }
    }

    /**
     * 清理过期备份
     * @param retentionDays 保留天数
     * @return 清理的备份数量
     */
    public int cleanupExpiredBackups(int retentionDays) {
        log.info("开始清理过期备份，保留天数: {}", retentionDays);

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            List<BackupInfo> allBackups = getBackupList(0);
            
            int cleanedCount = 0;
            for (BackupInfo backupInfo : allBackups) {
                if (backupInfo.getCreatedAt().isBefore(cutoffTime)) {
                    deleteBackup(backupInfo.getBackupId());
                    cleanedCount++;
                }
            }

            log.info("过期备份清理完成，清理数量: {}", cleanedCount);
            return cleanedCount;

        } catch (Exception e) {
            log.error("清理过期备份失败", e);
            throw new ConfigurationBackupException("清理过期备份失败", e);
        }
    }

    /**
     * 验证备份完整性
     * @param backupId 备份ID
     * @return 验证结果
     */
    public BackupValidationResult validateBackup(String backupId) {
        log.debug("验证备份完整性，备份ID: {}", backupId);

        try {
            ConfigurationBackup backup = getBackup(backupId);
            if (backup == null) {
                return new BackupValidationResult(false, "备份不存在");
            }

            // 尝试解密配置
            try {
                SecurityProperties decryptedProperties = decryptSensitiveData(backup.getConfiguration());
                
                // 验证配置
                SecurityConfigurationValidator.ValidationResult validationResult = 
                        validator.validateConfiguration(decryptedProperties);
                
                return new BackupValidationResult(true, "备份完整性验证通过", validationResult);
                
            } catch (Exception e) {
                return new BackupValidationResult(false, "备份解密失败: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("验证备份完整性失败，备份ID: {}", backupId, e);
            return new BackupValidationResult(false, "验证失败: " + e.getMessage());
        }
    }

    /**
     * 生成备份ID
     */
    private String generateBackupId() {
        return "backup-" + LocalDateTime.now().format(BACKUP_ID_FORMATTER) + "-" + 
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 加密敏感配置数据
     */
    private SecurityProperties encryptSensitiveData(SecurityProperties properties) {
        SecurityProperties encrypted = copySecurityProperties(properties);

        // 加密JWT密钥
        if (encrypted.getJwt().getSecret() != null) {
            encrypted.getJwt().setSecret(encryptionService.encryptJwtSecret(encrypted.getJwt().getSecret()));
        }

        // 加密API Key值
        if (encrypted.getApiKey().getKeys() != null) {
            for (ApiKey apiKey : encrypted.getApiKey().getKeys()) {
                if (apiKey.getKeyValue() != null) {
                    apiKey.setKeyValue(encryptionService.encryptApiKeyValue(apiKey.getKeyValue()));
                }
            }
        }

        return encrypted;
    }

    /**
     * 解密敏感配置数据
     */
    private SecurityProperties decryptSensitiveData(SecurityProperties properties) {
        SecurityProperties decrypted = copySecurityProperties(properties);

        // 解密JWT密钥
        if (decrypted.getJwt().getSecret() != null) {
            decrypted.getJwt().setSecret(encryptionService.decryptJwtSecret(decrypted.getJwt().getSecret()));
        }

        // 解密API Key值
        if (decrypted.getApiKey().getKeys() != null) {
            for (ApiKey apiKey : decrypted.getApiKey().getKeys()) {
                if (apiKey.getKeyValue() != null) {
                    apiKey.setKeyValue(encryptionService.decryptApiKeyValue(apiKey.getKeyValue()));
                }
            }
        }

        return decrypted;
    }

    /**
     * 复制SecurityProperties对象
     */
    private SecurityProperties copySecurityProperties(SecurityProperties source) {
        try {
            // 使用JSON序列化/反序列化进行深拷贝
            String json = objectMapper.writeValueAsString(source);
            return objectMapper.readValue(json, SecurityProperties.class);
        } catch (Exception e) {
            throw new ConfigurationBackupException("复制配置对象失败", e);
        }
    }

    /**
     * 存储备份
     */
    private void storeBackup(ConfigurationBackup backup) {
        try {
            String json = objectMapper.writeValueAsString(backup);
            Map<String, Object> backupMap = new HashMap<>();
            backupMap.put("data", json);
            backupMap.put("timestamp", backup.getCreatedAt().toString());
            
            storeManager.saveConfig(BACKUP_KEY_PREFIX + backup.getBackupId(), backupMap);
        } catch (Exception e) {
            throw new ConfigurationBackupException("存储备份失败", e);
        }
    }

    /**
     * 获取备份
     */
    private ConfigurationBackup getBackup(String backupId) {
        // 先从缓存获取
        ConfigurationBackup cached = backupCache.get(backupId);
        if (cached != null) {
            return cached;
        }

        // 从存储获取
        try {
            Map<String, Object> backupMap = storeManager.getConfig(BACKUP_KEY_PREFIX + backupId);
            if (backupMap == null) {
                return null;
            }

            String json = (String) backupMap.get("data");
            ConfigurationBackup backup = objectMapper.readValue(json, ConfigurationBackup.class);
            
            // 缓存备份
            backupCache.put(backupId, backup);
            
            return backup;
        } catch (Exception e) {
            log.error("获取备份失败，备份ID: {}", backupId, e);
            return null;
        }
    }

    /**
     * 更新备份索引
     */
    private void updateBackupIndex(ConfigurationBackup backup) {
        try {
            List<BackupInfo> backupInfos = loadBackupIndex();
            
            BackupInfo backupInfo = new BackupInfo();
            backupInfo.setBackupId(backup.getBackupId());
            backupInfo.setDescription(backup.getDescription());
            backupInfo.setUserId(backup.getUserId());
            backupInfo.setCreatedAt(backup.getCreatedAt());
            backupInfo.setValid(backup.getValidationResult().isValid());
            
            backupInfos.add(backupInfo);
            
            saveBackupIndex(backupInfos);
        } catch (Exception e) {
            log.error("更新备份索引失败", e);
        }
    }

    /**
     * 从备份索引中删除
     */
    private void removeFromBackupIndex(String backupId) {
        try {
            List<BackupInfo> backupInfos = loadBackupIndex();
            backupInfos.removeIf(info -> info.getBackupId().equals(backupId));
            saveBackupIndex(backupInfos);
        } catch (Exception e) {
            log.error("从备份索引删除失败", e);
        }
    }

    /**
     * 加载备份索引
     */
    private List<BackupInfo> loadBackupIndex() {
        try {
            Map<String, Object> indexMap = storeManager.getConfig(BACKUP_INDEX_KEY);
            if (indexMap == null) {
                return new ArrayList<>();
            }

            String json = (String) indexMap.get("data");
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BackupInfo.class));
        } catch (Exception e) {
            log.error("加载备份索引失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存备份索引
     */
    private void saveBackupIndex(List<BackupInfo> backupInfos) {
        try {
            String json = objectMapper.writeValueAsString(backupInfos);
            Map<String, Object> indexMap = new HashMap<>();
            indexMap.put("data", json);
            indexMap.put("timestamp", LocalDateTime.now());
            
            storeManager.saveConfig(BACKUP_INDEX_KEY, indexMap);
        } catch (Exception e) {
            log.error("保存备份索引失败", e);
        }
    }

    /**
     * 配置备份数据模型
     */
    public static class ConfigurationBackup {
        private String backupId;
        private String description;
        private String userId;
        private LocalDateTime createdAt;
        private SecurityProperties configuration;
        private SecurityConfigurationValidator.ValidationResult validationResult;

        // Getters and setters
        public String getBackupId() { return backupId; }
        public void setBackupId(String backupId) { this.backupId = backupId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public SecurityProperties getConfiguration() { return configuration; }
        public void setConfiguration(SecurityProperties configuration) { this.configuration = configuration; }

        public SecurityConfigurationValidator.ValidationResult getValidationResult() { return validationResult; }
        public void setValidationResult(SecurityConfigurationValidator.ValidationResult validationResult) { 
            this.validationResult = validationResult; 
        }
    }

    /**
     * 备份信息数据模型
     */
    public static class BackupInfo {
        private String backupId;
        private String description;
        private String userId;
        private LocalDateTime createdAt;
        private boolean valid;

        // Getters and setters
        public String getBackupId() { return backupId; }
        public void setBackupId(String backupId) { this.backupId = backupId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
    }

    /**
         * 备份验证结果
         */
        public record BackupValidationResult(boolean valid, String message,
                                             SecurityConfigurationValidator.ValidationResult configValidationResult) {
            public BackupValidationResult(boolean valid, String message) {
                this(valid, message, null);
            }

    }

    /**
     * 配置备份异常
     */
    public static class ConfigurationBackupException extends RuntimeException {
        public ConfigurationBackupException(String message) {
            super(message);
        }

        public ConfigurationBackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}