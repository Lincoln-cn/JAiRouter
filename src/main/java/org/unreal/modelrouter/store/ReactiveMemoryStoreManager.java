package org.unreal.modelrouter.store;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存响应式存储管理器
 * 纯响应式实现，数据存储在内存中
 */
public class ReactiveMemoryStoreManager implements ReactiveVersionedStoreManager {

    private final Map<String, Map<String, Object>> configStore = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, VersionedConfig>> versionStore = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> saveConfig(String key, Map<String, Object> config) {
        return Mono.fromRunnable(() -> configStore.put(key, config));
    }

    @Override
    public Mono<Map<String, Object>> getConfig(String key) {
        return Mono.fromCallable(() -> configStore.get(key));
    }

    @Override
    public Mono<Void> deleteConfig(String key) {
        return Mono.fromRunnable(() -> {
            configStore.remove(key);
            versionStore.remove(key);
        });
    }

    @Override
    public Flux<String> getAllKeys() {
        return Flux.fromIterable(configStore.keySet());
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return Mono.fromCallable(() -> configStore.containsKey(key));
    }

    @Override
    public Mono<Void> updateConfig(String key, Map<String, Object> config) {
        return saveConfig(key, config);
    }

    @Override
    public Mono<Map<String, Object>> getLatestConfig(String configKey) {
        return getConfig(configKey);
    }

    @Override
    public Mono<Void> saveConfigVersion(String key, Map<String, Object> config, int version) {
        return Mono.fromRunnable(() -> {
            Map<Integer, VersionedConfig> versions = versionStore.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            versions.put(version, new VersionedConfig(config, LocalDateTime.now()));
        });
    }

    @Override
    public Flux<Integer> getConfigVersions(String key) {
        return Mono.fromCallable(() -> {
                    Map<Integer, VersionedConfig> versions = versionStore.get(key);
                    if (versions == null) {
                        return Set.<Integer>of();
                    }
                    return versions.keySet();
                })
                .flatMapMany(Flux::fromIterable)
                .sort();
    }

    @Override
    public Mono<Map<String, Object>> getConfigByVersion(String key, int version) {
        return Mono.fromCallable(() -> {
            Map<Integer, VersionedConfig> versions = versionStore.get(key);
            if (versions == null) {
                return null;
            }
            VersionedConfig versionedConfig = versions.get(version);
            return versionedConfig != null ? versionedConfig.config : null;
        });
    }

    @Override
    public Mono<Void> deleteConfigVersion(String key, int version) {
        return Mono.fromRunnable(() -> {
            Map<Integer, VersionedConfig> versions = versionStore.get(key);
            if (versions != null) {
                versions.remove(version);
            }
        });
    }

    @Override
    public Mono<Boolean> versionExists(String key, int version) {
        return Mono.fromCallable(() -> {
            Map<Integer, VersionedConfig> versions = versionStore.get(key);
            return versions != null && versions.containsKey(version);
        });
    }

    @Override
    public Mono<String> getVersionFilePath(String key, int version) {
        return Mono.just("memory://" + key + "/v" + version);
    }

    @Override
    public Mono<LocalDateTime> getVersionCreatedTime(String key, int version) {
        return Mono.fromCallable(() -> {
            Map<Integer, VersionedConfig> versions = versionStore.get(key);
            if (versions == null) {
                return null;
            }
            VersionedConfig versionedConfig = versions.get(version);
            return versionedConfig != null ? versionedConfig.createdAt : null;
        });
    }

    /**
     * 版本化配置数据
     */
    private record VersionedConfig(Map<String, Object> config, LocalDateTime createdAt) {
    }
}
