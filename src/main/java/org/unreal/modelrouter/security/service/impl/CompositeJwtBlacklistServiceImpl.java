package org.unreal.modelrouter.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 复合存储策略的JWT黑名单服务实现
 * 结合Redis缓存和StoreManager持久化存储，提供故障转移和数据同步功能
 */
@Slf4j
@Service("compositeJwtBlacklistService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.composite.enabled", havingValue = "true")
public class CompositeJwtBlacklistServiceImpl implements JwtBlacklistService {
    
    @Qualifier("redisJwtBlacklistService")
    private final JwtBlacklistService redisService;
    
    @Qualifier("jwtBlacklistService")
    private final JwtBlacklistService fallbackService;
    
    private final StorageHealthService storageHealthService;
    
    @Override
    public Mono<Void> addToBlacklist(String tokenHash, String reason, String addedBy) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时添加到Redis和StoreManager
                    return redisService.addToBlacklist(tokenHash, reason, addedBy)
                        .then(fallbackService.addToBlacklist(tokenHash, reason, addedBy))
                        .doOnSuccess(unused -> log.debug("Token added to blacklist in both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to add to blacklist in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.addToBlacklist(tokenHash, reason, addedBy);
                        });
                } else {
                    // Redis不健康时，只添加到StoreManager
                    log.debug("Redis unhealthy, adding token to blacklist in StoreManager only");
                    return fallbackService.addToBlacklist(tokenHash, reason, addedBy);
                }
            })
            .doOnError(error -> log.error("Failed to add token to blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> isBlacklisted(String tokenHash) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.isBlacklisted(tokenHash)
                        .flatMap(redisResult -> {
                            if (!redisResult) {
                                // Redis中没有，再查StoreManager
                                return fallbackService.isBlacklisted(tokenHash);
                            }
                            return Mono.just(true);
                        });
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, checking blacklist in StoreManager");
                    return fallbackService.isBlacklisted(tokenHash);
                }
            })
            .doOnNext(isBlacklisted -> log.debug("Token {} blacklist status: {}", tokenHash, isBlacklisted))
            .doOnError(error -> log.error("Failed to check blacklist status: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> removeFromBlacklist(String tokenHash) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时从Redis和StoreManager移除
                    return redisService.removeFromBlacklist(tokenHash)
                        .then(fallbackService.removeFromBlacklist(tokenHash))
                        .doOnSuccess(unused -> log.debug("Token removed from blacklist in both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to remove from blacklist in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.removeFromBlacklist(tokenHash);
                        });
                } else {
                    // Redis不健康时，只从StoreManager移除
                    log.debug("Redis unhealthy, removing token from blacklist in StoreManager only");
                    return fallbackService.removeFromBlacklist(tokenHash);
                }
            })
            .doOnError(error -> log.error("Failed to remove token from blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> getBlacklistSize() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis获取
                    return redisService.getBlacklistSize()
                        .flatMap(redisSize -> {
                            if (redisSize == 0) {
                                // Redis中没有数据，从StoreManager获取
                                return fallbackService.getBlacklistSize();
                            }
                            return Mono.just(redisSize);
                        });
                } else {
                    // Redis不健康时，从StoreManager获取
                    log.debug("Redis unhealthy, getting blacklist size from StoreManager");
                    return fallbackService.getBlacklistSize();
                }
            })
            .doOnNext(size -> log.debug("Blacklist size: {}", size))
            .doOnError(error -> log.error("Failed to get blacklist size: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> cleanupExpiredEntries() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时清理Redis和StoreManager
                    return redisService.cleanupExpiredEntries()
                        .then(fallbackService.cleanupExpiredEntries())
                        .doOnSuccess(unused -> log.info("Expired blacklist entries cleaned up in both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to cleanup expired entries in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.cleanupExpiredEntries();
                        });
                } else {
                    // Redis不健康时，只清理StoreManager
                    log.debug("Redis unhealthy, cleaning up expired entries in StoreManager only");
                    return fallbackService.cleanupExpiredEntries();
                }
            })
            .doOnError(error -> log.error("Failed to cleanup expired blacklist entries: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Map<String, Object>> getBlacklistStats() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis获取统计
                    return redisService.getBlacklistStats()
                        .flatMap(redisStats -> {
                            // 合并StoreManager的统计信息
                            return fallbackService.getBlacklistStats()
                                .map(fallbackStats -> {
                                    redisStats.put("fallbackStats", fallbackStats);
                                    return redisStats;
                                })
                                .onErrorReturn(redisStats);
                        });
                } else {
                    // Redis不健康时，从StoreManager获取统计
                    log.debug("Redis unhealthy, getting blacklist stats from StoreManager");
                    return fallbackService.getBlacklistStats();
                }
            })
            .doOnError(error -> log.error("Failed to get blacklist stats: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<TokenBlacklistEntry> getBlacklistEntry(String tokenHash) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.getBlacklistEntry(tokenHash)
                        .switchIfEmpty(fallbackService.getBlacklistEntry(tokenHash))
                        .doOnNext(entry -> log.debug("Blacklist entry found for token: {}", tokenHash));
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, getting blacklist entry from StoreManager");
                    return fallbackService.getBlacklistEntry(tokenHash);
                }
            })
            .doOnError(error -> log.error("Failed to get blacklist entry: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> batchAddToBlacklist(List<String> tokenHashes, String reason, String addedBy) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时批量添加到Redis和StoreManager
                    return redisService.batchAddToBlacklist(tokenHashes, reason, addedBy)
                        .then(fallbackService.batchAddToBlacklist(tokenHashes, reason, addedBy))
                        .doOnSuccess(unused -> log.info("Batch added {} tokens to blacklist in both Redis and StoreManager", tokenHashes.size()))
                        .onErrorResume(error -> {
                            log.warn("Failed to batch add to blacklist in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.batchAddToBlacklist(tokenHashes, reason, addedBy);
                        });
                } else {
                    // Redis不健康时，只批量添加到StoreManager
                    log.debug("Redis unhealthy, batch adding tokens to blacklist in StoreManager only");
                    return fallbackService.batchAddToBlacklist(tokenHashes, reason, addedBy);
                }
            })
            .doOnError(error -> log.error("Failed to batch add tokens to blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> isServiceAvailable() {
        return storageHealthService.getAllStorageHealth()
            .map(healthMap -> {
                // 如果任一存储系统健康，服务就可用
                return healthMap.values().stream().anyMatch(Boolean::booleanValue);
            })
            .doOnNext(available -> log.debug("Blacklist service availability: {}", available));
    }
    
    @Override
    public Mono<Long> getExpiringEntriesCount(int hoursUntilExpiry) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis获取
                    return redisService.getExpiringEntriesCount(hoursUntilExpiry)
                        .flatMap(redisCount -> {
                            if (redisCount == 0) {
                                // Redis中没有数据，从StoreManager获取
                                return fallbackService.getExpiringEntriesCount(hoursUntilExpiry);
                            }
                            return Mono.just(redisCount);
                        });
                } else {
                    // Redis不健康时，从StoreManager获取
                    log.debug("Redis unhealthy, getting expiring entries count from StoreManager");
                    return fallbackService.getExpiringEntriesCount(hoursUntilExpiry);
                }
            })
            .doOnNext(count -> log.debug("Expiring entries count ({}h): {}", hoursUntilExpiry, count))
            .doOnError(error -> log.error("Failed to get expiring entries count: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> cleanupExpiredEntriesWithCount() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时清理Redis和StoreManager并返回总数
                    return redisService.cleanupExpiredEntriesWithCount()
                        .flatMap(redisCount -> {
                            return fallbackService.cleanupExpiredEntriesWithCount()
                                .map(fallbackCount -> redisCount + fallbackCount);
                        })
                        .doOnSuccess(totalCount -> log.info("Cleaned up {} expired blacklist entries in both Redis and StoreManager", totalCount))
                        .onErrorResume(error -> {
                            log.warn("Failed to cleanup expired entries in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.cleanupExpiredEntriesWithCount();
                        });
                } else {
                    // Redis不健康时，只清理StoreManager
                    log.debug("Redis unhealthy, cleaning up expired entries in StoreManager only");
                    return fallbackService.cleanupExpiredEntriesWithCount();
                }
            })
            .doOnError(error -> log.error("Failed to cleanup expired blacklist entries with count: {}", error.getMessage(), error));
    }
}