package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileStoreManager extends BaseStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStoreManager.class);

    private final String storagePath;
    private final ObjectMapper objectMapper;

    public FileStoreManager(final String storagePath) {
        this.storagePath = storagePath;
        this.objectMapper = new ObjectMapper();
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            Path path = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize storage directory: " + storagePath, e);
            throw new RuntimeException("Failed to initialize storage directory", e);
        }
    }

    /**
     * 保存配置信息到文件
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    protected void doSaveConfig(final String key, final Map<String, Object> config) {
        try {
            String sanitizedKey = org.unreal.modelrouter.util.PathSanitizer.sanitizeFileName(key);
            Path configPath = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            org.unreal.modelrouter.util.SafeFileOperations.writeJsonFile(configPath, config, objectMapper);
        } catch (IOException e) {
            LOGGER.error("Failed to save config for key: " + key, e);
            throw new RuntimeException("Failed to save config", e);
        }
    }

    /**
     * 从文件获取配置信息
     * @param key 配置键
     * @return 配置内容
     */
    @Override
    protected Map<String, Object> doGetConfig(final String key) {
        try {
            String sanitizedKey = org.unreal.modelrouter.util.PathSanitizer.sanitizeFileName(key);
            Path configPath = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            return org.unreal.modelrouter.util.SafeFileOperations.readJsonFile(configPath, objectMapper, new TypeReference<>() { });
        } catch (IOException e) {
            LOGGER.error("Failed to read config for key: " + key, e);
            throw new RuntimeException("Failed to read config", e);
        }
    }

    /**
     * 从文件删除配置信息
     * @param key 配置键
     */
    @Override
    protected void doDeleteConfig(final String key) {
        try {
            String sanitizedKey = org.unreal.modelrouter.util.PathSanitizer.sanitizeFileName(key);
            Path configPath = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            org.unreal.modelrouter.util.SafeFileOperations.deleteFile(configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to delete config for key: " + key, e);
            throw new RuntimeException("Failed to delete config", e);
        }
    }

    /**
     * 检查配置文件是否存在
     * @param key 配置键
     * @return 是否存在
     */
    @Override
    protected boolean doExists(final String key) {
        try {
            String sanitizedKey = org.unreal.modelrouter.util.PathSanitizer.sanitizeFileName(key);
            Path configPath = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            return Files.exists(configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to check existence for key: " + key, e);
            return false;
        }
    }

    /**
     * 更新配置信息到文件
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    protected void doUpdateConfig(final String key, final Map<String, Object> config) {
        // 更新和保存使用相同的操作
        doSaveConfig(key, config);
    }

    /**
     * 获取所有配置键
     * @return 所有配置键的集合
     */
    @Override
    public Iterable<String> getAllKeys() {
        try {
            Set<String> keys = new HashSet<>();
            Path storageDir = org.unreal.modelrouter.util.PathSanitizer.sanitizePath(storagePath);
            
            if (Files.exists(storageDir) && Files.isDirectory(storageDir)) {
                Files.list(storageDir)
                        .filter(path -> path.toString().endsWith(".json") && !path.toString().contains("@"))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String key = fileName.substring(0, fileName.lastIndexOf(".json"));
                            keys.add(key);
                        });
            }
            return keys;
        } catch (Exception e) {
            LOGGER.error("Failed to list config keys", e);
            return new HashSet<>();
        }
    }

    /**
     * 保存配置的版本
     * @param key 配置键
     * @param config 配置内容
     * @param version 版本号
     */
    @Override
    public void saveConfigVersion(String key, Map<String, Object> config, int version) {
        try {
            File versionFile = new File(storagePath, key + "@" + version + ".json");
            objectMapper.writeValue(versionFile, config);
        } catch (IOException e) {
            LOGGER.error("Failed to save config version for key: " + key + ", version: " + version, e);
        }
    }

    /**
     * 获取配置的所有版本号
     * @param key 配置键
     * @return 版本号列表
     */
    @Override
    public List<Integer> getConfigVersions(String key) {
        try {
            List<Integer> versions = new ArrayList<>();
            File storageDir = new File(storagePath);
            Pattern versionPattern = Pattern.compile(Pattern.quote(key) + "@(\\d+)\\.json");
            
            File[] files = storageDir.listFiles((dir, name) -> name.startsWith(key + "@") && name.endsWith(".json"));
            
            if (files != null) {
                for (File file : files) {
                    Matcher matcher = versionPattern.matcher(file.getName());
                    if (matcher.matches()) {
                        versions.add(Integer.parseInt(matcher.group(1)));
                    }
                }
            }
            
            Collections.sort(versions);
            return versions;
        } catch (Exception e) {
            LOGGER.error("Failed to list config versions for key: " + key, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定版本的配置
     * @param key 配置键
     * @param version 版本号
     * @return 配置内容
     */
    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        try {
            File versionFile = new File(storagePath, key + "@" + version + ".json");
            if (!versionFile.exists()) {
                return null;
            }
            return objectMapper.readValue(versionFile, new TypeReference<>() { });
        } catch (IOException e) {
            LOGGER.error("Failed to read config version for key: " + key + ", version: " + version, e);
            return null;
        }
    }

    /**
     * 删除指定版本的配置
     * @param key 配置键
     * @param version 版本号
     */
    @Override
    public void deleteConfigVersion(String key, int version) {
        try {
            File versionFile = new File(storagePath, key + "@" + version + ".json");
            if (versionFile.exists()) {
                Files.delete(versionFile.toPath());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete config version for key: " + key + ", version: " + version, e);
        }
    }
}
