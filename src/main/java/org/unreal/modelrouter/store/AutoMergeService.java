package org.unreal.modelrouter.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.store.StoreManagerConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动合并服务
 * 用于合并 config 目录下的多版本配置文件，并重置版本从1开始
 */
@Service
public class AutoMergeService {

    private static final Logger logger = LoggerFactory.getLogger(AutoMergeService.class);

    private static final String CONFIG_FILE_PREFIX = "model-router-config";
    private static final Pattern VERSION_PATTERN = Pattern.compile(CONFIG_FILE_PREFIX + "@(\\d+)\\.json");

    private final ObjectMapper objectMapper;
    private final StoreManager storeManager;
    private final ConfigurationService configurationService;
    private final StoreManagerConfiguration storeManagerConfiguration;

    @Autowired
    public AutoMergeService(ObjectMapper objectMapper, 
                           StoreManager storeManager,
                           ConfigurationService configurationService,
                           StoreManagerConfiguration storeManagerConfiguration) {
        this.objectMapper = objectMapper;
        this.storeManager = storeManager;
        this.configurationService = configurationService;
        this.storeManagerConfiguration = storeManagerConfiguration;
    }

    /**
     * 合并配置文件结果
     */
    public static class MergeResult {
        private final boolean success;
        private final String message;
        private final int mergedFilesCount;
        private final int newVersionCount;
        private final List<String> mergedFiles;
        private final List<String> errors;

        public MergeResult(boolean success, String message, int mergedFilesCount, 
                          int newVersionCount, List<String> mergedFiles, List<String> errors) {
            this.success = success;
            this.message = message;
            this.mergedFilesCount = mergedFilesCount;
            this.newVersionCount = newVersionCount;
            this.mergedFiles = mergedFiles != null ? mergedFiles : new ArrayList<>();
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getMergedFilesCount() { return mergedFilesCount; }
        public int getNewVersionCount() { return newVersionCount; }
        public List<String> getMergedFiles() { return mergedFiles; }
        public List<String> getErrors() { return errors; }
    }

    /**
     * 获取配置目录路径
     * @return 配置目录路径
     */
    private String getConfigDir() {
        return storeManagerConfiguration.getPath();
    }

    /**
     * 检查自动合并功能是否启用
     * @return 是否启用自动合并功能
     */
    private boolean isAutoMergeEnabled() {
        return storeManagerConfiguration.isAutoMerge();
    }

    /**
     * 扫描并获取所有版本配置文件
     * @return 版本文件映射 (版本号 -> 文件路径)
     */
    public Map<Integer, String> scanVersionFiles() {
        // 如果自动合并功能未启用，返回空映射
        if (!isAutoMergeEnabled()) {
            logger.info("自动合并功能未启用，跳过扫描版本配置文件");
            return new TreeMap<>();
        }

        Map<Integer, String> versionFiles = new TreeMap<>();
        
        try {
            Path configPath = Paths.get(getConfigDir());
            if (!Files.exists(configPath)) {
                logger.warn("配置目录不存在: {}", getConfigDir());
                return versionFiles;
            }

            Files.list(configPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        Matcher matcher = VERSION_PATTERN.matcher(fileName);
                        if (matcher.matches()) {
                            try {
                                int version = Integer.parseInt(matcher.group(1));
                                versionFiles.put(version, path.toString());
                            } catch (NumberFormatException e) {
                                logger.warn("无法解析版本号: {}", fileName);
                            }
                        }
                    });

            logger.info("扫描到 {} 个版本配置文件", versionFiles.size());
            
        } catch (IOException e) {
            logger.error("扫描配置文件时发生错误", e);
        }

        return versionFiles;
    }

    /**
     * 读取配置文件内容
     * @param filePath 文件路径
     * @return 配置内容
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfigFile(String filePath) throws IOException {
        // 使用安全的文件路径处理
        Path safePath = Paths.get(filePath).normalize();
        
        // 确保文件在config目录内
        Path configPath = Paths.get(getConfigDir()).toAbsolutePath().normalize();
        if (!safePath.toAbsolutePath().normalize().startsWith(configPath)) {
            throw new SecurityException("文件路径不在允许的目录内: " + filePath);
        }
        
        File file = safePath.toFile();
        if (!file.exists()) {
            throw new IOException("配置文件不存在: " + filePath);
        }
        
        return objectMapper.readValue(file, Map.class);
    }

    /**
     * 合并多个配置文件
     * @param configs 配置列表 (按版本顺序)
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeConfigs(List<Map<String, Object>> configs) {
        if (configs.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> merged = new HashMap<>();
        
        for (Map<String, Object> config : configs) {
            if (config == null) continue;
            
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if ("services".equals(key) && value instanceof Map && merged.containsKey(key)) {
                    // 特殊处理 services 字段，需要深度合并
                    Map<String, Object> existingServices = (Map<String, Object>) merged.get(key);
                    Map<String, Object> newServices = (Map<String, Object>) value;
                    
                    for (Map.Entry<String, Object> serviceEntry : newServices.entrySet()) {
                        String serviceType = serviceEntry.getKey();
                        Object serviceConfig = serviceEntry.getValue();
                        
                        if (existingServices.containsKey(serviceType) && 
                            serviceConfig instanceof Map && 
                            existingServices.get(serviceType) instanceof Map) {
                            // 合并同一服务类型的配置
                            Map<String, Object> existingServiceConfig = (Map<String, Object>) existingServices.get(serviceType);
                            Map<String, Object> newServiceConfig = (Map<String, Object>) serviceConfig;
                            existingServices.put(serviceType, mergeServiceConfig(existingServiceConfig, newServiceConfig));
                        } else {
                            existingServices.put(serviceType, serviceConfig);
                        }
                    }
                } else {
                    merged.put(key, value);
                }
            }
        }
        
        return merged;
    }

    /**
     * 合并服务配置
     * @param existing 现有配置
     * @param newConfig 新配置
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeServiceConfig(Map<String, Object> existing, Map<String, Object> newConfig) {
        Map<String, Object> merged = new HashMap<>(existing);
        
        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if ("instances".equals(key) && value instanceof List) {
                // instances 字段需要特殊处理，合并实例列表
                List<Map<String, Object>> existingInstances = (List<Map<String, Object>>) merged.getOrDefault(key, new ArrayList<>());
                List<Map<String, Object>> newInstances = (List<Map<String, Object>>) value;
                
                Map<String, Map<String, Object>> instanceMap = new HashMap<>();
                
                // 先添加现有实例
                for (Map<String, Object> instance : existingInstances) {
                    String instanceId = buildInstanceId(instance);
                    if (instanceId != null) {
                        instanceMap.put(instanceId, instance);
                    }
                }
                
                // 再添加或更新新实例
                for (Map<String, Object> instance : newInstances) {
                    String instanceId = buildInstanceId(instance);
                    if (instanceId != null) {
                        instanceMap.put(instanceId, instance);
                    }
                }
                
                merged.put(key, new ArrayList<>(instanceMap.values()));
            } else {
                merged.put(key, value);
            }
        }
        
        return merged;
    }

    /**
     * 构建实例ID
     * @param instance 实例配置
     * @return 实例ID
     */
    private String buildInstanceId(Map<String, Object> instance) {
        String name = (String) instance.get("name");
        String baseUrl = (String) instance.get("baseUrl");
        if (name != null && baseUrl != null) {
            return name + "@" + baseUrl;
        }
        return null;
    }

    /**
     * 执行自动合并操作
     * @return 合并结果
     */
    public MergeResult performAutoMerge() {
        logger.info("开始执行自动合并操作");
        
        List<String> mergedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // 1. 扫描版本文件
            Map<Integer, String> versionFiles = scanVersionFiles();
            if (versionFiles.isEmpty()) {
                return new MergeResult(false, "未找到任何版本配置文件", 0, 0, mergedFiles, errors);
            }

            // 2. 读取所有配置文件
            List<Map<String, Object>> configs = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : versionFiles.entrySet()) {
                try {
                    Map<String, Object> config = readConfigFile(entry.getValue());
                    configs.add(config);
                    mergedFiles.add(entry.getValue());
                    logger.debug("成功读取配置文件: {} (版本 {})", entry.getValue(), entry.getKey());
                } catch (IOException e) {
                    String error = "读取配置文件失败: " + entry.getValue() + " - " + e.getMessage();
                    errors.add(error);
                    logger.error(error, e);
                }
            }

            if (configs.isEmpty()) {
                return new MergeResult(false, "没有成功读取任何配置文件", 0, 0, mergedFiles, errors);
            }

            // 3. 合并配置
            Map<String, Object> mergedConfig = mergeConfigs(configs);
            logger.info("成功合并 {} 个配置文件", configs.size());

            // 4. 清除现有版本配置
            clearExistingVersions();

            // 5. 保存合并后的配置为版本1
            storeManager.saveConfigVersion("model-router-config", mergedConfig, 1);
            logger.info("已保存合并后的配置为版本1");

            // 6. 刷新配置服务
            configurationService.applyVersion(1);
            logger.info("已应用合并后的配置");

            return new MergeResult(true, 
                    String.format("成功合并 %d 个配置文件，重置版本为1", configs.size()),
                    configs.size(), 1, mergedFiles, errors);

        } catch (Exception e) {
            String error = "自动合并过程中发生错误: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);
            return new MergeResult(false, error, 0, 0, mergedFiles, errors);
        }
    }

    /**
     * 清除现有的版本配置
     */
    private void clearExistingVersions() {
        try {
            List<Integer> existingVersions = storeManager.getConfigVersions("model-router-config");
            for (Integer version : existingVersions) {
                storeManager.deleteConfigVersion("model-router-config", version);
                logger.debug("删除现有版本配置: {}", version);
            }
            logger.info("已清除 {} 个现有版本配置", existingVersions.size());
        } catch (Exception e) {
            logger.warn("清除现有版本配置时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 备份现有配置文件
     * @return 备份结果
     */
    public MergeResult backupConfigFiles() {
        logger.info("开始备份配置文件");
        
        List<String> backedUpFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            Map<Integer, String> versionFiles = scanVersionFiles();
            if (versionFiles.isEmpty()) {
                return new MergeResult(false, "未找到任何需要备份的配置文件", 0, 0, backedUpFiles, errors);
            }

            String backupDir = getConfigDir() + "/backup_" + System.currentTimeMillis();
            Path backupPath = Paths.get(getConfigDir()).resolve("backup_" + System.currentTimeMillis()).normalize();
            Files.createDirectories(backupPath);

            for (Map.Entry<Integer, String> entry : versionFiles.entrySet()) {
                try {
                    Path sourcePath = Paths.get(entry.getValue());
                    Path targetPath = backupPath.resolve(sourcePath.getFileName());
                    Files.copy(sourcePath, targetPath);
                    backedUpFiles.add(targetPath.toString());
                    logger.debug("备份文件: {} -> {}", entry.getValue(), targetPath);
                } catch (IOException e) {
                    String error = "备份文件失败: " + entry.getValue() + " - " + e.getMessage();
                    errors.add(error);
                    logger.error(error, e);
                }
            }

            return new MergeResult(true, 
                    String.format("成功备份 %d 个配置文件到 %s", backedUpFiles.size(), backupDir),
                    backedUpFiles.size(), 0, backedUpFiles, errors);

        } catch (Exception e) {
            String error = "备份过程中发生错误: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);
            return new MergeResult(false, error, 0, 0, backedUpFiles, errors);
        }
    }

    /**
     * 获取合并预览
     * @return 合并预览结果
     */
    public Map<String, Object> getMergePreview() {
        Map<String, Object> preview = new HashMap<>();
        
        try {
            Map<Integer, String> versionFiles = scanVersionFiles();
            List<Map<String, Object>> configs = new ArrayList<>();
            
            for (Map.Entry<Integer, String> entry : versionFiles.entrySet()) {
                try {
                    Map<String, Object> config = readConfigFile(entry.getValue());
                    configs.add(config);
                } catch (IOException e) {
                    logger.warn("读取配置文件失败: {}", entry.getValue());
                }
            }
            
            if (!configs.isEmpty()) {
                Map<String, Object> mergedConfig = mergeConfigs(configs);
                preview.put("mergedConfig", mergedConfig);
                preview.put("sourceFiles", versionFiles);
                preview.put("totalFiles", configs.size());
            }
            
        } catch (Exception e) {
            logger.error("生成合并预览时发生错误", e);
            preview.put("error", e.getMessage());
        }
        
        return preview;
    }

    /**
     * 清理配置文件
     * @param deleteOriginals 是否删除原始文件
     * @return 清理结果
     */
    public MergeResult cleanupConfigFiles(boolean deleteOriginals) {
        logger.info("开始清理配置文件，删除原始文件: {}", deleteOriginals);
        
        List<String> cleanedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            if (deleteOriginals) {
                Map<Integer, String> versionFiles = scanVersionFiles();
                
                for (Map.Entry<Integer, String> entry : versionFiles.entrySet()) {
                    try {
                        Path filePath = Paths.get(entry.getValue());
                        Files.deleteIfExists(filePath);
                        cleanedFiles.add(entry.getValue());
                        logger.debug("删除原始配置文件: {}", entry.getValue());
                    } catch (IOException e) {
                        String error = "删除文件失败: " + entry.getValue() + " - " + e.getMessage();
                        errors.add(error);
                        logger.error(error, e);
                    }
                }
            }

            return new MergeResult(true, 
                    String.format("成功清理 %d 个配置文件", cleanedFiles.size()),
                    cleanedFiles.size(), 0, cleanedFiles, errors);

        } catch (Exception e) {
            String error = "清理过程中发生错误: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);
            return new MergeResult(false, error, 0, 0, cleanedFiles, errors);
        }
    }
}