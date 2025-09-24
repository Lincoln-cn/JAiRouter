package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;
import org.unreal.modelrouter.util.PathSanitizer;
import org.unreal.modelrouter.util.SafeFileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 改进的文件存储管理器 实现清晰的配置版本管理逻辑
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
        // 实现初始化配置逻辑
        saveConfig(key, config);
        saveConfigVersion(key, config, 1);

        // 创建元数据
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setConfigKey(key);
        metadata.setInitialVersion(1);
        metadata.setCurrentVersion(1);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("system");
        metadata.setTotalVersions(1);

        // 保存元数据（这里简化处理，实际应该保存到文件中）
        // 在实际实现中，你可能需要将元数据保存到文件或数据库中
        return 1;
    }

    @Override
    public int updateConfig(String key, Map<String, Object> config, String description, String userId) {
        // 获取当前版本
        List<Integer> versions = getConfigVersions(key);
        int newVersion = versions.isEmpty() ? 1 : Collections.max(versions) + 1;

        // 保存新版本
        saveConfigVersion(key, config, newVersion);

        // 更新当前配置
        saveConfig(key, config);

        return newVersion;
    }

    @Override
    public Map<String, Object> getLatestConfig(String key) {
        return getConfig(key);
    }

    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);
            if (!Files.exists(versionPath)) {
                return null;
            }
            return SafeFileOperations.readJsonFile(versionPath, objectMapper, new TypeReference<>() {
            });
        } catch (Exception e) {
            LOGGER.error("Failed to read config version for key: {}, version: {}", key, version, e);
            return null;
        }
    }

    @Override
    public ConfigMetadata getConfigMetadata(String key) {
        // 简化实现，实际应该从存储中读取元数据
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setConfigKey(key);
        metadata.setInitialVersion(1);
        metadata.setCurrentVersion(1);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("system");
        metadata.setTotalVersions(1);
        return metadata;
    }

    @Override
    public List<VersionInfo> getVersionHistory(String key, int limit) {
        // 简化实现，实际应该从存储中读取版本历史
        return new ArrayList<>();
    }

    @Override
    public boolean isConfigInitialized(String key) {
        return exists(key);
    }

    @Override
    public int cleanupOldVersions(String key, int keepVersions) {
        // 简化实现，实际应该清理旧版本文件
        return 0;
    }

    // ========== StoreManager 实现 ==========
    @Override
    public void saveConfig(String key, Map<String, Object> config) {
        try {
            Path configPath = getConfigPath(key);
            SafeFileOperations.writeJsonFile(configPath, config, objectMapper);
        } catch (Exception e) {
            LOGGER.error("Failed to save config for key: " + key, e);
            throw new RuntimeException("Failed to save config", e);
        }
    }

    @Override
    public Map<String, Object> getConfig(String key) {
        try {
            Path configPath = getConfigPath(key);
            if (!Files.exists(configPath)) {
                return null;
            }
            return SafeFileOperations.readJsonFile(configPath, objectMapper, new TypeReference<>() {
            });
        } catch (Exception e) {
            LOGGER.error("Failed to get config for key: " + key, e);
            return null;
        }
    }

    @Override
    public void deleteConfig(String key) {
        try {
            Path configPath = getConfigPath(key);
            if (Files.exists(configPath)) {
                Files.delete(configPath);
            }
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
                        .filter(path -> path.toString().endsWith(JSON_SUFFIX) && !path.toString().contains(VERSION_PREFIX))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String key = fileName.substring(0, fileName.lastIndexOf(JSON_SUFFIX));
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
        try {
            Path configPath = getConfigPath(key);
            return Files.exists(configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to check config existence for key: " + key, e);
            return false;
        }
    }

    @Override
    public void updateConfig(String key, Map<String, Object> config) {
        saveConfig(key, config);
    }

    @Override
    public void saveConfigVersion(String key, Map<String, Object> config, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);
            SafeFileOperations.writeJsonFile(versionPath, config, objectMapper);
        } catch (Exception e) {
            LOGGER.error("Failed to save config version for key: {}, version: {}", key, version, e);
            throw new RuntimeException("Failed to save config version", e);
        }
    }

    @Override
    public List<Integer> getConfigVersions(String key) {
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

            Collections.sort(versions);
            return versions;
        } catch (Exception e) {
            LOGGER.error("Failed to get version numbers for key: " + key, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteConfigVersion(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);
            if (Files.exists(versionPath)) {
                Files.delete(versionPath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to delete config version for key: {}, version: {}", key, version, e);
            throw new RuntimeException("Failed to delete config version", e);
        }
    }

    // ========== 私有辅助方法 ==========
    private Path getConfigPath(String key) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath).resolve(sanitizedKey + JSON_SUFFIX);
    }

    private Path getVersionedConfigPath(String key, int version) {
        String sanitizedKey = PathSanitizer.sanitizeFileName(key);
        return PathSanitizer.sanitizePath(storagePath)
                .resolve(VERSIONS_DIR)
                .resolve(sanitizedKey + VERSION_PREFIX + version + JSON_SUFFIX);
    }

    /**
     * 验证指定版本是否存在
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本是否存在
     */
    @Override
    public boolean versionExists(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);
            boolean exists = Files.exists(versionPath);

            if (exists) {
                // 进行文件完整性检查
                return isVersionFileValid(versionPath);
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to check version existence for key: {}, version: {}", key, version, e);
            return false;
        }
    }

    /**
     * 获取指定版本的文件路径
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本文件的实际路径，如果版本不存在则返回null
     */
    @Override
    public String getVersionFilePath(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);

            if (Files.exists(versionPath)) {
                return versionPath.toAbsolutePath().toString();
            }

            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get version file path for key: {}, version: {}", key, version, e);
            return null;
        }
    }

    /**
     * 获取指定版本的创建时间
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本创建时间，如果版本不存在则返回null
     */
    @Override
    public LocalDateTime getVersionCreatedTime(String key, int version) {
        try {
            Path versionPath = getVersionedConfigPath(key, version);

            if (Files.exists(versionPath)) {
                BasicFileAttributes attrs = Files.readAttributes(versionPath, BasicFileAttributes.class);
                Instant creationTime = attrs.creationTime().toInstant();
                return LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault());
            }

            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get version creation time for key: {}, version: {}", key, version, e);
            return null;
        }
    }

    /**
     * 检查版本文件的完整性
     *
     * @param versionPath 版本文件路径
     * @return 文件是否有效
     */
    private boolean isVersionFileValid(Path versionPath) {
        try {
            // 检查文件大小（空文件或过小的文件可能损坏）
            if (Files.size(versionPath) < 2) { // 至少应该有"{}"
                LOGGER.warn("Version file is too small, possibly corrupted: {}", versionPath);
                return false;
            }

            // 尝试解析JSON以验证格式
            try {
                SafeFileOperations.readJsonFile(versionPath, objectMapper, new TypeReference<Map<String, Object>>() {
                });
                return true;
            } catch (IOException e) {
                LOGGER.warn("Version file contains invalid JSON: {}", versionPath, e);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Error validating version file: {}", versionPath, e);
            return false;
        }
    }
}
