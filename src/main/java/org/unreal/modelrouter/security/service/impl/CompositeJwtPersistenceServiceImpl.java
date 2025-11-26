package org.unreal.modelrouter.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 复合存储策略的JWT令牌持久化服务实现
 * 结合Redis缓存和StoreManager持久化存储，提供故障转移和数据同步功能
 */
@Slf4j
@Service("compositeJwtPersistenceService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.composite.enabled", havingValue = "true")
public class CompositeJwtPersistenceServiceImpl implements JwtPersistenceService {
    
    @Qualifier("redisJwtTokenPersistenceService")
    private final JwtPersistenceService redisService;
    
    @Qualifier("jwtTokenPersistenceService")
    private final JwtPersistenceService fallbackService;
    
    private final StorageHealthService storageHealthService;
    
    @Override
    public Mono<Void> saveToken(JwtTokenInfo tokenInfo) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时保存到Redis和StoreManager
                    return redisService.saveToken(tokenInfo)
                        .then(fallbackService.saveToken(tokenInfo))
                        .doOnSuccess(unused -> log.debug("Token saved to both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to save to Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.saveToken(tokenInfo);
                        });
                } else {
                    // Redis不健康时，只保存到StoreManager
                    log.debug("Redis unhealthy, saving token to StoreManager only");
                    return fallbackService.saveToken(tokenInfo);
                }
            })
            .doOnError(error -> log.error("Failed to save token: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenHash(String tokenHash) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.findByTokenHash(tokenHash)
                        .switchIfEmpty(fallbackService.findByTokenHash(tokenHash))
                        .doOnNext(token -> log.debug("Token found in storage"));
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, querying token from StoreManager");
                    return fallbackService.findByTokenHash(tokenHash);
                }
            })
            .doOnError(error -> log.error("Failed to find token by hash: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findActiveTokensByUserId(String userId) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.findActiveTokensByUserId(userId)
                        .flatMap(redisTokens -> {
                            if (redisTokens.isEmpty()) {
                                // Redis中没有数据，从StoreManager查询
                                return fallbackService.findActiveTokensByUserId(userId);
                            }
                            return Mono.just(redisTokens);
                        });
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, querying active tokens from StoreManager");
                    return fallbackService.findActiveTokensByUserId(userId);
                }
            })
            .doOnNext(tokens -> log.debug("Found {} active tokens for user: {}", tokens.size(), userId))
            .doOnError(error -> log.error("Failed to find active tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findAllTokens(int page, int size) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.findAllTokens(page, size)
                        .flatMap(redisTokens -> {
                            if (redisTokens.isEmpty()) {
                                // Redis中没有数据，从StoreManager查询
                                return fallbackService.findAllTokens(page, size);
                            }
                            return Mono.just(redisTokens);
                        });
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, querying all tokens from StoreManager");
                    return fallbackService.findAllTokens(page, size);
                }
            })
            .doOnNext(tokens -> log.debug("Found {} tokens (page: {}, size: {})", tokens.size(), page, size))
            .doOnError(error -> log.error("Failed to find all tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> updateTokenStatus(String tokenHash, TokenStatus status) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时更新Redis和StoreManager
                    return redisService.updateTokenStatus(tokenHash, status)
                        .then(fallbackService.updateTokenStatus(tokenHash, status))
                        .doOnSuccess(unused -> log.debug("Token status updated in both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to update status in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.updateTokenStatus(tokenHash, status);
                        });
                } else {
                    // Redis不健康时，只更新StoreManager
                    log.debug("Redis unhealthy, updating token status in StoreManager only");
                    return fallbackService.updateTokenStatus(tokenHash, status);
                }
            })
            .doOnError(error -> log.error("Failed to update token status: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> countActiveTokens() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis统计
                    return redisService.countActiveTokens()
                        .flatMap(redisCount -> {
                            if (redisCount == 0) {
                                // Redis中没有数据，从StoreManager统计
                                return fallbackService.countActiveTokens();
                            }
                            return Mono.just(redisCount);
                        });
                } else {
                    // Redis不健康时，从StoreManager统计
                    log.debug("Redis unhealthy, counting active tokens from StoreManager");
                    return fallbackService.countActiveTokens();
                }
            })
            .doOnNext(count -> log.debug("Active tokens count: {}", count))
            .doOnError(error -> log.error("Failed to count active tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> countTokensByStatus(TokenStatus status) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis统计
                    return redisService.countTokensByStatus(status)
                        .flatMap(redisCount -> {
                            if (redisCount == 0) {
                                // Redis中没有数据，从StoreManager统计
                                return fallbackService.countTokensByStatus(status);
                            }
                            return Mono.just(redisCount);
                        });
                } else {
                    // Redis不健康时，从StoreManager统计
                    log.debug("Redis unhealthy, counting tokens by status from StoreManager");
                    return fallbackService.countTokensByStatus(status);
                }
            })
            .doOnNext(count -> log.debug("Tokens count for status {}: {}", status, count))
            .doOnError(error -> log.error("Failed to count tokens by status: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> removeExpiredTokens() {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时清理Redis和StoreManager
                    return redisService.removeExpiredTokens()
                        .then(fallbackService.removeExpiredTokens())
                        .doOnSuccess(unused -> log.info("Expired tokens removed from both Redis and StoreManager"))
                        .onErrorResume(error -> {
                            log.warn("Failed to remove expired tokens from Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.removeExpiredTokens();
                        });
                } else {
                    // Redis不健康时，只清理StoreManager
                    log.debug("Redis unhealthy, removing expired tokens from StoreManager only");
                    return fallbackService.removeExpiredTokens();
                }
            })
            .doOnError(error -> log.error("Failed to remove expired tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenId(String tokenId) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.findByTokenId(tokenId)
                        .switchIfEmpty(fallbackService.findByTokenId(tokenId))
                        .doOnNext(token -> log.debug("Token found by ID: {}", tokenId));
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, querying token by ID from StoreManager");
                    return fallbackService.findByTokenId(tokenId);
                }
            })
            .doOnError(error -> log.error("Failed to find token by ID: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findTokensByUserId(String userId, int page, int size) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，优先从Redis查询
                    return redisService.findTokensByUserId(userId, page, size)
                        .flatMap(redisTokens -> {
                            if (redisTokens.isEmpty()) {
                                // Redis中没有数据，从StoreManager查询
                                return fallbackService.findTokensByUserId(userId, page, size);
                            }
                            return Mono.just(redisTokens);
                        });
                } else {
                    // Redis不健康时，从StoreManager查询
                    log.debug("Redis unhealthy, querying tokens by user ID from StoreManager");
                    return fallbackService.findTokensByUserId(userId, page, size);
                }
            })
            .doOnNext(tokens -> log.debug("Found {} tokens for user {} (page: {}, size: {})", tokens.size(), userId, page, size))
            .doOnError(error -> log.error("Failed to find tokens by user ID: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> batchUpdateTokenStatus(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy) {
        return storageHealthService.isRedisHealthy()
            .flatMap(isHealthy -> {
                if (isHealthy) {
                    // Redis健康时，同时更新Redis和StoreManager
                    return redisService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy)
                        .then(fallbackService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
                        .doOnSuccess(unused -> log.info("Batch updated {} tokens in both Redis and StoreManager", tokenHashes.size()))
                        .onErrorResume(error -> {
                            log.warn("Failed to batch update in Redis, falling back to StoreManager only: {}", error.getMessage());
                            return fallbackService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy);
                        });
                } else {
                    // Redis不健康时，只更新StoreManager
                    log.debug("Redis unhealthy, batch updating tokens in StoreManager only");
                    return fallbackService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy);
                }
            })
            .doOnError(error -> log.error("Failed to batch update token status: {}", error.getMessage(), error));
    }
}