package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.util.PathSanitizer;
import org.unreal.modelrouter.util.SafeFileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 改进的文件存储管理器
 * 实现清晰的配置版本管理逻辑
 */
public class ImprovedFileStoreManager implements StoreManager, ConfigVersionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImprovedFileStoreManager.class);

    private final String storagePath;
    private final ObjectMapper objectMapper;
    
    private static final String VERSIONS_DIR = "versions";
    private static final String METADATA_SUFFIX = ".metadata.json";
    private static final String VERSION_PREFIX = ".v";
    private static final String JSON_SUFFIX = ".json";

    public ImprovedFileStoreManager(String storagePath) {
        this.storagePath = storagePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            Path basePath = PathSanitizer.sanitizePath(storagePath);
            Path versionsPath = basePath.resolve(VERSIONS_DIR);
            
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
            if (!Files.exists(versionsPath)) {
                Files.createDirectories(versionsPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize storage directory: " + storagePath, e);
            throw new RuntimeException("Failed to initialize storage directory", e);
        }
    }

    // ========== ConfigVersionManager 实现 ==========

    @Override
    public int initializeConfig(String key, Map<String, Object> config, String description) {
        if (isConfigInitialized(key)) {
            LOGGER.warn("Config already initialized for key: {}", key);
            return getConfigMetadata(key).getCurrentVersion();
        }

        LOGGER.info("Initializing config for key: {}", key);
        
        try {
            // 创建版本1
            int version = 1;
            saveVersionedConfig(key, config, version);
            
            // 创建元数据
            ConfigMetadata metadata = new ConfigMetadata();
            metadata.setConfigKey(key);
            metadata.setCurrentVersion(version);
            metadata.setInitialVersion(version);
            metadata.setCreatedAt(LocalDateTime.now());
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy("system");
            metadata.setTotalVersions(1);
            
            saveMetadata(key, metadata);
            
            // 创建版本历史
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(version);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy("system");
            versionInfo.setDescription(description != null ? description : "Initial configuration");
            versionInfo.setChangeType(VersionInfo.ChangeType.INITIAL);
            
            saveVersionInfo(key, versionInfo);
            
            // 更新当前活跃配置
            updateActiveConfig(key, config);
            
            LOGGER.info("Config initialized for key: {}, version: {}", key, version);
            return version;
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize config for key: " + key, e);
            throw new RuntimeException("Failed to initialize config", e);
        }
    }

    @Override
    public int updateConfig(String key, Map<String, Object> config, String description, String userId) {
        if (!isConfigInitialized(key)) {
            return initializeConfig(key, config, description);
        }

        try {
            // 获取当前配置和元数据
            Map<String, Object> currentConfig = getLatestConfig(key);
            ConfigMetadata metadata = getConfigMetadata(key);
            
            // 检查配置是否有变化
            if (Objects.equals(currentConfig, config)) {
                LOGGER.debug("Config unchanged for key: {}, returning current version: {}", 
                           key, metadata.getCurrentVersion());
                return metadata.getCurrentVersion();
            }

            // 创建新版本
            int newVersion = metadata.getCurrentVersion() + 1;
            saveVersionedConfig(key, config, newVersion);
            
            // 更新元数据
            metadata.setCurrentVersion(newVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            
            saveMetadata(key, metadata);
            
            // 创建版本历史
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(newVersion);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy(userId != null ? userId : "system");
            versionInfo.setDescription(description != null ? description : "Configuration update");
            versionInfo.setChangeType(VersionInfo.ChangeType.UPDATE);
            
            saveVersionInfo(key, versionInfo);
            
            // 更新当前活跃配置
            updateActiveConfig(key, config);
            
            LOGGER.info("Config updated for key: {}, new version: {}", key, newVersion);
            return newVersion;
            
        } catch (Exception e) {
            LOGGER.error("Failed to update config for key: " + key, e);
            throw new RuntimeException("Failed to update config", e);
        }
    }

    @Override
    public Map<String, Object> getLatestConfig(String key) {
        try {
            // 优先从当前活跃配置读取
            Path activePath = getActiveConfigPath(key);
            if (Files.exists(activePath)) {
                return SafeFileOperations.readJsonFile(activePath, objectMapper, new TypeReference<>() {});
            }
            
            // 如果活跃配置不存在，从最新版本读取
            ConfigMetadata metadata = getConfigMetadata(key);
            if (metadata != null) {
                return getConfigByVersion(key, metadata.getCurrentVersion());
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get latest config for key: " + key, e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);
            if (!Files.exists(versionPath)) {
                return null;
            }
            return SafeFileOperations.readJsonFile(versionPath, objectMapper, new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.error("Failed to get config version for key: {}, version: {}", key, version, e);
            return null;
        }
    }

    @Override
    public int rollbackToVersion(String key, int targetVersion, String description, String userId) {
        if (!isConfigInitialized(key)) {
            throw new IllegalArgumentException("Config not initialized for key: " + key);
        }

        try {
            // 验证目标版本是否存在
            Map<String, Object> targetConfig = getConfigByVersion(key, targetVersion);
            if (targetConfig == null) {
                throw new IllegalArgumentException("Target version does not exist: " + targetVersion);
            }

            // 创建回滚版本（新版本号）
            ConfigMetadata metadata = getConfigMetadata(key);
            int rollbackVersion = metadata.getCurrentVersion() + 1;
            
            saveVersionedConfig(key, targetConfig, rollbackVersion);
            
            // 更新元数据
            metadata.setCurrentVersion(rollbackVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            
            saveMetadata(key, metadata);
            
            // 创建版本历史
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(rollbackVersion);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy(userId != null ? userId : "system");
            versionInfo.setDescription(description != null ? description : 
                                     "Rollback to version " + targetVersion);
            versionInfo.setChangeType(VersionInfo.ChangeType.ROLLBACK);
            
            saveVersionInfo(key, versionInfo);
            
            // 更新当前活跃配置
            updateActiveConfig(key, targetConfig);
            
            LOGGER.info("Config rolled back for key: {}, from version {} to {}, new version: {}", 
                       key, metadata.getCurrentVersion() - 1, targetVersion, rollbackVersion);
            return rollbackVersion;
            
        } catch (Exception e) {
            LOGGER.error("Failed to rollback config for key: " + key, e);
            throw new RuntimeException("Failed to rollback config", e);
        }
    }

    @Override
    public ConfigMetadata getConfigMetadata(String key) {
        try {
            Path metadataPath = getMetadataPath(key);
            if (!Files.exists(metadataPath)) {
                return null;
            }
            Map<String, Object> metadataMap = SafeFileOperations.readJsonFile(metadataPath, objectMapper, 
                                                 new TypeReference<Map<String, Object>>() {});
            return objectMapper.convertValue(metadataMap, ConfigMetadata.class);
        } catch (Exception e) {
            LOGGER.error("Failed to get metadata for key: " + key, e);
            return null;
        }
    }

    @Override
    public List<VersionInfo> getVersionHistory(String key, int limit) {
        try {
            Path historyPath = getVersionHistoryPath(key);
            if (!Files.exists(historyPath)) {
                return new ArrayList<>();
            }
            
            List<Map<String, Object>> allHistoryMaps = SafeFileOperations.readJsonFile(historyPath, objectMapper,
                    new TypeReference<List<Map<String, Object>>>() {});
            
            // 转换为VersionInfo对象并按版本号倒序排序
            return allHistoryMaps.stream()
                    .map(map -> objectMapper.convertValue(map, VersionInfo.class))
                    .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
                    .limit(limit > 0 ? limit : allHistoryMaps.size())
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            LOGGER.error("Failed to get version history for key: " + key, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean isConfigInitialized(String key) {
        return getConfigMetadata(key) != null;
    }

    @Override
    public int cleanupOldVersions(String key, int keepVersions) {
        if (keepVersions <= 0) {
            throw new IllegalArgumentException("Keep versions must be positive");
        }

        try {
            ConfigMetadata metadata = getConfigMetadata(key);
            if (metadata == null || metadata.getTotalVersions() <= keepVersions) {
                return 0;
            }

            // 获取所有版本并排序
            List<Integer> allVersions = getAllVersionNumbers(key);
            allVersions.sort(Collections.reverseOrder());
            
            // 删除旧版本
            int deletedCount = 0;
            for (int i = keepVersions; i < allVersions.size(); i++) {
                int versionToDelete = allVersions.get(i);
                Path versionPath = getVersionedConfigPath(key, versionToDelete);
                
                if (Files.exists(versionPath)) {
                    Files.delete(versionPath);
                    deletedCount++;
                }
            }
            
            // 更新元数据
            metadata.setTotalVersions(keepVersions);
            saveMetadata(key, metadata);
            
            // 清理版本历史
            cleanupVersionHistory(key, keepVersions);
            
            LOGGER.info("Cleaned up {} old versions for key: {}", deletedCount, key);
            return deletedCount;
            
        } catch (Exception e) {
            LOGGER.error("Failed to cleanup old versions for key: " + key, e);
            return 0;
        }
    }

    // ========== StoreManager 实现（兼容性） ==========

    @Override
    public void saveConfig(String key, Map<String, Object> config) {
        updateConfig(key, config, "Legacy save operation", "system");
    }

    @Override
    public Map<String, Object> getConfig(String key) {
        return getLatestConfig(key);
    }

    @Override
    public void deleteConfig(String key) {
        // 实现配置删除逻辑
        try {
            // 删除所有版本文件
            List<Integer> versions = getAllVersionNumbers(key);
            for (int version : versions) {
                Path versionPath = getVersionedConfigPath(key, version);
                if (Files.exists(versionPath)) {
                    Files.delete(versionPath);
                }
            }
            
            // 删除元数据和历史
            Path metadataPath = getMetadataPath(key);
            Path historyPath = getVersionHistoryPath(key);
            Path activePath = getActiveConfigPath(key);
            
            if (Files.exists(metadataPath)) Files.delete(metadataPath);
            if (Files.exists(historyPath)) Files.delete(historyPath);
            if (Files.exists(activePath)) Files.delete(activePath);
            
            LOGGER.info("Deleted all config data for key: {}", key);
            
        } catch (Exception e) {
            LOGGER.error("Failed to delete config for key: " + key, e);
            throw new RuntimeException("Failed to delete config", e);
        }
    }

    @Override
    public Iterable<String> getAllKeys() {
        try {
            Set<String> keys = new HashSet<>();
            Path basePath = PathSanitizer.sanitizePath(storagePath);
            
            if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                Files.list(basePath)
                        .filter(path -> path.toString().endsWith(METADATA_SUFFIX))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String key = fileName.substring(0, fileName.lastIndexOf(METADATA_SUFFIX));
                            keys.add(key);
                        });
            }
            return keys;
        } catch (Exception e) {
            LOGGER.error("Failed to list config keys", e);
            return new HashSet<>();
        }
    }

    @Override
    public boolean exists(String key) {
        return isConfigInitialized(key);
    }

    @Override
    public void updateConfig(String key, Map<String, Object> config) {
        updateConfig(key, config, "Legacy update operation", "system");
    }

    // ========== 私有辅助方法 ==========

    private Path getActiveConfigPath(String key) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath).resolve(sanitizedKey + JSON_SUFFIX);
    }

    private Path getVersionedConfigPath(String key, int version) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath)
                .resolve(VERSIONS_DIR)
                .resolve(sanitizedKey + VERSION_PREFIX + version + JSON_SUFFIX);
    }

    private Path getMetadataPath(String key) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath).resolve(sanitizedKey + METADATA_SUFFIX);
    }

    private Path getVersionHistoryPath(String key) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath).resolve(sanitizedKey + ".history.json");
    }

    private void saveVersionedConfig(String key, Map<String, Object> config, int version) throws IOException {
        Path versionPath = getVersionedConfigPath(key, version);
        SafeFileOperations.writeJsonFile(versionPath, config, objectMapper);
    }

    private void saveMetadata(String key, ConfigMetadata metadata) throws IOException {
        Path metadataPath = getMetadataPath(key);
        Map<String, Object> metadataMap = objectMapper.convertValue(metadata, Map.class);
        SafeFileOperations.writeJsonFile(metadataPath, metadataMap, objectMapper);
    }

    private void updateActiveConfig(String key, Map<String, Object> config) throws IOException {
        Path activePath = getActiveConfigPath(key);
        SafeFileOperations.writeJsonFile(activePath, config, objectMapper);
    }

    private void saveVersionInfo(String key, VersionInfo versionInfo) throws IOException {
        Path historyPath = getVersionHistoryPath(key);
        
        List<Map<String, Object>> history;
        if (Files.exists(historyPath)) {
            history = SafeFileOperations.readJsonFile(historyPath, objectMapper,
                    new TypeReference<List<Map<String, Object>>>() {});
        } else {
            history = new ArrayList<>();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> versionInfoMap = objectMapper.convertValue(versionInfo, Map.class);
        history.add(versionInfoMap);
        SafeFileOperations.writeJsonFile(historyPath, history, objectMapper);
    }

    private List<Integer> getAllVersionNumbers(String key) {
        try {
            Path versionsDir = PathSanitizer.sanitizePath(storagePath).resolve(VERSIONS_DIR);
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            String prefix = sanitizedKey + VERSION_PREFIX;
            
            List<Integer> versions = new ArrayList<>();
            
            if (Files.exists(versionsDir) && Files.isDirectory(versionsDir)) {
                Files.list(versionsDir)
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith(prefix) && fileName.endsWith(JSON_SUFFIX);
                        })
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String versionStr = fileName.substring(prefix.length(), 
                                                                 fileName.lastIndexOf(JSON_SUFFIX));
                            try {
                                versions.add(Integer.parseInt(versionStr));
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Invalid version number in file: {}", fileName);
                            }
                        });
            }
            
            return versions;
        } catch (Exception e) {
            LOGGER.error("Failed to get version numbers for key: " + key, e);
            return new ArrayList<>();
        }
    }

    private void cleanupVersionHistory(String key, int keepVersions) throws IOException {
        List<VersionInfo> history = getVersionHistory(key, 0);
        if (history.size() <= keepVersions) {
            return;
        }
        
        // 保留最新的版本历史
        List<VersionInfo> keptHistory = history.stream()
                .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
                .limit(keepVersions)
                .collect(Collectors.toList());
        
        // 转换为Map格式进行存储
        List<Map<String, Object>> keptHistoryMaps = keptHistory.stream()
                .map(versionInfo -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.convertValue(versionInfo, Map.class);
                    return map;
                })
                .collect(Collectors.toList());
        
        Path historyPath = getVersionHistoryPath(key);
        SafeFileOperations.writeJsonFile(historyPath, keptHistoryMaps, objectMapper);
    }
}