package org.unreal.modelrouter.store.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.util.JacksonHelper;
import org.unreal.modelrouter.util.PathSanitizer;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * 文件存储状态持久化实现
 * 
 * Tier 3: 兜底方案，极端情况下使用文件存储
 * 当 H2 数据库故障时，使用文件存储作为最终兜底
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Service
@ConditionalOnProperty(name = "jairouter.persistence.file.enabled", havingValue = "true", matchIfMissing = true)
public class FileStatePersistenceServiceImpl implements StatePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(FileStatePersistenceServiceImpl.class);
    private static final int TIER_PRIORITY = 3;

    @Value("${jairouter.persistence.file.path:./data/state}")
    private String storagePath;

    @Override
    public Mono<Boolean> save(final StateType stateType,final String key,final Map<String, Object> stateData) {
        return Mono.fromCallable(() -> {
            try {
                Path filePath = resolveFilePath(stateType, key);
                Files.createDirectories(filePath.getParent());
                
                JacksonHelper.getObjectMapper().writeValue(filePath.toFile(), stateData);
                logger.debug("State saved to file: {}", filePath);
                return true;
            } catch (IOException e) {
                logger.error("Failed to save state to file: {} for type {}", key, stateType, e);
                return false;
            }
        });
    }

    @Override
    public Mono<Map<String, Object>> load(final StateType stateType,final String key) {
        return Mono.fromCallable(() -> {
            try {
                Path filePath = resolveFilePath(stateType, key);
                if (!Files.exists(filePath)) {
                    logger.debug("No state file found for key: {}", key);
                    return new HashMap<>();
                }
                
                Map<String, Object> stateData = JacksonHelper.getObjectMapper().readValue(
                        filePath.toFile(), new TypeReference<Map<String, Object>>() {});
                logger.debug("State loaded from file: {}", filePath);
                return stateData;
            } catch (IOException e) {
                logger.error("Failed to load state from file: {} for type {}", key, stateType, e);
                return new HashMap<>();
            }
        });
    }

    @Override
    public Mono<Boolean> delete(final StateType stateType,final String key) {
        return Mono.fromCallable(() -> {
            try {
                Path filePath = resolveFilePath(stateType, key);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    logger.debug("State file deleted: {}", filePath);
                }
                return true;
            } catch (IOException e) {
                logger.error("Failed to delete state file: {} for type {}", key, stateType, e);
                return false;
            }
        });
    }

    @Override
    public Mono<Boolean> exists(final StateType stateType,final String key) {
        return Mono.fromCallable(() -> {
            Path filePath = resolveFilePath(stateType, key);
            return Files.exists(filePath);
        });
    }

    @Override
    public Mono<Iterable<String>> getAllKeys(final StateType stateType) {
        return Mono.fromCallable(() -> {
            Path dirPath = resolveDirectoryPath(stateType);
            if (!Files.exists(dirPath)) {
                return new HashMap<String, String>().keySet();
            }
            
            return StreamSupport.stream(Files.list(dirPath).spliterator(), false)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .collect(java.util.stream.Collectors.toList());
        });
    }

    @Override
    public Mono<Integer> saveBatch(final StateType stateType,final Map<String, Map<String, Object>> states) {
        if (states.isEmpty()) {
            return Mono.just(0);
        }

        int successCount = 0;
        for (Map.Entry<String, Map<String, Object>> entry : states.entrySet()) {
            Boolean result = save(stateType, entry.getKey(), entry.getValue()).block();
            if (Boolean.TRUE.equals(result)) {
                successCount++;
            }
        }
        return Mono.just(successCount);
    }

    @Override
    public Mono<Map<String, Map<String, Object>>> loadBatch(final StateType stateType,final Iterable<String> keys) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String key : keys) {
            Map<String, Object> stateData = load(stateType, key).block();
            if (stateData != null && !stateData.isEmpty()) {
                result.put(key, stateData);
            }
        }
        return Mono.just(result);
    }

    @Override
    public Mono<Boolean> clearAll(final StateType stateType) {
        return Mono.fromCallable(() -> {
            try {
                Path dirPath = resolveDirectoryPath(stateType);
                if (Files.exists(dirPath)) {
                    Files.walk(dirPath)
                            .filter(Files::isRegularFile)
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    logger.warn("Failed to delete file: {}", p, e);
                                }
                            });
                    Files.deleteIfExists(dirPath);
                }
                logger.info("All state files cleared for type: {}", stateType);
                return true;
            } catch (IOException e) {
                logger.error("Failed to clear all state files for type {}", stateType, e);
                return false;
            }
        });
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.fromCallable(() -> {
            try {
                Path basePath = PathSanitizer.sanitizePath(storagePath);
                Files.createDirectories(basePath);
                return Files.isDirectory(basePath) && Files.isWritable(basePath);
            } catch (Exception e) {
                logger.warn("File storage health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public String getTierName() {
        return "file";
    }

    @Override
    public int getTierPriority() {
        return TIER_PRIORITY;
    }

    /**
     * 解析文件存储路径
     */
    private Path resolveFilePath(final StateType stateType,final String key) {
        Path basePath = PathSanitizer.sanitizePath(storagePath);
        Path typePath = basePath.resolve(stateType.name().toLowerCase());
        String safeFileName = PathSanitizer.sanitizeFileName(key) + ".json";
        return typePath.resolve(safeFileName);
    }

    /**
     * 解析目录路径
     */
    private Path resolveDirectoryPath(final StateType stateType) {
        Path basePath = PathSanitizer.sanitizePath(storagePath);
        return basePath.resolve(stateType.name().toLowerCase());
    }
}