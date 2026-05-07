package org.unreal.modelrouter.persistence.store.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组合状态持久化服务 - 三层退坡策略实现
 * 
 * 实现 Redis → H2 → File 三层退坡策略:
 * 1. Tier 1 (Redis): 最高优先级，用于分布式共享状态
 * 2. Tier 2 (H2): 默认退坡层，复用现有 StoreManager 框架
 * 3. Tier 3 (File): 兜底方案，极端情况下使用文件存储
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Service
@Primary
public class CompositeStatePersistenceServiceImpl implements StatePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(CompositeStatePersistenceServiceImpl.class);
    private static final int TIER_PRIORITY = 0;

    private final List<StatePersistenceService> persistenceServices = new java.util.ArrayList<>();
    private volatile StatePersistenceService activeService;
    private final Map<String, Boolean> healthStatusCache = new HashMap<>();

    @Autowired(required = false)
    private RedisStatePersistenceServiceImpl redisService;

    @Autowired
    private H2StatePersistenceServiceImpl h2Service;

    @Autowired(required = false)
    private FileStatePersistenceServiceImpl fileService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initializeActiveService();
    }

    private void initializeActiveService() {
        logger.info("Initializing state persistence service with fallback strategy");

        if (redisService != null) {
            persistenceServices.add(redisService);
        }
        persistenceServices.add(h2Service);
        if (fileService != null) {
            persistenceServices.add(fileService);
        }

        persistenceServices.sort((a, b) -> a.getTierPriority() - b.getTierPriority());
        selectBestAvailableService();

        logger.info("State persistence initialized. Active tier: {}", getActiveTierName());
    }

    private void selectBestAvailableService() {
        for (StatePersistenceService service : persistenceServices) {
            Boolean cachedHealth = healthStatusCache.get(service.getTierName());
            if (cachedHealth != null && cachedHealth) {
                activeService = service;
                logger.debug("Using cached healthy service: {}", service.getTierName());
                return;
            }

            Boolean isHealthy = service.isHealthy().block();
            healthStatusCache.put(service.getTierName(), isHealthy);

            if (Boolean.TRUE.equals(isHealthy)) {
                activeService = service;
                logger.info("Selected healthy service: {} (priority: {})", 
                        service.getTierName(), service.getTierPriority());
                return;
            } else {
                logger.warn("Service {} is unhealthy, trying next tier", service.getTierName());
            }
        }

        activeService = h2Service;
        logger.warn("All persistence services unhealthy, falling back to H2 as default");
    }

    @Override
    public Mono<Boolean> save(final StateType stateType, final String key,final Map<String, Object> stateData) {
        return executeWithFallback(
                () -> activeService.save(stateType, key, stateData),
                stateType, key);
    }

    @Override
    public Mono<Map<String, Object>> load(final StateType stateType, final String key) {
        return executeWithFallbackRead(stateType, key);
    }

    @Override
    public Mono<Boolean> delete(final StateType stateType, final String key) {
        return executeWithFallback(
                () -> activeService.delete(stateType, key),
                stateType, key);
    }

    @Override
    public Mono<Boolean> exists(final StateType stateType, final String key) {
        return activeService.exists(stateType, key);
    }

    @Override
    public Mono<Iterable<String>> getAllKeys(final StateType stateType) {
        return activeService.getAllKeys(stateType);
    }

    @Override
    public Mono<Integer> saveBatch(final StateType stateType, final Map<String, Map<String, Object>> states) {
        return executeWithFallbackBatch(
                () -> activeService.saveBatch(stateType, states),
                stateType);
    }

    @Override
    public Mono<Map<String, Map<String, Object>>> loadBatch(final StateType stateType, final Iterable<String> keys) {
        return executeWithFallbackReadBatch(stateType, keys);
    }

    @Override
    public Mono<Boolean> clearAll(final StateType stateType) {
        return executeWithFallback(
                () -> activeService.clearAll(stateType),
                stateType, null);
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return activeService != null ? activeService.isHealthy() : Mono.just(false);
    }

    @Override
    public String getTierName() {
        return "composite";
    }

    @Override
    public int getTierPriority() {
        return TIER_PRIORITY;
    }

    public String getActiveTierName() {
        return activeService != null ? activeService.getTierName() : "none";
    }

    public int getActiveTierPriority() {
        return activeService != null ? activeService.getTierPriority() : Integer.MAX_VALUE;
    }

    public boolean switchTier(final String tierName) {
        for (StatePersistenceService service : persistenceServices) {
            if (service.getTierName().equalsIgnoreCase(tierName)) {
                Boolean isHealthy = service.isHealthy().block();
                if (Boolean.TRUE.equals(isHealthy)) {
                    activeService = service;
                    logger.info("Manually switched to tier: {}", tierName);
                    return true;
                } else {
                    logger.warn("Cannot switch to unhealthy tier: {}", tierName);
                    return false;
                }
            }
        }
        logger.warn("Unknown tier name: {}", tierName);
        return false;
    }

    public void refreshHealthStatus() {
        healthStatusCache.clear();
        for (StatePersistenceService service : persistenceServices) {
            Boolean isHealthy = service.isHealthy().block();
            healthStatusCache.put(service.getTierName(), isHealthy);
            logger.debug("Health status refreshed for {}: {}", service.getTierName(), isHealthy);
        }
        selectBestAvailableService();
    }

    public Map<String, Boolean> getAllTierStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (StatePersistenceService service : persistenceServices) {
            Boolean cached = healthStatusCache.get(service.getTierName());
            if (cached == null) {
                cached = service.isHealthy().block();
                healthStatusCache.put(service.getTierName(), cached);
            }
            status.put(service.getTierName(), cached);
        }
        return status;
    }

    private Mono<Boolean> executeWithFallback(
            java.util.function.Supplier<Mono<Boolean>> operation,
            final StateType stateType, final String key) {
        return operation.get()
                .onErrorResume(e -> {
                    logger.warn("Operation failed on tier {}, trying fallback: {}", 
                            getActiveTierName(), e.getMessage());
                    return tryFallbackOperation(operation, stateType, key);
                });
    }

    private Mono<Boolean> tryFallbackOperation(
            java.util.function.Supplier<Mono<Boolean>> originalOperation,
            final StateType stateType, final String key) {
        int currentPriority = getActiveTierPriority();
        
        List<StatePersistenceService> lowerTierServices = persistenceServices.stream()
                .filter(s -> s.getTierPriority() > currentPriority)
                .collect(Collectors.toList());

        for (StatePersistenceService fallbackService : lowerTierServices) {
            Boolean isHealthy = fallbackService.isHealthy().block();
            if (Boolean.TRUE.equals(isHealthy)) {
                logger.info("Falling back to tier {}", fallbackService.getTierName());
                activeService = fallbackService;
                return originalOperation.get();
            }
        }

        logger.error("No healthy fallback service available");
        return Mono.just(false);
    }

    private Mono<Map<String, Object>> executeWithFallbackRead(final StateType stateType, final String key) {
        return activeService.load(stateType, key)
                .flatMap(data -> {
                    if (data.isEmpty()) {
                        logger.debug("No data in tier {} for key {}, trying lower tiers", 
                                getActiveTierName(), key);
                        return tryFallbackRead(stateType, key);
                    }
                    return Mono.just(data);
                })
                .onErrorResume(e -> {
                    logger.warn("Load failed on tier {}, trying fallback: {}", 
                            getActiveTierName(), e.getMessage());
                    return tryFallbackRead(stateType, key);
                });
    }

    private Mono<Map<String, Object>> tryFallbackRead(final StateType stateType, final String key) {
        int currentPriority = getActiveTierPriority();
        
        List<StatePersistenceService> lowerTierServices = persistenceServices.stream()
                .filter(s -> s.getTierPriority() > currentPriority)
                .collect(Collectors.toList());

        for (StatePersistenceService fallbackService : lowerTierServices) {
            Boolean isHealthy = fallbackService.isHealthy().block();
            if (Boolean.TRUE.equals(isHealthy)) {
                logger.info("Reading from fallback tier {} for key {}", 
                        fallbackService.getTierName(), key);
                
                return fallbackService.load(stateType, key)
                        .doOnNext(data -> {
                            if (!data.isEmpty()) {
                                activeService = fallbackService;
                            }
                        });
            }
        }

        logger.warn("No data found in any tier for key: {}", key);
        return Mono.just(new HashMap<>());
    }

    private Mono<Integer> executeWithFallbackBatch(
            java.util.function.Supplier<Mono<Integer>> operation,
            final StateType stateType) {
        return operation.get()
                .onErrorResume(e -> {
                    logger.warn("Batch operation failed on tier {}, trying fallback: {}", 
                            getActiveTierName(), e.getMessage());
                    
                    int currentPriority = getActiveTierPriority();
                    for (StatePersistenceService fallbackService : persistenceServices) {
                        if (fallbackService.getTierPriority() > currentPriority) {
                            Boolean isHealthy = fallbackService.isHealthy().block();
                            if (Boolean.TRUE.equals(isHealthy)) {
                                activeService = fallbackService;
                                return operation.get();
                            }
                        }
                    }
                    
                    return Mono.just(0);
                });
    }

    private Mono<Map<String, Map<String, Object>>> executeWithFallbackReadBatch(
            final StateType stateType, final Iterable<String> keys) {
        return activeService.loadBatch(stateType, keys)
                .onErrorResume(e -> {
                    logger.warn("Batch load failed on tier {}, trying fallback: {}", 
                            getActiveTierName(), e.getMessage());
                    
                    int currentPriority = getActiveTierPriority();
                    for (StatePersistenceService fallbackService : persistenceServices) {
                        if (fallbackService.getTierPriority() > currentPriority) {
                            Boolean isHealthy = fallbackService.isHealthy().block();
                            if (Boolean.TRUE.equals(isHealthy)) {
                                activeService = fallbackService;
                                return fallbackService.loadBatch(stateType, keys);
                            }
                        }
                    }
                    
                    return Mono.just(new HashMap<>());
                });
    }
}