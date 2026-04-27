package org.unreal.modelrouter.store.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Redis 状态持久化实现
 * 
 * Tier 1: 最高优先级，用于分布式共享状态
 * 当 Redis 可用时使用此实现
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Service
@ConditionalOnProperty(name = "jairouter.persistence.redis.enabled", havingValue = "true")
public class RedisStatePersistenceServiceImpl implements StatePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(RedisStatePersistenceServiceImpl.class);
    private static final String KEY_PREFIX = "jairouter:state:";
    private static final int TIER_PRIORITY = 1;

    @Autowired(required = false)
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Mono<Boolean> save(final StateType stateType, final String key,final Map<String, Object> stateData) {
        if (!isRedisAvailable()) {
            logger.debug("Redis not available, skipping save for key: {}", key);
            return Mono.just(false);
        }

        String redisKey = buildRedisKey(stateType, key);
        return Mono.fromCallable(() -> {
            try {
                return objectMapper.writeValueAsString(stateData);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize state data for key: {}", key, e);
                throw new RuntimeException(e);
            }
        })
        .flatMap(json -> reactiveRedisTemplate.opsForValue().set(redisKey, json, Duration.ofDays(7)))
        .doOnSuccess(saved -> logger.debug("State saved to Redis: {} (saved={})", redisKey, saved))
        .doOnError(e -> logger.warn("Failed to save state to Redis: {}", key, e.getMessage()))
        .thenReturn(true)
        .onErrorResume(e -> Mono.just(false));
    }

    @Override
    public Mono<Map<String, Object>> load(final StateType stateType, final String key) {
        if (!isRedisAvailable()) {
            logger.debug("Redis not available, returning empty map for key: {}", key);
            return Mono.just(new HashMap<String, Object>());
        }

        String redisKey = buildRedisKey(stateType, key);
        return reactiveRedisTemplate.opsForValue().get(redisKey)
                .flatMap(json -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> stateData = (Map<String, Object>) objectMapper.readValue(json, Map.class);
                        logger.debug("State loaded from Redis: {}", redisKey);
                        return Mono.just(stateData);
                    } catch (JsonProcessingException e) {
                        logger.error("Failed to deserialize state data from Redis: {}", key, e);
                        return Mono.just(new HashMap<String, Object>());
                    }
                })
                .defaultIfEmpty(new HashMap<String, Object>())
                .doOnError(e -> logger.warn("Failed to load state from Redis: {}", key, e.getMessage()))
                .onErrorResume(e -> Mono.just(new HashMap<String, Object>()));
    }

    @Override
    public Mono<Boolean> delete(final StateType stateType, final String key) {
        if (!isRedisAvailable()) {
            logger.debug("Redis not available, skipping delete for key: {}", key);
            return Mono.just(false);
        }

        String redisKey = buildRedisKey(stateType, key);
        return reactiveRedisTemplate.delete(redisKey)
                .doOnSuccess(deleted -> logger.debug("State deleted from Redis: {} (deleted={})", redisKey, deleted))
                .thenReturn(true)
                .onErrorResume(e -> Mono.just(false));
    }

    @Override
    public Mono<Boolean> exists(final StateType stateType, final String key) {
        if (!isRedisAvailable()) {
            return Mono.just(false);
        }

        String redisKey = buildRedisKey(stateType, key);
        return reactiveRedisTemplate.hasKey(redisKey)
                .onErrorResume(e -> Mono.just(false));
    }

    @Override
    public Mono<Iterable<String>> getAllKeys(final StateType stateType) {
        if (!isRedisAvailable()) {
            Set<String> emptySet = new HashSet<>();
            return Mono.just(emptySet);
        }

        String pattern = KEY_PREFIX + stateType.name().toLowerCase() + ":*";
        return reactiveRedisTemplate.keys(pattern)
                .map(this::extractKeyFromRedisKey)
                .collectList()
                .map(list -> (Iterable<String>) list)
                .onErrorResume(e -> Mono.just(new HashSet<>()));
    }

    @Override
    public Mono<Integer> saveBatch(final StateType stateType, final Map<String, Map<String, Object>> states) {
        if (!isRedisAvailable() || states.isEmpty()) {
            return Mono.just(0);
        }

        return Flux.fromIterable(states.entrySet())
                .flatMap(entry -> save(stateType, entry.getKey(), entry.getValue()))
                .filter(saved -> saved)
                .count()
                .map(Long::intValue)
                .doOnNext(count -> logger.debug("Batch saved {} states to Redis", count));
    }

    @Override
    public Mono<Map<String, Map<String, Object>>> loadBatch(final StateType stateType, final Iterable<String> keys) {
        if (!isRedisAvailable()) {
            return Mono.just(new HashMap<String, Map<String, Object>>());
        }

        return Flux.fromIterable(keys)
                .flatMap(key -> load(stateType, key)
                        .filter(data -> !data.isEmpty())
                        .map(data -> Map.entry(key, data)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Boolean> clearAll(final StateType stateType) {
        if (!isRedisAvailable()) {
            return Mono.just(false);
        }

        String pattern = KEY_PREFIX + stateType.name().toLowerCase() + ":*";
        return reactiveRedisTemplate.keys(pattern)
                .flatMap(reactiveRedisTemplate::delete)
                .then()
                .thenReturn(true)
                .doOnSuccess(v -> logger.info("All states cleared from Redis for type: {}", stateType))
                .onErrorResume(e -> Mono.just(false));
    }

    @Override
    public Mono<Boolean> isHealthy() {
        if (!isRedisAvailable()) {
            return Mono.just(false);
        }

        return reactiveRedisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .map(response -> "PONG".equalsIgnoreCase(response.toString()))
                .onErrorResume(e -> Mono.just(false));
    }

    @Override
    public String getTierName() {
        return "redis";
    }

    @Override
    public int getTierPriority() {
        return TIER_PRIORITY;
    }

    /**
     * 检查 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        return reactiveRedisTemplate != null;
    }

    /**
     * 构建 Redis 键
     */
    private String buildRedisKey(final StateType stateType, final String key) {
        return KEY_PREFIX + stateType.name().toLowerCase() + ":" + key;
    }

    /**
     * 从 Redis 键中提取原始键
     */
    private String extractKeyFromRedisKey(final String redisKey) {
        String prefix = KEY_PREFIX;
        if (redisKey.startsWith(prefix)) {
            String remaining = redisKey.substring(prefix.length());
            int colonIndex = remaining.indexOf(':');
            if (colonIndex > 0) {
                return remaining.substring(colonIndex + 1);
            }
        }
        return redisKey;
    }
}