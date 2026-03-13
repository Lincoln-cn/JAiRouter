package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.util.JacksonHelper;
import org.unreal.modelrouter.util.PathSanitizer;
import org.unreal.modelrouter.util.SafeFileOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 文件存储响应式管理器
 * 纯响应式实现，文件操作在 boundedElastic 调度器上执行
 */
public class ReactiveFileStoreManager implements ReactiveVersionedStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveFileStoreManager.class);

    private final String storagePath;

    public ReactiveFileStoreManager(String storagePath) {
        this.storagePath = storagePath;
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            Path path = PathSanitizer.sanitizePath(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize storage directory: {}", storagePath, e);
            throw new RuntimeException("Failed to initialize storage directory", e);
        }
    }

    @Override
    public Mono<Void> saveConfig(String key, Map<String, Object> config) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path configPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + ".json");
                    SafeFileOperations.writeJsonFile(configPath, config, JacksonHelper.getObjectMapper());
                    return configPath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(path -> LOGGER.debug("Saved config for key: {}", key))
                .doOnError(e -> LOGGER.error("Failed to save config for key: {}", key, e))
                .then();
    }

    @Override
    public Mono<Map<String, Object>> getConfig(String key) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path configPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + ".json");
                    return SafeFileOperations.readJsonFile(configPath, JacksonHelper.getObjectMapper(), new TypeReference<Map<String, Object>>() {
                    });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("File does not exist")) {
                        LOGGER.debug("Config not found for key: {}", key);
                    } else {
                        LOGGER.error("Failed to read config for key: {}", key, e);
                    }
                })
                .onErrorReturn(e -> e.getMessage() != null && e.getMessage().contains("File does not exist"), (Map<String, Object>) null);
    }

    @Override
    public Mono<Void> deleteConfig(String key) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path configPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + ".json");
                    SafeFileOperations.deleteFile(configPath);
                    return configPath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(path -> LOGGER.info("Deleted config for key: {}", key))
                .doOnError(e -> LOGGER.error("Failed to delete config for key: {}", key, e))
                .then();
    }

    @Override
    public Flux<String> getAllKeys() {
        return Mono.fromCallable(() -> PathSanitizer.sanitizePath(storagePath))
                .flatMapMany(path -> {
                    if (!Files.exists(path) || !Files.isDirectory(path)) {
                        return Flux.empty();
                    }
                    return Mono.fromCallable(() -> {
                                try (Stream<Path> paths = Files.list(path)) {
                                    return paths
                                            .filter(p -> p.toString().endsWith(".json") && !p.toString().contains("@"))
                                            .map(p -> {
                                                String fileName = p.getFileName().toString();
                                                return fileName.substring(0, fileName.lastIndexOf(".json"));
                                            })
                                            .toList();
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                })
                .doOnError(e -> LOGGER.error("Failed to get all config keys", e));
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path configPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + ".json");
                    return Files.exists(configPath);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> LOGGER.error("Failed to check existence for key: {}", key, e))
                .onErrorReturn(false);
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
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path versionPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json");
                    SafeFileOperations.writeJsonFile(versionPath, config, JacksonHelper.getObjectMapper());
                    return versionPath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(path -> LOGGER.debug("Saved config version for key: {} with version: {}", key, version))
                .doOnError(e -> LOGGER.error("Failed to save config version for key: {}, version: {}", key, version, e))
                .then();
    }

    @Override
    public Flux<Integer> getConfigVersions(String key) {
        return Mono.fromCallable(() -> PathSanitizer.sanitizePath(storagePath))
                .flatMapMany(path -> {
                    if (!Files.exists(path) || !Files.isDirectory(path)) {
                        return Flux.empty();
                    }
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    return Mono.fromCallable(() -> {
                                try (Stream<Path> paths = Files.list(path)) {
                                    return paths
                                            .filter(p -> p.getFileName().toString().startsWith(sanitizedKey + "@"))
                                            .filter(p -> p.toString().endsWith(".json"))
                                            .map(p -> {
                                                String fileName = p.getFileName().toString();
                                                String versionPart = fileName.substring(
                                                        fileName.indexOf("@") + 1,
                                                        fileName.lastIndexOf(".json")
                                                );
                                                return Integer.parseInt(versionPart);
                                            })
                                            .sorted()
                                            .toList();
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                })
                .doOnError(e -> LOGGER.error("Failed to get config versions for key: {}", key, e));
    }

    @Override
    public Mono<Map<String, Object>> getConfigByVersion(String key, int version) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path versionPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json");
                    return SafeFileOperations.readJsonFile(versionPath, JacksonHelper.getObjectMapper(), new TypeReference<Map<String, Object>>() {
                    });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> LOGGER.error("Failed to read config version for key: {}, version: {}", key, version, e));
    }

    @Override
    public Mono<Void> deleteConfigVersion(String key, int version) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path versionPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json");
                    SafeFileOperations.deleteFile(versionPath);
                    return versionPath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(path -> LOGGER.debug("Deleted config version for key: {}, version: {}", key, version))
                .doOnError(e -> LOGGER.error("Failed to delete config version for key: {}, version: {}", key, version, e))
                .then();
    }

    @Override
    public Mono<Boolean> versionExists(String key, int version) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path versionPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json");
                    return Files.exists(versionPath);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(false);
    }

    @Override
    public Mono<String> getVersionFilePath(String key, int version) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    return PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json")
                            .toString();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<LocalDateTime> getVersionCreatedTime(String key, int version) {
        return Mono.fromCallable(() -> {
                    String sanitizedKey = PathSanitizer.sanitizeFileName(key);
                    Path versionPath = PathSanitizer.sanitizePath(storagePath)
                            .resolve(sanitizedKey + "@" + version + ".json");
                    return Files.readAttributes(versionPath, java.nio.file.attribute.BasicFileAttributes.class);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(attrs -> LocalDateTime.ofInstant(
                        attrs.creationTime().toInstant(),
                        ZoneId.systemDefault()
                ))
                .doOnError(e -> LOGGER.error("Failed to get version created time for key: {}, version: {}", key, version, e));
    }
}
