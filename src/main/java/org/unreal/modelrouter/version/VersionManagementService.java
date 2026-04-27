package org.unreal.modelrouter.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import org.unreal.modelrouter.version.strategy.SequentialVersionGenerator;
import org.unreal.modelrouter.version.strategy.VersionGenerator;
import org.unreal.modelrouter.version.strategy.VersionGeneratorFactory;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 版本管理服务
 * 统一管理配置版本的生成、查询和元数据维护
 * 使用策略模式支持多种版本号生成策略
 */
@Service
public class VersionManagementService {

    private static final Logger logger = LoggerFactory.getLogger(VersionManagementService.class);

    private static final String CURRENT_KEY = "model-router-config";
    private static final String METADATA_KEY = CURRENT_KEY + ".metadata";
    private static final String HISTORY_KEY = CURRENT_KEY + ".history";

    private final StoreManager storeManager;
    private final VersionGeneratorFactory versionGeneratorFactory;

    // 内存缓存
    private final Map<String, ConfigMetadata> configMetadataMap = new HashMap<>();
    private final Map<String, List<VersionInfo>> versionHistoryMap = new HashMap<>();

    // 默认使用顺序递增策略
    private VersionGenerator defaultVersionGenerator;

    @Autowired
    public VersionManagementService(final StoreManager storeManager,
                                    final VersionGeneratorFactory versionGeneratorFactory) {
        this.storeManager = storeManager;
        this.versionGeneratorFactory = versionGeneratorFactory;
    }

    @PostConstruct
    public void init() {
        // 设置默认版本生成策略
        this.defaultVersionGenerator = versionGeneratorFactory
                .getStrategy("SequentialVersionGenerator")
                .orElse(new SequentialVersionGenerator());

        // 加载已有数据
        loadExistingData();

        logger.info("版本管理服务初始化完成，使用策略: {}", defaultVersionGenerator.getStrategyName());
    }

    /**
     * 生成新版本号
     *
     * @param operation 操作描述
     * @param operatorId 操作者ID
     * @return 新版本号
     */
    public int generateNewVersion(final String operation,final String operatorId) {
        ConfigMetadata metadata = getOrCreateMetadata();
        List<Integer> existingVersions = getAllVersions();

        // 构建版本生成上下文
        VersionContext context = VersionContext.builder()
                .configKey(CURRENT_KEY)
                .currentVersion(metadata.getCurrentVersion())
                .existingVersions(existingVersions)
                .totalVersions(metadata.getTotalVersions())
                .operation(operation)
                .operatorId(operatorId)
                .build();

        // 使用策略生成版本号
        int newVersion = defaultVersionGenerator.generateNextVersion(context);

        logger.debug("生成新版本号: {} (策略: {})",
                newVersion, defaultVersionGenerator.getStrategyName());

        return newVersion;
    }

    /**
     * 记录新版本信息
     *
     * @param version 版本号
     * @param description 描述
     * @param userId 用户ID
     * @param changeType 变更类型
     */
    public void recordVersion(final int version,final String description,final String userId,
                              final VersionInfo.ChangeType changeType) {
        // 更新元数据
        ConfigMetadata metadata = getOrCreateMetadata();
        metadata.setCurrentVersion(version);
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy(userId != null ? userId : "system");
        metadata.setTotalVersions(metadata.getTotalVersions() + 1);
        metadata.addVersion(version);
        saveMetadata(metadata);

        // 添加到历史记录
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.setVersion(version);
        versionInfo.setCreatedAt(LocalDateTime.now());
        versionInfo.setCreatedBy(userId != null ? userId : "system");
        versionInfo.setDescription(description != null ? description : "配置更新");
        versionInfo.setChangeType(changeType);

        List<VersionInfo> history = versionHistoryMap.computeIfAbsent(CURRENT_KEY, k -> new ArrayList<>());
        history.add(versionInfo);
        saveVersionHistory(history);

        logger.info("记录新版本: {}, 描述: {}", version, description);
    }

    /**
     * 获取所有版本号列表
     *
     * @return 排序后的版本号列表
     */
    public List<Integer> getAllVersions() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null && !metadata.getExistingVersions().isEmpty()) {
            return metadata.getExistingVersions().stream()
                    .sorted()
                    .collect(Collectors.toList());
        }

        // 从存储中扫描
        List<Integer> versions = storeManager.getConfigVersions(CURRENT_KEY);
        versions.sort(Integer::compareTo);
        return versions;
    }

    /**
     * 获取当前版本号
     *
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        return metadata != null ? metadata.getCurrentVersion() : 0;
    }

    /**
     * 删除版本
     *
     * @param version 要删除的版本号
     * @throws IllegalStateException 如果尝试删除当前版本或最后一个版本
     */
    public void deleteVersion(final int version) {
        // 检查是否是当前版本
        int currentVersion = getCurrentVersion();
        if (version == currentVersion) {
            throw new IllegalStateException(
                    String.format("不能删除当前版本 %d", version));
        }

        // 检查是否是最后一个版本
        List<Integer> allVersions = getAllVersions();
        if (allVersions.size() <= 1) {
            throw new IllegalStateException("不能删除最后一个版本");
        }

        // 执行删除
        storeManager.deleteConfigVersion(CURRENT_KEY, version);

        // 更新元数据
        ConfigMetadata metadata = getOrCreateMetadata();
        metadata.removeVersion(version);
        metadata.setTotalVersions(Math.max(0, metadata.getTotalVersions() - 1));
        saveMetadata(metadata);

        // 更新历史记录
        List<VersionInfo> history = versionHistoryMap.get(CURRENT_KEY);
        if (history != null) {
            history.removeIf(v -> v.getVersion() == version);
            saveVersionHistory(history);
        }

        logger.info("删除版本: {}", version);
    }

    /**
     * 获取版本历史
     *
     * @return 版本信息列表
     */
    public List<VersionInfo> getVersionHistory() {
        return versionHistoryMap.getOrDefault(CURRENT_KEY, new ArrayList<>());
    }

    /**
     * 获取配置元数据
     *
     * @return 元数据
     */
    public ConfigMetadata getMetadata() {
        return configMetadataMap.get(CURRENT_KEY);
    }

    /**
     * 检查版本是否存在
     *
     * @param version 版本号
     * @return true 如果存在
     */
    public boolean versionExists(final int version) {
        return storeManager.versionExists(CURRENT_KEY, version);
    }

    // ============ 私有方法 ============

    private ConfigMetadata getOrCreateMetadata() {
        return configMetadataMap.computeIfAbsent(CURRENT_KEY, k -> {
            ConfigMetadata metadata = new ConfigMetadata();
            metadata.setConfigKey(CURRENT_KEY);
            metadata.setInitialVersion(1);
            metadata.setCurrentVersion(0);
            metadata.setCreatedAt(LocalDateTime.now());
            metadata.setTotalVersions(0);
            metadata.setExistingVersions(new HashSet<>());
            return metadata;
        });
    }

    private void saveMetadata(final ConfigMetadata metadata) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("configKey", metadata.getConfigKey());
            data.put("currentVersion", metadata.getCurrentVersion());
            data.put("initialVersion", metadata.getInitialVersion());
            data.put("createdAt", metadata.getCreatedAt());
            data.put("lastModified", metadata.getLastModified());
            data.put("lastModifiedBy", metadata.getLastModifiedBy());
            data.put("totalVersions", metadata.getTotalVersions());
            data.put("existingVersions", new ArrayList<>(metadata.getExistingVersions()));

            storeManager.saveConfig(METADATA_KEY, data);
            configMetadataMap.put(CURRENT_KEY, metadata);
        } catch (Exception e) {
            logger.error("保存配置元数据失败", e);
            throw new RuntimeException("保存配置元数据失败", e);
        }
    }

    private void saveVersionHistory(final List<VersionInfo> history) {
        try {
            List<Map<String, Object>> historyList = history.stream()
                    .map(this::convertVersionInfoToMap)
                    .collect(Collectors.toList());

            storeManager.saveConfig(HISTORY_KEY, Collections.singletonMap("history", historyList));
            versionHistoryMap.put(CURRENT_KEY, history);
        } catch (Exception e) {
            logger.error("保存版本历史失败", e);
            throw new RuntimeException("保存版本历史失败", e);
        }
    }

    private Map<String, Object> convertVersionInfoToMap(final VersionInfo info) {
        Map<String, Object> map = new HashMap<>();
        map.put("version", info.getVersion());
        map.put("createdAt", info.getCreatedAt());
        map.put("createdBy", info.getCreatedBy());
        map.put("description", info.getDescription());
        map.put("changeType", info.getChangeType().name());
        return map;
    }

    private VersionInfo convertMapToVersionInfo(final Map<String, Object> map) {
        VersionInfo info = new VersionInfo();
        info.setVersion(((Number) map.get("version")).intValue());
        info.setCreatedAt(JacksonHelper.covertStringToLocalDateTime((String) map.get("createdAt")));
        info.setCreatedBy((String) map.get("createdBy"));
        info.setDescription((String) map.get("description"));
        info.setChangeType(VersionInfo.ChangeType.valueOf((String) map.get("changeType")));
        return info;
    }

    @SuppressWarnings("unchecked")
    private void loadExistingData() {
        try {
            // 加载元数据
            if (storeManager.exists(METADATA_KEY)) {
                Map<String, Object> data = storeManager.getConfig(METADATA_KEY);
                if (data != null) {
                    ConfigMetadata metadata = new ConfigMetadata();
                    metadata.setConfigKey((String) data.get("configKey"));
                    metadata.setCurrentVersion(((Number) data.get("currentVersion")).intValue());
                    metadata.setInitialVersion(((Number) data.getOrDefault("initialVersion", 1)).intValue());
                    metadata.setCreatedAt(parseDateTime(data.get("createdAt")));
                    metadata.setLastModified(parseDateTime(data.get("lastModified")));
                    metadata.setLastModifiedBy((String) data.get("lastModifiedBy"));
                    metadata.setTotalVersions(((Number) data.getOrDefault("totalVersions", 0)).intValue());

                    List<Integer> versions = (List<Integer>) data.get("existingVersions");
                    if (versions != null) {
                        metadata.setExistingVersions(new HashSet<>(versions));
                    }

                    configMetadataMap.put(CURRENT_KEY, metadata);
                    logger.info("加载配置元数据，当前版本: {}", metadata.getCurrentVersion());
                }
            }

            // 加载历史记录
            if (storeManager.exists(HISTORY_KEY)) {
                Map<String, Object> data = storeManager.getConfig(HISTORY_KEY);
                if (data != null && data.containsKey("history")) {
                    List<Map<String, Object>> historyList = (List<Map<String, Object>>) data.get("history");
                    List<VersionInfo> history = historyList.stream()
                            .map(this::convertMapToVersionInfo)
                            .collect(Collectors.toList());
                    versionHistoryMap.put(CURRENT_KEY, history);
                    logger.info("加载版本历史，共 {} 个版本", history.size());
                }
            }
        } catch (Exception e) {
            logger.warn("加载版本数据时发生错误: {}", e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(final Object value) {
        if (value instanceof String) {
            return JacksonHelper.covertStringToLocalDateTime((String) value);
        }
        return null;
    }
}
