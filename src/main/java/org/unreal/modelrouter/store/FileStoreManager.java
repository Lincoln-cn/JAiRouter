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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 基于文件的配置存储实现
 */
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
            Path path = Paths.get(storagePath);
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
            File configFile = new File(storagePath, key + ".json");
            objectMapper.writeValue(configFile, config);
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
            File configFile = new File(storagePath, key + ".json");
            if (!configFile.exists()) {
                return null;
            }
            return objectMapper.readValue(configFile, new TypeReference<>() { });
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
            File configFile = new File(storagePath, key + ".json");
            if (configFile.exists()) {
                Files.delete(configFile.toPath());
            }
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
        File configFile = new File(storagePath, key + ".json");
        return configFile.exists();
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
            File storageDir = new File(storagePath);
            File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
            
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String key = fileName.substring(0, fileName.lastIndexOf(".json"));
                    keys.add(key);
                }
            }
            return keys;
        } catch (Exception e) {
            LOGGER.error("Failed to list config keys", e);
            return new HashSet<>();
        }
    }
}
