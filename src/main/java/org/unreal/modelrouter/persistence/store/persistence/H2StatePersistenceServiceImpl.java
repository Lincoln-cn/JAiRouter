package org.unreal.modelrouter.persistence.store.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * H2 数据库状态持久化实现 (基于 StoreManager)
 * 
 * Tier 2: 默认退坡层，复用现有 StoreManager 框架
 * 当 Redis 不可用时，使用 H2 数据库存储状态
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Service
public class H2StatePersistenceServiceImpl implements StatePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(H2StatePersistenceServiceImpl.class);
    private static final String KEY_PREFIX = "state.";
    private static final int TIER_PRIORITY = 2;

    @Autowired
    private StoreManager storeManager;

    @Override
    public Mono<Boolean> save(final StateType stateType, final String key,final Map<String, Object> stateData) {
        String storeKey = buildStoreKey(stateType, key);
        return Mono.fromCallable(() -> {
            try {
                storeManager.saveConfig(storeKey, stateData);
                logger.debug("State saved to H2: {}", storeKey);
                return true;
            } catch (Exception e) {
                logger.error("Failed to save state to H2: {}", storeKey, e);
                return false;
            }
        });
    }

    @Override
    public Mono<Map<String, Object>> load(final StateType stateType, final String key) {
        String storeKey = buildStoreKey(stateType, key);
        return Mono.fromCallable(() -> {
            Map<String, Object> stateData = storeManager.getConfig(storeKey);
            if (stateData == null || stateData.isEmpty()) {
                logger.debug("No state found in H2 for key: {}", storeKey);
                return new HashMap<>();
            }
            logger.debug("State loaded from H2: {}", storeKey);
            return stateData;
        });
    }

    @Override
    public Mono<Boolean> delete(final StateType stateType, final String key) {
        String storeKey = buildStoreKey(stateType, key);
        return Mono.fromCallable(() -> {
            try {
                storeManager.deleteConfig(storeKey);
                logger.debug("State deleted from H2: {}", storeKey);
                return true;
            } catch (Exception e) {
                logger.error("Failed to delete state from H2: {}", storeKey, e);
                return false;
            }
        });
    }

    @Override
    public Mono<Boolean> exists(final StateType stateType, final String key) {
        String storeKey = buildStoreKey(stateType, key);
        return Mono.fromCallable(() -> storeManager.exists(storeKey));
    }

    @Override
    public Mono<Iterable<String>> getAllKeys(final StateType stateType) {
        String prefix = KEY_PREFIX + stateType.name().toLowerCase() + ".";
        return Mono.fromCallable(() -> {
            Iterable<String> allKeys = storeManager.getAllKeys();
            Set<String> filteredKeys = StreamSupport.stream(allKeys.spliterator(), false)
                    .filter(k -> k.startsWith(prefix))
                    .map(this::extractKeyFromStoreKey)
                    .collect(Collectors.toSet());
            return filteredKeys;
        });
    }

    @Override
    public Mono<Integer> saveBatch(final StateType stateType, final Map<String, Map<String, Object>> states) {
        if (states.isEmpty()) {
            return Mono.just(0);
        }

        return Flux.fromIterable(states.entrySet())
                .flatMap(entry -> save(stateType, entry.getKey(), entry.getValue()))
                .filter(saved -> saved)
                .count()
                .map(Long::intValue);
    }

    @Override
    public Mono<Map<String, Map<String, Object>>> loadBatch(final StateType stateType, final Iterable<String> keys) {
        return Flux.fromIterable(keys)
                .flatMap(key -> load(stateType, key)
                        .filter(data -> !data.isEmpty())
                        .map(data -> Map.entry(key, data)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Boolean> clearAll(final StateType stateType) {
        String prefix = KEY_PREFIX + stateType.name().toLowerCase() + ".";
        return Mono.fromCallable(() -> {
            Iterable<String> allKeys = storeManager.getAllKeys();
            int deletedCount = 0;
            for (String key : allKeys) {
                if (key.startsWith(prefix)) {
                    storeManager.deleteConfig(key);
                    deletedCount++;
                }
            }
            logger.info("All states cleared from H2 for type: {} (deleted {} keys)", stateType, deletedCount);
            return true;
        });
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.fromCallable(() -> {
            try {
                storeManager.exists("test.health.check");
                return true;
            } catch (Exception e) {
                logger.warn("H2 health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public String getTierName() {
        return "h2";
    }

    @Override
    public int getTierPriority() {
        return TIER_PRIORITY;
    }

    private String buildStoreKey(final StateType stateType, final String key) {
        return KEY_PREFIX + stateType.name().toLowerCase() + "." + key;
    }

    private String extractKeyFromStoreKey(final String storeKey) {
        String prefix = KEY_PREFIX;
        if (storeKey.startsWith(prefix)) {
            String remaining = storeKey.substring(prefix.length());
            int dotIndex = remaining.indexOf('.');
            if (dotIndex > 0) {
                return remaining.substring(dotIndex + 1);
            }
        }
        return storeKey;
    }
}