package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;
import org.unreal.modelrouter.store.ReactiveVersionedStoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import org.unreal.modelrouter.util.SecurityUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本控制服务
 * 负责配置版本的生命周期管理
 */
@Service
public class VersionControlService {

    private static final Logger logger = LoggerFactory.getLogger(VersionControlService.class);
    private static final String CURRENT_KEY = "model-router-config";

    private final ReactiveVersionedStoreManager storeManager;

    // 版本控制相关字段
    private final Map<String, ConfigMetadata> configMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, List<VersionInfo>> versionHistoryMap = new ConcurrentHashMap<>();

    public VersionControlService(ReactiveVersionedStoreManager storeManager) {
        this.storeManager = storeManager;
    }

    /**
     * 初始化版本控制
     */
    public Mono<Void> initialize() {
        return loadExistingMetadata()
                .then(loadVersionHistory())
                .doOnSuccess(v -> logger.info("版本控制初始化完成"))
                .doOnError(e -> logger.error("版本控制初始化失败", e));
    }

    /**
     * 加载现有元数据
     */
    private Mono<Void> loadExistingMetadata() {
        return storeManager.exists(CURRENT_KEY + ".metadata")
                .flatMap(exists -> {
                    if (!exists) {
                        return createInitialMetadata();
                    }
                    return storeManager.getConfig(CURRENT_KEY + ".metadata")
                            .flatMap(this::parseMetadata)
                            .flatMap(metadata -> {
                                configMetadataMap.put(CURRENT_KEY, metadata);
                                return Mono.empty();
                            });
                });
    }

    /**
     * 加载版本历史
     */
    private Mono<Void> loadVersionHistory() {
        return storeManager.exists(CURRENT_KEY + ".history")
                .flatMap(exists -> {
                    if (!exists) {
                        versionHistoryMap.put(CURRENT_KEY, new ArrayList<>());
                        return Mono.empty();
                    }
                    return storeManager.getConfig(CURRENT_KEY + ".history")
                            .flatMap(this::parseVersionHistory)
                            .flatMap(history -> {
                                versionHistoryMap.put(CURRENT_KEY, history);
                                return Mono.empty();
                            });
                });
    }

    /**
     * 创建初始元数据
     */
    private Mono<Void> createInitialMetadata() {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setConfigKey(CURRENT_KEY);
        metadata.setInitialVersion(1);
        metadata.setCurrentVersion(0);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("system");
        metadata.setTotalVersions(0);
        metadata.setExistingVersions(new HashSet<>());

        configMetadataMap.put(CURRENT_KEY, metadata);
        versionHistoryMap.put(CURRENT_KEY, new ArrayList<>());

        return saveMetadata(metadata)
                .then(saveVersionHistory(CURRENT_KEY, new ArrayList<>()));
    }

    /**
     * 解析元数据
     */
    @SuppressWarnings("unchecked")
    private Mono<ConfigMetadata> parseMetadata(Map<String, Object> metadataMap) {
        return Mono.fromCallable(() -> {
            ConfigMetadata metadata = new ConfigMetadata();
            metadata.setConfigKey(CURRENT_KEY);
            if (metadataMap.containsKey("currentVersion")) {
                metadata.setCurrentVersion(((Number) metadataMap.get("currentVersion")).intValue());
            }
            if (metadataMap.containsKey("initialVersion")) {
                metadata.setInitialVersion(((Number) metadataMap.get("initialVersion")).intValue());
            }
            if (metadataMap.containsKey("createdAt")) {
                metadata.setCreatedAt(JacksonHelper.covertStringToLocalDateTime((String) metadataMap.get("createdAt")));
            }
            if (metadataMap.containsKey("lastModified")) {
                metadata.setLastModified(JacksonHelper.covertStringToLocalDateTime((String) metadataMap.get("lastModified")));
            }
            if (metadataMap.containsKey("lastModifiedBy")) {
                metadata.setLastModifiedBy((String) metadataMap.get("lastModifiedBy"));
            }
            if (metadataMap.containsKey("totalVersions")) {
                metadata.setTotalVersions(((Number) metadataMap.get("totalVersions")).intValue());
            }
            if (metadataMap.containsKey("existingVersions")) {
                List<Integer> versionList = (List<Integer>) metadataMap.get("existingVersions");
                if (versionList != null) {
                    metadata.setExistingVersions(new HashSet<>(versionList));
                }
            }
            return metadata;
        });
    }

    /**
     * 解析版本历史
     */
    @SuppressWarnings("unchecked")
    private Mono<List<VersionInfo>> parseVersionHistory(Map<String, Object> historyWrapper) {
        return Mono.fromCallable(() -> {
            List<VersionInfo> versionHistory = new ArrayList<>();
            if (historyWrapper.containsKey("history")) {
                List<Map<String, Object>> historyList = (List<Map<String, Object>>) historyWrapper.get("history");
                if (historyList != null) {
                    for (Map<String, Object> historyItem : historyList) {
                        VersionInfo versionInfo = new VersionInfo();
                        if (historyItem.containsKey("version")) {
                            versionInfo.setVersion(((Number) historyItem.get("version")).intValue());
                        }
                        if (historyItem.containsKey("createdAt")) {
                            versionInfo.setCreatedAt(JacksonHelper.covertStringToLocalDateTime((String) historyItem.get("createdAt")));
                        }
                        if (historyItem.containsKey("createdBy")) {
                            versionInfo.setCreatedBy((String) historyItem.get("createdBy"));
                        }
                        if (historyItem.containsKey("description")) {
                            versionInfo.setDescription((String) historyItem.get("description"));
                        }
                        if (historyItem.containsKey("changeType")) {
                            versionInfo.setChangeType(VersionInfo.ChangeType.valueOf((String) historyItem.get("changeType")));
                        }
                        versionHistory.add(versionInfo);
                    }
                }
            }
            return versionHistory;
        });
    }

    /**
     * 获取所有版本号
     */
    public Flux<Integer> getAllVersions() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata == null || metadata.getExistingVersions().isEmpty()) {
            return scanExistingVersions();
        }
        return Flux.fromIterable(metadata.getExistingVersions())
                .sort();
    }

    /**
     * 扫描实际存在的版本
     */
    private Flux<Integer> scanExistingVersions() {
        return storeManager.getConfigVersions(CURRENT_KEY)
                .sort();
    }

    /**
     * 获取指定版本的配置
     */
    public Mono<Map<String, Object>> getVersionConfig(int version) {
        if (version == 0) {
            return Mono.empty(); // YAML 原始配置，由调用方处理
        }
        return storeManager.getConfigByVersion(CURRENT_KEY, version);
    }

    /**
     * 创建新版本
     */
    public Mono<Integer> createNewVersion(Map<String, Object> config, String description, String userId) {
        return generateNextVersionNumber()
                .flatMap(version ->
                        storeManager.saveConfigVersion(CURRENT_KEY, config, version)
                                .then(updateMetadata(version, userId))
                                .then(addVersionHistory(version, description, userId))
                                .thenReturn(version)
                )
                .doOnSuccess(version -> logger.info("已保存配置为新版本：{}", version));
    }

    /**
     * 生成下一个版本号
     */
    private Mono<Integer> generateNextVersionNumber() {
        return Mono.fromCallable(() -> {
            long timestamp = System.currentTimeMillis();
            int timestampPart = (int) (timestamp % 1000000);
            int randomPart = (int) (new SecureRandom().nextDouble() * 1000);
            return timestampPart * 1000 + randomPart;
        });
    }

    /**
     * 更新元数据
     */
    private Mono<Void> updateMetadata(int newVersion, String userId) {
        return Mono.fromCallable(() -> {
            ConfigMetadata metadata = configMetadataMap.computeIfAbsent(CURRENT_KEY, k -> {
                ConfigMetadata newMetadata = new ConfigMetadata();
                newMetadata.setConfigKey(CURRENT_KEY);
                newMetadata.setInitialVersion(1);
                newMetadata.setCurrentVersion(0);
                newMetadata.setCreatedAt(LocalDateTime.now());
                newMetadata.setTotalVersions(0);
                return newMetadata;
            });

            metadata.setCurrentVersion(newVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            metadata.addVersion(newVersion);

            return metadata;
        }).flatMap(this::saveMetadata);
    }

    /**
     * 添加版本历史
     */
    private Mono<Void> addVersionHistory(int version, String description, String userId) {
        return Mono.fromCallable(() -> {
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(version);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy(userId != null ? userId : "system");
            versionInfo.setDescription(description != null ? description : "配置更新");
            versionInfo.setChangeType(VersionInfo.ChangeType.UPDATE);

            List<VersionInfo> versionHistory = versionHistoryMap.computeIfAbsent(CURRENT_KEY, k -> new ArrayList<>());
            versionHistory.add(versionInfo);

            return versionHistory;
        }).flatMap(history -> saveVersionHistory(CURRENT_KEY, history));
    }

    /**
     * 保存元数据
     */
    private Mono<Void> saveMetadata(ConfigMetadata metadata) {
        return Mono.fromCallable(() -> {
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("configKey", metadata.getConfigKey());
            metadataMap.put("currentVersion", metadata.getCurrentVersion());
            metadataMap.put("initialVersion", metadata.getInitialVersion());
            metadataMap.put("createdAt", metadata.getCreatedAt());
            metadataMap.put("lastModified", metadata.getLastModified());
            metadataMap.put("lastModifiedBy", metadata.getLastModifiedBy());
            metadataMap.put("totalVersions", metadata.getTotalVersions());
            metadataMap.put("existingVersions", new ArrayList<>(metadata.getExistingVersions()));
            return metadataMap;
        }).flatMap(map -> storeManager.saveConfig(CURRENT_KEY + ".metadata", map));
    }

    /**
     * 保存版本历史
     */
    private Mono<Void> saveVersionHistory(String key, List<VersionInfo> versionHistory) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> historyList = new ArrayList<>();
            for (VersionInfo versionInfo : versionHistory) {
                Map<String, Object> historyItem = new HashMap<>();
                historyItem.put("version", versionInfo.getVersion());
                historyItem.put("createdAt", versionInfo.getCreatedAt());
                historyItem.put("createdBy", versionInfo.getCreatedBy());
                historyItem.put("description", versionInfo.getDescription());
                historyItem.put("changeType", versionInfo.getChangeType().name());
                historyList.add(historyItem);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("history", historyList);
            return result;
        }).flatMap(map -> storeManager.saveConfig(key + ".history", map));
    }

    /**
     * 检查版本是否存在
     */
    public Mono<Boolean> versionExists(int version) {
        return storeManager.versionExists(CURRENT_KEY, version);
    }

    /**
     * 获取当前版本
     */
    public int getCurrentVersion() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        return metadata != null ? metadata.getCurrentVersion() : 0;
    }

    /**
     * 删除版本
     */
    public Mono<Void> deleteVersion(int version) {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null && version == metadata.getCurrentVersion()) {
            return Mono.error(new IllegalStateException(
                    String.format("不能删除当前版本 %d。请先应用其他版本后再删除此版本", version)));
        }

        return getAllVersions().collectList()
                .flatMap(versions -> {
                    if (versions.size() <= 1) {
                        return Mono.error(new IllegalStateException("不能删除最后一个版本，系统至少需要保留一个配置版本"));
                    }
                    return storeManager.deleteConfigVersion(CURRENT_KEY, version);
                })
                .then(updateMetadataAfterDelete(version))
                .then(updateHistoryAfterDelete(version))
                .doOnSuccess(v -> logger.info("成功删除配置版本: {}", version));
    }

    /**
     * 删除后更新元数据
     */
    private Mono<Void> updateMetadataAfterDelete(int version) {
        return Mono.fromRunnable(() -> {
            ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
            if (metadata != null) {
                metadata.setTotalVersions(Math.max(0, metadata.getTotalVersions() - 1));
                metadata.setLastModified(LocalDateTime.now());
                metadata.setLastModifiedBy(SecurityUtils.getCurrentUserId());
                metadata.removeVersion(version);
            }
        }).then(Mono.defer(() -> saveMetadata(configMetadataMap.get(CURRENT_KEY))));
    }

    /**
     * 删除后更新历史
     */
    private Mono<Void> updateHistoryAfterDelete(int version) {
        return Mono.fromRunnable(() -> {
            List<VersionInfo> versionHistory = versionHistoryMap.get(CURRENT_KEY);
            if (versionHistory != null) {
                versionHistory.removeIf(info -> info.getVersion() == version);
            }
        }).then(Mono.defer(() -> saveVersionHistory(CURRENT_KEY, versionHistoryMap.get(CURRENT_KEY))));
    }
}
