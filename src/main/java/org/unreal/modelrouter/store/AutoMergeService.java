package org.unreal.modelrouter.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.entity.*;
import org.unreal.modelrouter.util.InstanceIdUtils;
import org.unreal.modelrouter.util.SecurityUtils;

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

            try (var files = Files.list(configPath)) {
                files.filter(Files::isRegularFile)
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
            }

            logger.info("扫描到 {} 个版本配置文件", versionFiles.size());
            
        } catch (IOException e) {
            logger.error("扫描配置文件时发生错误", e);
        }

        return versionFiles;
    }

    /**
     * 基于版本管理系统扫描版本文件
     * 使用ConfigurationService获取版本信息，而不是直接扫描文件系统
     *
     * @return 版本文件映射 (版本号 -> 版本描述)
     */
    public Map<Integer, String> scanVersionFilesFromVersionManager() {
        logger.info("开始从版本管理系统扫描版本文件");

        Map<Integer, String> versionFiles = new TreeMap<>();

        try {
            // 获取所有版本号
            List<Integer> versions = configurationService.getAllVersions();

            if (versions.isEmpty()) {
                logger.info("版本管理系统中未找到任何版本");
                return versionFiles;
            }

            // 为每个版本创建映射条目
            for (Integer version : versions) {
                // 验证版本配置是否可读
                Map<String, Object> versionConfig = configurationService.getVersionConfig(version);
                if (versionConfig != null && !versionConfig.isEmpty()) {
                    String description = String.format("版本 %d (通过版本管理系统)", version);
                    versionFiles.put(version, description);
                    logger.debug("找到有效版本: {}", version);
                } else {
                    logger.warn("版本 {} 的配置为空或无法读取", version);
                }
            }

            logger.info("从版本管理系统扫描到 {} 个有效版本", versionFiles.size());

        } catch (Exception e) {
            logger.error("从版本管理系统扫描版本文件时发生错误", e);
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
     * 改进的配置合并方法，支持版本管理系统格式和冲突检测
     *
     * @param configs          配置列表 (按版本顺序)
     * @param mergeDescription
     * @return 合并结果，包含合并后的配置和冲突信息
     */
    public MergeConfigResult mergeConfigsWithConflictDetection(List<Map<String, Object>> configs, String mergeDescription) {
        if (configs.isEmpty()) {
            return new MergeConfigResult(new HashMap<>(), new ArrayList<>(), new ArrayList<>());
        }

        Map<String, Object> merged = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        logger.info("开始合并 {} 个配置，启用冲突检测", configs.size());

        for (int i = 0; i < configs.size(); i++) {
            Map<String, Object> config = configs.get(i);
            if (config == null) {
                warnings.add(String.format("版本 %d 的配置为空，跳过", i + 1));
                continue;
            }

            // 标准化配置格式以支持版本管理系统
            Map<String, Object> normalizedConfig = normalizeVersionManagerConfig(config);

            for (Map.Entry<String, Object> entry : normalizedConfig.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("services".equals(key) && value instanceof Map && merged.containsKey(key)) {
                    // 深度合并服务配置，检测冲突
                    Map<String, Object> existingServices = (Map<String, Object>) merged.get(key);
                    Map<String, Object> newServices = (Map<String, Object>) value;

                    MergeServiceResult serviceResult = mergeServicesWithConflictDetection(
                            existingServices, newServices, i + 1);

                    merged.put(key, serviceResult.mergedServices());
                    conflicts.addAll(serviceResult.conflicts());
                    warnings.addAll(serviceResult.warnings());

                } else if (merged.containsKey(key)) {
                    // 检测非服务配置的冲突
                    Object existingValue = merged.get(key);
                    if (!Objects.equals(existingValue, value)) {
                        String conflict = String.format("配置键 '%s' 在版本 %d 中存在冲突: 现有值='%s', 新值='%s'",
                                key, i + 1, existingValue, value);
                        conflicts.add(conflict);
                        logger.warn(conflict);

                        // 使用后来的值覆盖（后来者优先策略）
                        merged.put(key, value);
                    }
                } else {
                    merged.put(key, value);
                }
            }
        }

        // 后处理：确保合并结果的完整性
        Map<String, Object> finalMerged = postProcessMergedConfig(merged, warnings, mergeDescription);

        logger.info("配置合并完成，发现 {} 个冲突，{} 个警告", conflicts.size(), warnings.size());

        return new MergeConfigResult(finalMerged, conflicts, warnings);
    }

    /**
     * 标准化版本管理系统的配置格式
     *
     * @param config 原始配置
     * @return 标准化后的配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeVersionManagerConfig(Map<String, Object> config) {
        Map<String, Object> normalized = new HashMap<>(config);

        // 移除版本管理系统的元数据字段
        normalized.remove("_metadata");
        normalized.remove("version");
        normalized.remove("timestamp");
        normalized.remove("createdAt");
        normalized.remove("lastModified");
        normalized.remove("versionInfo");

        // 确保services字段存在
        if (!normalized.containsKey("services")) {
            normalized.put("services", new HashMap<>());
        }

        // 标准化服务配置格式
        Object servicesObj = normalized.get("services");
        if (servicesObj instanceof Map) {
            Map<String, Object> services = (Map<String, Object>) servicesObj;
            Map<String, Object> normalizedServices = new HashMap<>();

            for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                String serviceType = serviceEntry.getKey();
                Object serviceConfig = serviceEntry.getValue();

                if (serviceConfig instanceof Map) {
                    Map<String, Object> normalizedServiceConfig = normalizeServiceConfigFormat(
                            (Map<String, Object>) serviceConfig);
                    normalizedServices.put(serviceType, normalizedServiceConfig);
                } else {
                    normalizedServices.put(serviceType, serviceConfig);
                }
            }

            normalized.put("services", normalizedServices);
        }

        return normalized;
    }

    /**
     * 标准化服务配置格式
     *
     * @param serviceConfig 服务配置
     * @return 标准化后的服务配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeServiceConfigFormat(Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new HashMap<>(serviceConfig);

        // 确保instances字段存在且为列表
        if (!normalized.containsKey("instances")) {
            normalized.put("instances", new ArrayList<>());
        } else {
            Object instancesObj = normalized.get("instances");
            if (!(instancesObj instanceof List)) {
                logger.warn("instances字段不是列表类型，重置为空列表");
                normalized.put("instances", new ArrayList<>());
            } else {
                // 标准化实例配置
                List<Map<String, Object>> instances = (List<Map<String, Object>>) instancesObj;
                List<Map<String, Object>> normalizedInstances = new ArrayList<>();

                for (Map<String, Object> instance : instances) {
                    if (instance instanceof Map) {
                        normalizedInstances.add(normalizeInstanceConfigFormat(instance));
                    }
                }

                normalized.put("instances", normalizedInstances);
            }
        }

        return normalized;
    }

    /**
     * 标准化实例配置格式
     *
     * @param instanceConfig 实例配置
     * @return 标准化后的实例配置
     */
    private Map<String, Object> normalizeInstanceConfigFormat(Map<String, Object> instanceConfig) {
        Map<String, Object> normalized = new HashMap<>(instanceConfig);

        // 确保必要字段存在
        if (!normalized.containsKey("weight")) {
            normalized.put("weight", 1);
        }

        if (!normalized.containsKey("status")) {
            normalized.put("status", "active");
        }

        // 移除动态字段
        normalized.remove("health");
        normalized.remove("lastHealthCheck");
        normalized.remove("healthCheckCount");
        normalized.remove("lastError");

        // 确保instanceId格式正确
        String name = (String) normalized.get("name");
        String baseUrl = (String) normalized.get("baseUrl");
        if (name != null && baseUrl != null) {
            String instanceId = name + "@" + baseUrl;
            normalized.put("instanceId", instanceId);
        }

        return normalized;
    }

    /**
     * 合并服务配置并检测冲突
     *
     * @param existingServices 现有服务配置
     * @param newServices      新服务配置
     * @param versionIndex     版本索引（用于错误报告）
     * @return 合并结果
     */
    @SuppressWarnings("unchecked")
    private MergeServiceResult mergeServicesWithConflictDetection(
            Map<String, Object> existingServices,
            Map<String, Object> newServices,
            int versionIndex) {

        Map<String, Object> mergedServices = new HashMap<>(existingServices);
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, Object> serviceEntry : newServices.entrySet()) {
            String serviceType = serviceEntry.getKey();
            Object serviceConfig = serviceEntry.getValue();

            if (mergedServices.containsKey(serviceType) &&
                    serviceConfig instanceof Map &&
                    mergedServices.get(serviceType) instanceof Map) {

                // 合并同一服务类型的配置
                Map<String, Object> existingServiceConfig = (Map<String, Object>) mergedServices.get(serviceType);
                Map<String, Object> newServiceConfig = (Map<String, Object>) serviceConfig;

                MergeServiceConfigResult configResult = mergeServiceConfigWithConflictDetection(
                        existingServiceConfig, newServiceConfig, serviceType, versionIndex);

                mergedServices.put(serviceType, configResult.mergedConfig());
                conflicts.addAll(configResult.conflicts());
                warnings.addAll(configResult.warnings());

            } else {
                mergedServices.put(serviceType, serviceConfig);
                if (existingServices.containsKey(serviceType)) {
                    warnings.add(String.format("服务类型 '%s' 在版本 %d 中被完全替换", serviceType, versionIndex));
                }
            }
        }

        return new MergeServiceResult(mergedServices, conflicts, warnings);
    }

    /**
     * 合并单个服务配置并检测冲突
     *
     * @param existingConfig 现有服务配置
     * @param newConfig      新服务配置
     * @param serviceType    服务类型
     * @param versionIndex   版本索引
     * @return 合并结果
     */
    @SuppressWarnings("unchecked")
    private MergeServiceConfigResult mergeServiceConfigWithConflictDetection(
            Map<String, Object> existingConfig,
            Map<String, Object> newConfig,
            String serviceType,
            int versionIndex) {

        Map<String, Object> merged = new HashMap<>(existingConfig);
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // 特殊处理实例列表合并
                List<Map<String, Object>> existingInstances =
                        (List<Map<String, Object>>) merged.getOrDefault(key, new ArrayList<>());
                List<Map<String, Object>> newInstances = (List<Map<String, Object>>) value;

                MergeInstanceResult instanceResult = mergeInstancesWithConflictDetection(
                        existingInstances, newInstances, serviceType, versionIndex);

                merged.put(key, instanceResult.mergedInstances());
                conflicts.addAll(instanceResult.conflicts());
                warnings.addAll(instanceResult.warnings());

            } else if (merged.containsKey(key)) {
                // 检测其他配置项的冲突
                Object existingValue = merged.get(key);
                if (!Objects.equals(existingValue, value)) {
                    String conflict = String.format("服务 '%s' 的配置项 '%s' 在版本 %d 中存在冲突: 现有值='%s', 新值='%s'",
                            serviceType, key, versionIndex, existingValue, value);
                    conflicts.add(conflict);

                    // 使用后来的值（后来者优先策略）
                    merged.put(key, value);
                }
            } else {
                merged.put(key, value);
            }
        }

        return new MergeServiceConfigResult(merged, conflicts, warnings);
    }

    /**
     * 合并实例列表并检测冲突
     *
     * @param existingInstances 现有实例列表
     * @param newInstances      新实例列表
     * @param serviceType       服务类型
     * @param versionIndex      版本索引
     * @return 合并结果
     */
    private MergeInstanceResult mergeInstancesWithConflictDetection(
            List<Map<String, Object>> existingInstances,
            List<Map<String, Object>> newInstances,
            String serviceType,
            int versionIndex) {

        Map<String, Map<String, Object>> instanceMap = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 先添加现有实例
        for (Map<String, Object> instance : existingInstances) {
            String instanceId = InstanceIdUtils.getInstanceId(instance);
            if (instanceId != null) {
                instanceMap.put(instanceId, instance);
            } else {
                warnings.add(String.format("服务 '%s' 中发现无效实例配置（缺少name或baseUrl）", serviceType));
            }
        }

        // 添加或更新新实例，检测冲突
        for (Map<String, Object> instance : newInstances) {
            String instanceId = InstanceIdUtils.getInstanceId(instance);
            if (instanceId != null) {
                if (instanceMap.containsKey(instanceId)) {
                    // 检测实例配置冲突
                    Map<String, Object> existingInstance = instanceMap.get(instanceId);
                    List<String> instanceConflicts = detectInstanceConflicts(
                            existingInstance, instance, instanceId, serviceType, versionIndex);

                    if (!instanceConflicts.isEmpty()) {
                        conflicts.addAll(instanceConflicts);
                    }

                    // 合并实例配置（后来者优先）
                    Map<String, Object> mergedInstance = new HashMap<>(existingInstance);
                    mergedInstance.putAll(instance);
                    instanceMap.put(instanceId, mergedInstance);
                } else {
                    instanceMap.put(instanceId, instance);
                }
            } else {
                warnings.add(String.format("服务 '%s' 在版本 %d 中发现无效实例配置（缺少name或baseUrl）",
                        serviceType, versionIndex));
            }
        }

        List<Map<String, Object>> mergedInstances = new ArrayList<>(instanceMap.values());

        return new MergeInstanceResult(mergedInstances, conflicts, warnings);
    }

    /**
     * 检测实例配置冲突
     *
     * @param existingInstance 现有实例
     * @param newInstance      新实例
     * @param instanceId       实例ID
     * @param serviceType      服务类型
     * @param versionIndex     版本索引
     * @return 冲突列表
     */
    private List<String> detectInstanceConflicts(
            Map<String, Object> existingInstance,
            Map<String, Object> newInstance,
            String instanceId,
            String serviceType,
            int versionIndex) {

        List<String> conflicts = new ArrayList<>();

        // 检查关键配置项的冲突
        String[] keyFields = {"weight", "status", "timeout", "retryCount", "maxConnections"};

        for (String field : keyFields) {
            Object existingValue = existingInstance.get(field);
            Object newValue = newInstance.get(field);

            if (existingValue != null && newValue != null && !Objects.equals(existingValue, newValue)) {
                String conflict = String.format("服务 '%s' 实例 '%s' 的 '%s' 在版本 %d 中存在冲突: 现有值='%s', 新值='%s'",
                        serviceType, instanceId, field, versionIndex, existingValue, newValue);
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    /**
     * 后处理合并后的配置，确保完整性
     *
     * @param merged           合并后的配置
     * @param warnings         警告列表
     * @param mergeDescription
     * @return 最终配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postProcessMergedConfig(Map<String, Object> merged, List<String> warnings, String mergeDescription) {
        Map<String, Object> processed = new HashMap<>(merged);

        // 确保services字段存在
        if (!processed.containsKey("services")) {
            processed.put("services", new HashMap<>());
            warnings.add("合并后的配置缺少services字段，已添加空的services配置");
        }

        // 验证服务配置的完整性
        Object servicesObj = processed.get("services");
        if (servicesObj instanceof Map) {
            Map<String, Object> services = (Map<String, Object>) servicesObj;

            for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                String serviceType = serviceEntry.getKey();
                Object serviceConfig = serviceEntry.getValue();

                if (serviceConfig instanceof Map) {
                    Map<String, Object> serviceMap = (Map<String, Object>) serviceConfig;

                    // 确保instances字段存在
                    if (!serviceMap.containsKey("instances")) {
                        serviceMap.put("instances", new ArrayList<>());
                        warnings.add(String.format("服务 '%s' 缺少instances字段，已添加空的实例列表", serviceType));
                    }
                }
            }
        }

        // 添加合并元数据
        Map<String, Object> mergeMetadata = new HashMap<>();
        mergeMetadata.put("operationDetail", mergeDescription);
        mergeMetadata.put("operation", "Merge");
        mergeMetadata.put("timestamp", System.currentTimeMillis());
        mergeMetadata.put("mergeType", "auto-merge-with-version-manager");
        processed.put("_metadata", mergeMetadata);

        return processed;
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
                    String instanceId =  InstanceIdUtils.getInstanceId(instance);
                    if (instanceId != null) {
                        instanceMap.put(instanceId, instance);
                    }
                }

                // 再添加或更新新实例
                for (Map<String, Object> instance : newInstances) {
                    String instanceId =  InstanceIdUtils.getInstanceId(instance);
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
     * 执行自动合并操作
     * @return 合并结果
     */
    public MergeResult performMerge() {
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

            configurationService.cleanVersion();
            String mergeDescription = "执行文件合并，成功合并 %d 个配置文件，重置版本".formatted(configs.size());
            Map<String, Object> mergeMetadata = new HashMap<>();
            mergeMetadata.put("operationDetail", mergeDescription);
            mergeMetadata.put("operation", "Merge");
            mergeMetadata.put("timestamp", System.currentTimeMillis());
            mergeMetadata.put("mergeType", "auto-merge-with-version-manager");
            mergedConfig.put("_metadata", mergeMetadata);

            configurationService.saveAsNewVersion(mergedConfig, "手动合并配置文件", SecurityUtils.getCurrentUserId());

            logger.info("已应用合并后的配置");

            return new MergeResult(true,
                    String.format("成功合并 %d 个配置文件，重置版本", configs.size()),
                    configs.size(), 1, mergedFiles, errors);

        } catch (Exception e) {
            String error = "自动合并过程中发生错误: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);
            return new MergeResult(false, error, 0, 0, mergedFiles, errors);
        }
    }

    /**
     * 执行基于版本管理系统的自动合并操作
     * 使用ConfigurationService进行版本管理，而不是直接操作文件
     *
     * @return 合并结果
     */
    public MergeResult performAutoMergeWithVersionManager() {
        logger.info("开始执行基于版本管理系统的自动合并操作");

        List<String> mergedVersions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            // 1. 从版本管理系统获取所有版本
            List<Integer> versions = configurationService.getAllVersions();
            if (versions.isEmpty()) {
                return new MergeResult(false, "版本管理系统中未找到任何版本", 0, 0, mergedVersions, errors);
            }

            logger.info("找到 {} 个版本需要合并: {}", versions.size(), versions);

            // 2. 读取所有版本的配置
            List<Map<String, Object>> configs = new ArrayList<>();
            for (Integer version : versions) {
                try {
                    Map<String, Object> config = configurationService.getVersionConfig(version);
                    if (config != null && !config.isEmpty()) {
                        configs.add(config);
                        mergedVersions.add("版本 " + version);
                        logger.debug("成功读取版本 {} 的配置", version);
                    } else {
                        String warning = "版本 " + version + " 的配置为空，跳过合并";
                        logger.warn(warning);
                        errors.add(warning);
                    }
                } catch (Exception e) {
                    String error = "读取版本 " + version + " 配置失败: " + e.getMessage();
                    errors.add(error);
                    logger.error(error, e);
                }
            }

            if (configs.isEmpty()) {
                return new MergeResult(false, "没有成功读取任何版本配置", 0, 0, mergedVersions, errors);
            }

            // 3. 合并配置，启用冲突检测
            String mergeDescription = String.format("执行智能合并版本: %s", versions);
            MergeConfigResult mergeResult = mergeConfigsWithConflictDetection(configs, mergeDescription);
            Map<String, Object> mergedConfig = mergeResult.mergedConfig();

            // 记录合并过程中的冲突和警告
            if (mergeResult.hasConflicts()) {
                logger.warn("合并过程中发现 {} 个冲突:", mergeResult.conflicts().size());
                for (String conflict : mergeResult.conflicts()) {
                    logger.warn("  - {}", conflict);
                    errors.add("冲突: " + conflict);
                }
            }

            if (mergeResult.hasWarnings()) {
                logger.info("合并过程中发现 {} 个警告:", mergeResult.warnings().size());
                for (String warning : mergeResult.warnings()) {
                    logger.info("  - {}", warning);
                }
            }

            logger.info("成功合并 {} 个版本的配置", configs.size());

            // 4. 通过ConfigurationService保存合并后的配置为新版本

            int newVersion = configurationService.saveAsNewVersionIfChanged(
                    mergedConfig,
                    mergeDescription,
                    "auto-merge-system"
            );

            if (newVersion > 0) {
                logger.info("合并后的配置已保存为版本: {}", newVersion);

                // 5. 应用新版本
                configurationService.applyVersion(newVersion);
                logger.info("已应用合并后的配置版本: {}", newVersion);

                return new MergeResult(true,
                        String.format("成功合并 %d 个版本，创建新版本: %d", configs.size(), newVersion),
                        configs.size(), newVersion, mergedVersions, errors);
            } else {
                return new MergeResult(false, "合并后的配置与当前配置相同，未创建新版本",
                        configs.size(), 0, mergedVersions, errors);
            }

        } catch (Exception e) {
            String error = "基于版本管理系统的自动合并过程中发生错误: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);
            return new MergeResult(false, error, 0, 0, mergedVersions, errors);
        }
    }

    /**
     * 执行原子性的配置合并操作，支持错误恢复
     *
     * @return 合并结果
     */
    public MergeResult performAtomicMergeWithVersionManager() {
        logger.info("开始执行原子性配置合并操作");

        List<String> mergedVersions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 备份当前配置用于错误恢复
        Map<String, Object> backupConfig = null;
        int backupVersion = 0;

        try {
            // 1. 备份当前配置
            try {
                backupVersion = configurationService.getCurrentVersion();
                backupConfig = configurationService.getVersionConfig(backupVersion);
                logger.info("已备份当前配置版本: {}", backupVersion);
            } catch (Exception e) {
                logger.warn("无法备份当前配置: {}", e.getMessage());
            }

            // 2. 获取所有版本进行合并
            List<Integer> versions = configurationService.getAllVersions();
            if (versions.isEmpty()) {
                return new MergeResult(false, "版本管理系统中未找到任何版本", 0, 0, mergedVersions, errors);
            }

            logger.info("准备合并 {} 个版本: {}", versions.size(), versions);

            // 3. 读取所有版本配置
            List<Map<String, Object>> configs = new ArrayList<>();
            for (Integer version : versions) {
                try {
                    Map<String, Object> config = configurationService.getVersionConfig(version);
                    if (config != null && !config.isEmpty()) {
                        configs.add(config);
                        mergedVersions.add("版本 " + version);
                        logger.debug("成功读取版本 {} 的配置", version);
                    } else {
                        String warning = "版本 " + version + " 的配置为空，跳过合并";
                        logger.warn(warning);
                        errors.add(warning);
                    }
                } catch (Exception e) {
                    String error = "读取版本 " + version + " 配置失败: " + e.getMessage();
                    errors.add(error);
                    logger.error(error, e);
                }
            }

            if (configs.isEmpty()) {
                return new MergeResult(false, "没有成功读取任何版本配置", 0, 0, mergedVersions, errors);
            }

            // 4. 执行合并操作
            String mergeDescription = String.format("执行原子合并版本: %s", versions);
            MergeConfigResult mergeResult = mergeConfigsWithConflictDetection(configs, mergeDescription);
            Map<String, Object> mergedConfig = mergeResult.mergedConfig();

            // 检查是否有严重冲突需要中止操作
            if (mergeResult.hasConflicts() && shouldAbortOnConflicts(mergeResult.conflicts())) {
                String conflictSummary = String.format("发现 %d 个严重冲突，中止合并操作", mergeResult.conflicts().size());
                logger.error(conflictSummary);
                errors.addAll(mergeResult.conflicts());
                return new MergeResult(false, conflictSummary, configs.size(), 0, mergedVersions, errors);
            }

            // 记录冲突和警告
            if (mergeResult.hasConflicts()) {
                logger.warn("合并过程中发现 {} 个冲突，但继续执行:", mergeResult.conflicts().size());
                for (String conflict : mergeResult.conflicts()) {
                    logger.warn("  - {}", conflict);
                    errors.add("冲突: " + conflict);
                }
            }

            if (mergeResult.hasWarnings()) {
                logger.info("合并过程中发现 {} 个警告:", mergeResult.warnings().size());
                for (String warning : mergeResult.warnings()) {
                    logger.info("  - {}", warning);
                }
            }

            // 5. 原子性保存合并结果
            try {
                mergeDescription = String.format("原子性自动合并版本: %s (冲突: %d, 警告: %d)",
                        versions, mergeResult.conflicts().size(), mergeResult.warnings().size());

                int newVersion = configurationService.saveAsNewVersionIfChanged(
                        mergedConfig,
                        mergeDescription,
                        "atomic-merge-system"
                );

                if (newVersion > 0) {
                    logger.info("合并后的配置已保存为版本: {}", newVersion);

                    // 6. 应用新版本
                    configurationService.applyVersion(newVersion);
                    logger.info("已应用合并后的配置版本: {}", newVersion);

                    return new MergeResult(true,
                            String.format("成功原子性合并 %d 个版本，创建新版本: %d", configs.size(), newVersion),
                            configs.size(), newVersion, mergedVersions, errors);
                } else {
                    return new MergeResult(false, "合并后的配置与当前配置相同，未创建新版本",
                            configs.size(), 0, mergedVersions, errors);
                }

            } catch (Exception e) {
                logger.error("保存或应用合并配置时发生错误，尝试恢复", e);

                // 错误恢复：恢复到备份配置
                if (backupConfig != null && backupVersion > 0) {
                    try {
                        configurationService.applyVersion(backupVersion);
                        logger.info("已恢复到备份配置版本: {}", backupVersion);
                        errors.add("操作失败，已恢复到版本 " + backupVersion);
                    } catch (Exception recoveryException) {
                        logger.error("恢复备份配置失败", recoveryException);
                        errors.add("恢复备份配置失败: " + recoveryException.getMessage());
                    }
                }

                String error = "原子性合并操作失败: " + e.getMessage();
                errors.add(error);
                return new MergeResult(false, error, configs.size(), 0, mergedVersions, errors);
            }

        } catch (Exception e) {
            String error = "原子性配置合并过程中发生未预期的错误: " + e.getMessage();
            logger.error(error, e);

            // 尝试恢复到备份配置
            if (backupConfig != null && backupVersion > 0) {
                try {
                    configurationService.applyVersion(backupVersion);
                    logger.info("已恢复到备份配置版本: {}", backupVersion);
                    errors.add("操作失败，已恢复到版本 " + backupVersion);
                } catch (Exception recoveryException) {
                    logger.error("恢复备份配置失败", recoveryException);
                    errors.add("恢复备份配置失败: " + recoveryException.getMessage());
                }
            }

            errors.add(error);
            return new MergeResult(false, error, 0, 0, mergedVersions, errors);
        }
    }

    /**
     * 判断是否应该因为冲突而中止合并操作
     *
     * @param conflicts 冲突列表
     * @return true如果应该中止，false如果可以继续
     */
    private boolean shouldAbortOnConflicts(List<String> conflicts) {
        // 定义严重冲突的关键词
        String[] criticalConflictKeywords = {
                "baseUrl", "endpoint", "apiKey", "authentication", "security"
        };

        for (String conflict : conflicts) {
            String lowerConflict = conflict.toLowerCase();
            for (String keyword : criticalConflictKeywords) {
                if (lowerConflict.contains(keyword.toLowerCase())) {
                    logger.warn("发现严重冲突（包含关键词 '{}'）: {}", keyword, conflict);
                    return true;
                }
            }
        }

        // 如果冲突数量过多，也应该中止
        if (conflicts.size() > 10) {
            logger.warn("冲突数量过多（{}），建议中止操作", conflicts.size());
            return true;
        }
        
        return false;
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
     * 获取基于版本管理系统的合并预览
     *
     * @return 合并预览结果
     */
    public Map<String, Object> getMergePreviewFromVersionManager() {
        Map<String, Object> preview = new HashMap<>();

        try {
            // 获取所有版本
            List<Integer> versions = configurationService.getAllVersions();
            if (versions.isEmpty()) {
                preview.put("error", "版本管理系统中未找到任何版本");
                return preview;
            }

            List<Map<String, Object>> configs = new ArrayList<>();
            Map<Integer, String> versionInfo = new TreeMap<>();

            // 读取所有版本的配置
            for (Integer version : versions) {
                try {
                    Map<String, Object> config = configurationService.getVersionConfig(version);
                    if (config != null && !config.isEmpty()) {
                        configs.add(config);
                        versionInfo.put(version, String.format("版本 %d", version));
                    }
                } catch (Exception e) {
                    logger.warn("读取版本 {} 配置失败: {}", version, e.getMessage());
                }
            }

            if (!configs.isEmpty()) {
                // 生成合并预览
                Map<String, Object> mergedConfig = mergeConfigs(configs);
                preview.put("mergedConfig", mergedConfig);
                preview.put("sourceVersions", versionInfo);
                preview.put("totalVersions", configs.size());
                preview.put("availableVersions", versions);

                // 添加合并统计信息
                Map<String, Object> mergeStats = generateMergeStatistics(configs, mergedConfig);
                preview.put("mergeStatistics", mergeStats);

                logger.info("生成了包含 {} 个版本的合并预览", configs.size());
            } else {
                preview.put("error", "没有找到有效的版本配置");
            }

        } catch (Exception e) {
            logger.error("生成基于版本管理系统的合并预览时发生错误", e);
            preview.put("error", e.getMessage());
        }

        return preview;
    }

    /**
     * 生成合并统计信息
     *
     * @param sourceConfigs 源配置列表
     * @param mergedConfig  合并后的配置
     * @return 统计信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> generateMergeStatistics(List<Map<String, Object>> sourceConfigs, Map<String, Object> mergedConfig) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 统计服务类型数量
            Set<String> allServiceTypes = new HashSet<>();
            int totalInstances = 0;

            for (Map<String, Object> config : sourceConfigs) {
                if (config.containsKey("services")) {
                    Map<String, Object> services = (Map<String, Object>) config.get("services");
                    allServiceTypes.addAll(services.keySet());

                    // 统计实例数量
                    for (Object serviceConfig : services.values()) {
                        if (serviceConfig instanceof Map) {
                            Map<String, Object> serviceMap = (Map<String, Object>) serviceConfig;
                            if (serviceMap.containsKey("instances")) {
                                List<Object> instances = (List<Object>) serviceMap.get("instances");
                                totalInstances += instances.size();
                            }
                        }
                    }
                }
            }

            // 统计合并后的结果
            int mergedServiceTypes = 0;
            int mergedInstances = 0;

            if (mergedConfig.containsKey("services")) {
                Map<String, Object> mergedServices = (Map<String, Object>) mergedConfig.get("services");
                mergedServiceTypes = mergedServices.size();

                for (Object serviceConfig : mergedServices.values()) {
                    if (serviceConfig instanceof Map) {
                        Map<String, Object> serviceMap = (Map<String, Object>) serviceConfig;
                        if (serviceMap.containsKey("instances")) {
                            List<Object> instances = (List<Object>) serviceMap.get("instances");
                            mergedInstances += instances.size();
                        }
                    }
                }
            }

            stats.put("sourceVersionCount", sourceConfigs.size());
            stats.put("totalServiceTypes", allServiceTypes.size());
            stats.put("totalSourceInstances", totalInstances);
            stats.put("mergedServiceTypes", mergedServiceTypes);
            stats.put("mergedInstances", mergedInstances);
            stats.put("instanceReduction", totalInstances - mergedInstances);

        } catch (Exception e) {
            logger.warn("生成合并统计信息时发生错误: {}", e.getMessage());
            stats.put("error", "统计信息生成失败: " + e.getMessage());
        }
        
        return stats;
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