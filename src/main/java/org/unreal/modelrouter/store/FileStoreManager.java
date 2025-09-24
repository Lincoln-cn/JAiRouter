package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.util.PathSanitizer;
import org.unreal.modelrouter.util.SafeFileOperations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class FileStoreManager extends BaseStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStoreManager.class);

    private final String storagePath;
    private final ObjectMapper objectMapper;

    public FileStoreManager(final String storagePath) {
        this.storagePath = storagePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            Path path = PathSanitizer.sanitizePath(storagePath);
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            Path configPath = PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            SafeFileOperations.writeJsonFile(configPath, config, objectMapper);
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            Path configPath = PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            return SafeFileOperations.readJsonFile(configPath, objectMapper, new TypeReference<>() { });
        } catch (IOException e) {
            // 对于文件不存在的情况，静默处理，因为这是正常现象（新安装或默认初始化）
            if (e.getMessage() != null && e.getMessage().contains("File does not exist")) {
                return null;
            }
            LOGGER.error("Failed to read config for key: " + key + ". File path: " + storagePath + "/" + key + ".json", e);
            throw new RuntimeException("Failed to read config for key: " + key + ". Please check if the JSON file is valid.", e);
        }
    }

    /**
     * 从文件删除配置信息
     * @param key 配置键
     */
    @Override
    protected void doDeleteConfig(final String key) {
        try {
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            Path configPath = PathSanitizer.sanitizePath(storagePath)
                    .resolve(sanitizedKey + ".json");
            SafeFileOperations.deleteFile(configPath);
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            Path configPath = PathSanitizer.sanitizePath(storagePath)
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
            Path storageDir = PathSanitizer.sanitizePath(storagePath);
            
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
        // 只有当配置不为空时才保存版本文件
        if (config != null && !config.isEmpty()) {
            try {
                String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
                objectMapper.writeValue(versionFile, config);
            } catch (IOException e) {
                LOGGER.error("Failed to save config version for key: " + key + ", version: " + version, e);
            }
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            
            // 使用缓存来减少文件系统遍历
            File[] files = storageDir.listFiles((dir, name) -> 
                name.startsWith(sanitizedKey + "@") && 
                (name.endsWith(".json") || name.endsWith(".json.gz")));
            
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    // 提取版本号，支持压缩和未压缩的文件
                    if (fileName.endsWith(".json")) {
                        String versionStr = fileName.substring(
                            (sanitizedKey + "@").length(), 
                            fileName.length() - ".json".length());
                        try {
                            versions.add(Integer.parseInt(versionStr));
                        } catch (NumberFormatException e) {
                            // 忽略无法解析为数字的文件
                            LOGGER.debug("无法解析版本号: {}", fileName);
                        }
                    } else if (fileName.endsWith(".json.gz")) {
                        String versionStr = fileName.substring(
                            (sanitizedKey + "@").length(), 
                            fileName.length() - ".json.gz".length());
                        try {
                            versions.add(Integer.parseInt(versionStr));
                        } catch (NumberFormatException e) {
                            // 忽略无法解析为数字的文件
                            LOGGER.debug("无法解析版本号: {}", fileName);
                        }
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            if (versionFile.exists()) {
                Files.delete(versionFile.toPath());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete config version for key: " + key + ", version: " + version, e);
        }
    }

    /**
     * 压缩指定版本的配置文件
     * @param key 配置键
     * @param version 版本号
     * @return 压缩是否成功
     */
    public boolean compressConfigVersion(String key, int version) {
        try {
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            File compressedFile = new File(storagePath, sanitizedKey + "@" + version + ".json.gz");
            
            if (!versionFile.exists()) {
                LOGGER.warn("Config version file does not exist: " + versionFile.getAbsolutePath());
                return false;
            }
            
            // 如果压缩文件已存在，先删除
            if (compressedFile.exists()) {
                Files.delete(compressedFile.toPath());
            }
            
            // 压缩文件
            try (java.io.FileInputStream fis = new java.io.FileInputStream(versionFile);
                 java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(compressedFile))) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    gzos.write(buffer, 0, len);
                }
            }
            
            // 删除原始文件
            Files.delete(versionFile.toPath());
            
            LOGGER.info("Successfully compressed config version file: " + versionFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to compress config version for key: " + key + ", version: " + version, e);
            return false;
        }
    }

    /**
     * 解压缩指定版本的配置文件
     * @param key 配置键
     * @param version 版本号
     * @return 解压缩是否成功
     */
    public boolean decompressConfigVersion(String key, int version) {
        try {
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File compressedFile = new File(storagePath, sanitizedKey + "@" + version + ".json.gz");
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            
            if (!compressedFile.exists()) {
                LOGGER.warn("Compressed config version file does not exist: " + compressedFile.getAbsolutePath());
                return false;
            }
            
            // 如果原始文件已存在，先删除
            if (versionFile.exists()) {
                Files.delete(versionFile.toPath());
            }
            
            // 解压缩文件
            try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(compressedFile));
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(versionFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }
            
            // 删除压缩文件
            Files.delete(compressedFile.toPath());
            
            LOGGER.info("Successfully decompressed config version file: " + compressedFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to decompress config version for key: " + key + ", version: " + version, e);
            return false;
        }
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            File compressedFile = new File(storagePath, sanitizedKey + "@" + version + ".json.gz");

            // 检查版本文件是否存在（支持压缩和未压缩格式）
            boolean exists = versionFile.exists() || compressedFile.exists();

            if (exists) {
                // 进行文件完整性检查
                File fileToCheck = versionFile.exists() ? versionFile : compressedFile;
                return isVersionFileValid(fileToCheck, versionFile.exists());
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to check version existence for key: " + key + ", version: " + version, e);
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            File compressedFile = new File(storagePath, sanitizedKey + "@" + version + ".json.gz");

            if (versionFile.exists()) {
                return versionFile.getAbsolutePath();
            } else if (compressedFile.exists()) {
                return compressedFile.getAbsolutePath();
            }

            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get version file path for key: " + key + ", version: " + version, e);
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
            String sanitizedKey = PathSanitizer.sanitizeFileName(key);
            File versionFile = new File(storagePath, sanitizedKey + "@" + version + ".json");
            File compressedFile = new File(storagePath, sanitizedKey + "@" + version + ".json.gz");

            File fileToCheck = null;
            if (versionFile.exists()) {
                fileToCheck = versionFile;
            } else if (compressedFile.exists()) {
                fileToCheck = compressedFile;
            }

            if (fileToCheck != null) {
                BasicFileAttributes attrs = Files.readAttributes(fileToCheck.toPath(), BasicFileAttributes.class);
                Instant creationTime = attrs.creationTime().toInstant();
                return LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault());
            }

            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to get version creation time for key: " + key + ", version: " + version, e);
            return null;
        }
    }

    /**
     * 检查版本文件的完整性
     *
     * @param file           要检查的文件
     * @param isUncompressed 是否为未压缩文件
     * @return 文件是否有效
     */
    private boolean isVersionFileValid(File file, boolean isUncompressed) {
        try {
            // 检查文件大小（空文件或过小的文件可能损坏）
            if (file.length() < 2) { // 至少应该有"{}"
                LOGGER.warn("Version file is too small, possibly corrupted: " + file.getAbsolutePath());
                return false;
            }

            // 对于未压缩的JSON文件，尝试解析以验证格式
            if (isUncompressed) {
                try {
                    objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {
                    });
                    return true;
                } catch (IOException e) {
                    LOGGER.warn("Version file contains invalid JSON: " + file.getAbsolutePath(), e);
                    return false;
                }
            } else {
                // 对于压缩文件，检查是否可以正常读取
                try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(
                        new java.io.FileInputStream(file))) {
                    // 尝试读取前几个字节以验证压缩格式
                    byte[] buffer = new byte[10];
                    gzis.read(buffer);
                    return true;
                } catch (IOException e) {
                    LOGGER.warn("Version file is not a valid gzip file: " + file.getAbsolutePath(), e);
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error validating version file: " + file.getAbsolutePath(), e);
            return false;
        }
    }
}