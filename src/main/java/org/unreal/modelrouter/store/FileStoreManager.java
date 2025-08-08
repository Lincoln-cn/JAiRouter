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

    private static final Logger logger = LoggerFactory.getLogger(FileStoreManager.class);

    private final String storagePath;
    private final ObjectMapper objectMapper;

    public FileStoreManager(String storagePath) {
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
            logger.error("Failed to initialize storage directory: " + storagePath, e);
            throw new RuntimeException("Failed to initialize storage directory", e);
        }
    }

    @Override
    protected void doSaveConfig(String key, Map<String, Object> config) {
        try {
            File configFile = new File(storagePath, key + ".json");
            objectMapper.writeValue(configFile, config);
        } catch (IOException e) {
            logger.error("Failed to save config for key: " + key, e);
            throw new RuntimeException("Failed to save config", e);
        }
    }

    @Override
    protected Map<String, Object> doGetConfig(String key) {
        try {
            File configFile = new File(storagePath, key + ".json");
            if (!configFile.exists()) {
                return null;
            }
            return objectMapper.readValue(configFile, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            logger.error("Failed to read config for key: " + key, e);
            throw new RuntimeException("Failed to read config", e);
        }
    }

    @Override
    protected void doDeleteConfig(String key) {
        try {
            File configFile = new File(storagePath, key + ".json");
            if (configFile.exists()) {
                Files.delete(configFile.toPath());
            }
        } catch (IOException e) {
            logger.error("Failed to delete config for key: " + key, e);
            throw new RuntimeException("Failed to delete config", e);
        }
    }

    @Override
    protected boolean doExists(String key) {
        File configFile = new File(storagePath, key + ".json");
        return configFile.exists();
    }

    @Override
    protected void doUpdateConfig(String key, Map<String, Object> config) {
        // 更新和保存使用相同的操作
        doSaveConfig(key, config);
    }

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
            logger.error("Failed to list config keys", e);
            return new HashSet<>();
        }
    }
}
