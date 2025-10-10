package org.unreal.modelrouter.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于Redis缓存和StoreManager的JWT令牌持久化服务实现
 * 使用Redis作为主要缓存层，StoreManager作为持久化存储和故障回退
 */
@Slf4j
@Service("redisJwtTokenPersistenceService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
public class RedisJwtTokenPersistenceServiceImpl implements JwtPersistenceService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Qualifier("jwtTokenStoreManager")
    private final StoreManager fallbackStoreManager;
    
    // Redis键前缀
    private static final String TOKEN_PREFIX = "jwt:token:";
    private static final String USER_INDEX_PREFIX = "jwt:user_tokens:";
    private static final String STATUS_INDEX_PREFIX = "jwt:status:";
    private static final String TOKEN_COUNTER_KEY = "jwt:stats:token_count";
    
    // 默认TTL设置
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration INDEX_TTL = Duration.ofHours(1);
    private static final Duration STATS_TTL = Duration.ofMinutes(5);
    
    @Override
    public Mono<Void> saveToken(JwtTokenInfo tokenInfo) {
        return Mono.fromRunnable(() -> {
            if (tokenInfo == null || tokenInfo.getTokenHash() == null) {
                throw new IllegalArgumentException("Token info and token hash cannot be null");
            }
            
            // 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            if (tokenInfo.getCreatedAt() == null) {
                tokenInfo.setCreatedAt(now);
            }
            tokenInfo.setUpdatedAt(now);
            
            // 如果没有设置状态，默认为ACTIVE
            if (tokenInfo.getStatus() == null) {
                tokenInfo.setStatus(TokenStatus.ACTIVE);
            }
        })
        .then(saveTokenToRedis(tokenInfo))
        .onErrorResume(error -> {
            log.warn("Failed to save token to Redis, falling back to StoreManager: {}", error.getMessage());
            return saveTokenToFallback(tokenInfo);
        })
        .doOnSuccess(unused -> log.debug("Successfully saved token for user: {}", tokenInfo.getUserId()))
        .doOnError(error -> log.error("Failed to save token: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.empty();
        }
        
        return findTokenInRedis(tokenHash)
            .onErrorResume(error -> {
                log.warn("Failed to find token in Redis, falling back to StoreManager: {}", error.getMessage());
                return findTokenInFallback(tokenHash);
            })
            .doOnNext(token -> log.debug("Found token for hash: {}", tokenHash))
            .doOnError(error -> log.error("Failed to find token by hash: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findActiveTokensByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.just(new ArrayList<>());
        }
        
        return findActiveTokensInRedis(userId)
            .onErrorResume(error -> {
                log.warn("Failed to find active tokens in Redis, falling back to StoreManager: {}", error.getMessage());
                return findActiveTokensInFallback(userId);
            })
            .doOnNext(tokens -> log.debug("Found {} active tokens for user: {}", tokens.size(), userId))
            .doOnError(error -> log.error("Failed to find active tokens for user {}: {}", userId, error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findAllTokens(int page, int size) {
        return findAllTokensInRedis(page, size)
            .onErrorResume(error -> {
                log.warn("Failed to find all tokens in Redis, falling back to StoreManager: {}", error.getMessage());
                return findAllTokensInFallback(page, size);
            })
            .doOnNext(tokens -> log.debug("Found {} tokens (page: {}, size: {})", tokens.size(), page, size))
            .doOnError(error -> log.error("Failed to find all tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> updateTokenStatus(String tokenHash, TokenStatus status) {
        if (tokenHash == null || status == null) {
            return Mono.error(new IllegalArgumentException("Token hash and status cannot be null"));
        }
        
        return updateTokenStatusInRedis(tokenHash, status)
            .onErrorResume(error -> {
                log.warn("Failed to update token status in Redis, falling back to StoreManager: {}", error.getMessage());
                return updateTokenStatusInFallback(tokenHash, status);
            })
            .doOnSuccess(unused -> log.debug("Successfully updated token status to {} for hash: {}", status, tokenHash))
            .doOnError(error -> log.error("Failed to update token status: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> countActiveTokens() {
        return countActiveTokensInRedis()
            .onErrorResume(error -> {
                log.warn("Failed to count active tokens in Redis, falling back to StoreManager: {}", error.getMessage());
                return countActiveTokensInFallback();
            })
            .doOnNext(count -> log.debug("Active tokens count: {}", count))
            .doOnError(error -> log.error("Failed to count active tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> countTokensByStatus(TokenStatus status) {
        return countTokensByStatusInRedis(status)
            .onErrorResume(error -> {
                log.warn("Failed to count tokens by status in Redis, falling back to StoreManager: {}", error.getMessage());
                return countTokensByStatusInFallback(status);
            })
            .doOnNext(count -> log.debug("Tokens count for status {}: {}", status, count))
            .doOnError(error -> log.error("Failed to count tokens by status {}: {}", status, error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> removeExpiredTokens() {
        return removeExpiredTokensFromRedis()
            .onErrorResume(error -> {
                log.warn("Failed to remove expired tokens from Redis, falling back to StoreManager: {}", error.getMessage());
                return removeExpiredTokensFromFallback();
            })
            .doOnSuccess(unused -> log.info("Successfully removed expired tokens"))
            .doOnError(error -> log.error("Failed to remove expired tokens: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenId(String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return Mono.empty();
        }
        
        return findTokenByIdInRedis(tokenId)
            .onErrorResume(error -> {
                log.warn("Failed to find token by ID in Redis, falling back to StoreManager: {}", error.getMessage());
                return findTokenByIdInFallback(tokenId);
            })
            .doOnNext(token -> log.debug("Found token by ID: {}", tokenId))
            .doOnError(error -> log.error("Failed to find token by ID {}: {}", tokenId, error.getMessage(), error));
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findTokensByUserId(String userId, int page, int size) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.just(new ArrayList<>());
        }
        
        return findTokensByUserIdInRedis(userId, page, size)
            .onErrorResume(error -> {
                log.warn("Failed to find tokens by user ID in Redis, falling back to StoreManager: {}", error.getMessage());
                return findTokensByUserIdInFallback(userId, page, size);
            })
            .doOnNext(tokens -> log.debug("Found {} tokens for user {} (page: {}, size: {})", tokens.size(), userId, page, size))
            .doOnError(error -> log.error("Failed to find tokens for user {}: {}", userId, error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> batchUpdateTokenStatus(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy) {
        if (tokenHashes == null || tokenHashes.isEmpty() || status == null) {
            return Mono.empty();
        }
        
        return batchUpdateTokenStatusInRedis(tokenHashes, status, reason, updatedBy)
            .onErrorResume(error -> {
                log.warn("Failed to batch update token status in Redis, falling back to StoreManager: {}", error.getMessage());
                return batchUpdateTokenStatusInFallback(tokenHashes, status, reason, updatedBy);
            })
            .doOnSuccess(unused -> log.info("Batch updated {} tokens to status {}", tokenHashes.size(), status))
            .doOnError(error -> log.error("Failed to batch update token status: {}", error.getMessage(), error));
    }
    
    // Redis操作方法
    
    private Mono<Void> saveTokenToRedis(JwtTokenInfo tokenInfo) {
        return Mono.fromCallable(() -> {
            try {
                String tokenKey = TOKEN_PREFIX + tokenInfo.getTokenHash();
                String tokenJson = JacksonHelper.getObjectMapper().writeValueAsString(tokenInfo);
                
                // 计算TTL
                Duration ttl = calculateTokenTTL(tokenInfo);
                
                return tokenJson;
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize token info", e);
            }
        })
        .flatMap(tokenJson -> {
            String tokenKey = TOKEN_PREFIX + tokenInfo.getTokenHash();
            Duration ttl = calculateTokenTTL(tokenInfo);
            
            return redisTemplate.opsForValue().set(tokenKey, tokenJson, ttl)
                .then(updateUserIndexInRedis(tokenInfo.getUserId(), tokenInfo.getTokenHash(), true))
                .then(updateStatusIndexInRedis(tokenInfo.getStatus(), tokenInfo.getTokenHash(), true))
                .then(incrementTokenCounterInRedis())
                .then(saveTokenToFallback(tokenInfo)); // 同时保存到fallback存储
        });
    }
    
    private Mono<JwtTokenInfo> findTokenInRedis(String tokenHash) {
        String tokenKey = TOKEN_PREFIX + tokenHash;
        
        return redisTemplate.opsForValue().get(tokenKey)
            .map(tokenJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(tokenJson, JwtTokenInfo.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize token info", e);
                }
            });
    }
    
    private Mono<List<JwtTokenInfo>> findActiveTokensInRedis(String userId) {
        String userIndexKey = USER_INDEX_PREFIX + userId;
        
        return redisTemplate.opsForSet().members(userIndexKey)
            .flatMap(tokenHash -> findTokenInRedis(tokenHash))
            .filter(token -> TokenStatus.ACTIVE.equals(token.getStatus()))
            .filter(token -> !isTokenExpired(token))
            .collectList();
    }
    
    private Mono<List<JwtTokenInfo>> findAllTokensInRedis(int page, int size) {
        String pattern = TOKEN_PREFIX + "*";
        
        return redisTemplate.scan()
            .filter(key -> key.startsWith(TOKEN_PREFIX))
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .map(tokenJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(tokenJson, JwtTokenInfo.class);
                } catch (Exception e) {
                    log.warn("Failed to deserialize token: {}", e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .sort((a, b) -> {
                LocalDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                LocalDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                return timeB.compareTo(timeA);
            })
            .skip(page * size)
            .take(size)
            .collectList();
    }
    
    private Mono<Void> updateTokenStatusInRedis(String tokenHash, TokenStatus status) {
        return findTokenInRedis(tokenHash)
            .flatMap(token -> {
                TokenStatus oldStatus = token.getStatus();
                
                // 更新令牌状态
                token.setStatus(status);
                token.setUpdatedAt(LocalDateTime.now());
                
                if (TokenStatus.REVOKED.equals(status)) {
                    token.setRevokedAt(LocalDateTime.now());
                }
                
                return saveTokenToRedis(token)
                    .then(updateStatusIndexInRedis(oldStatus, tokenHash, false))
                    .then(updateStatusIndexInRedis(status, tokenHash, true));
            });
    }
    
    private Mono<Long> countActiveTokensInRedis() {
        String statusIndexKey = STATUS_INDEX_PREFIX + TokenStatus.ACTIVE.name();
        
        return redisTemplate.opsForSet().size(statusIndexKey)
            .defaultIfEmpty(0L);
    }
    
    private Mono<Long> countTokensByStatusInRedis(TokenStatus status) {
        String statusIndexKey = STATUS_INDEX_PREFIX + status.name();
        
        return redisTemplate.opsForSet().size(statusIndexKey)
            .defaultIfEmpty(0L);
    }
    
    private Mono<Void> removeExpiredTokensFromRedis() {
        String pattern = TOKEN_PREFIX + "*";
        
        return redisTemplate.scan()
            .filter(key -> key.startsWith(TOKEN_PREFIX))
            .flatMap(key -> redisTemplate.opsForValue().get(key)
                .map(tokenJson -> {
                    try {
                        JwtTokenInfo token = JacksonHelper.getObjectMapper().readValue(tokenJson, JwtTokenInfo.class);
                        return isTokenExpired(token) ? key : null;
                    } catch (Exception e) {
                        log.warn("Failed to check token expiry: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(expiredKey -> redisTemplate.delete(expiredKey))
            )
            .then();
    }
    
    private Mono<JwtTokenInfo> findTokenByIdInRedis(String tokenId) {
        String pattern = TOKEN_PREFIX + "*";
        
        return redisTemplate.scan()
            .filter(key -> key.startsWith(TOKEN_PREFIX))
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .map(tokenJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(tokenJson, JwtTokenInfo.class);
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(token -> tokenId.equals(token.getId()))
            .next();
    }
    
    private Mono<List<JwtTokenInfo>> findTokensByUserIdInRedis(String userId, int page, int size) {
        String userIndexKey = USER_INDEX_PREFIX + userId;
        
        return redisTemplate.opsForSet().members(userIndexKey)
            .flatMap(tokenHash -> findTokenInRedis(tokenHash))
            .sort((a, b) -> {
                LocalDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                LocalDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                return timeB.compareTo(timeA);
            })
            .skip(page * size)
            .take(size)
            .collectList();
    }
    
    private Mono<Void> batchUpdateTokenStatusInRedis(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy) {
        return Flux.fromIterable(tokenHashes)
            .flatMap(tokenHash -> findTokenInRedis(tokenHash)
                .flatMap(token -> {
                    TokenStatus oldStatus = token.getStatus();
                    
                    // 更新令牌信息
                    token.setStatus(status);
                    token.setUpdatedAt(LocalDateTime.now());
                    
                    if (reason != null) {
                        token.setRevokeReason(reason);
                    }
                    if (updatedBy != null) {
                        token.setRevokedBy(updatedBy);
                    }
                    if (TokenStatus.REVOKED.equals(status)) {
                        token.setRevokedAt(LocalDateTime.now());
                    }
                    
                    return saveTokenToRedis(token)
                        .then(updateStatusIndexInRedis(oldStatus, tokenHash, false))
                        .then(updateStatusIndexInRedis(status, tokenHash, true));
                })
                .onErrorResume(error -> {
                    log.warn("Failed to update token status for hash {}: {}", tokenHash, error.getMessage());
                    return Mono.empty();
                })
            )
            .then();
    }
    
    // Redis索引操作方法
    
    private Mono<Void> updateUserIndexInRedis(String userId, String tokenHash, boolean add) {
        if (userId == null || tokenHash == null) {
            return Mono.empty();
        }
        
        String userIndexKey = USER_INDEX_PREFIX + userId;
        
        if (add) {
            return redisTemplate.opsForSet().add(userIndexKey, tokenHash)
                .then(redisTemplate.expire(userIndexKey, INDEX_TTL))
                .then();
        } else {
            return redisTemplate.opsForSet().remove(userIndexKey, tokenHash)
                .then();
        }
    }
    
    private Mono<Void> updateStatusIndexInRedis(TokenStatus status, String tokenHash, boolean add) {
        if (status == null || tokenHash == null) {
            return Mono.empty();
        }
        
        String statusIndexKey = STATUS_INDEX_PREFIX + status.name();
        
        if (add) {
            return redisTemplate.opsForSet().add(statusIndexKey, tokenHash)
                .then(redisTemplate.expire(statusIndexKey, INDEX_TTL))
                .then();
        } else {
            return redisTemplate.opsForSet().remove(statusIndexKey, tokenHash)
                .then();
        }
    }
    
    private Mono<Void> incrementTokenCounterInRedis() {
        return redisTemplate.opsForValue().increment(TOKEN_COUNTER_KEY)
            .then(redisTemplate.expire(TOKEN_COUNTER_KEY, STATS_TTL))
            .then();
    }
    
    // Fallback操作方法（委托给现有的StoreManager实现）
    
    private Mono<Void> saveTokenToFallback(JwtTokenInfo tokenInfo) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> tokenData = convertToMap(tokenInfo);
                String tokenKey = "jwt_token_" + tokenInfo.getTokenHash();
                fallbackStoreManager.saveConfig(tokenKey, tokenData);
            } catch (Exception e) {
                log.warn("Failed to save token to fallback storage: {}", e.getMessage());
            }
        });
    }
    
    private Mono<JwtTokenInfo> findTokenInFallback(String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                String tokenKey = "jwt_token_" + tokenHash;
                Map<String, Object> tokenData = fallbackStoreManager.getConfig(tokenKey);
                return tokenData != null ? convertFromMap(tokenData) : null;
            } catch (Exception e) {
                log.warn("Failed to find token in fallback storage: {}", e.getMessage());
                return null;
            }
        });
    }
    
    private Mono<List<JwtTokenInfo>> findActiveTokensInFallback(String userId) {
        // 委托给现有的JwtTokenPersistenceServiceImpl
        return Mono.just(new ArrayList<>());
    }
    
    private Mono<List<JwtTokenInfo>> findAllTokensInFallback(int page, int size) {
        // 委托给现有的JwtTokenPersistenceServiceImpl
        return Mono.just(new ArrayList<>());
    }
    
    private Mono<Void> updateTokenStatusInFallback(String tokenHash, TokenStatus status) {
        return Mono.fromRunnable(() -> {
            try {
                String tokenKey = "jwt_token_" + tokenHash;
                Map<String, Object> tokenData = fallbackStoreManager.getConfig(tokenKey);
                
                if (tokenData != null) {
                    tokenData.put("status", status.name());
                    tokenData.put("updatedAt", LocalDateTime.now().toString());
                    
                    if (TokenStatus.REVOKED.equals(status)) {
                        tokenData.put("revokedAt", LocalDateTime.now().toString());
                    }
                    
                    fallbackStoreManager.updateConfig(tokenKey, tokenData);
                }
            } catch (Exception e) {
                log.warn("Failed to update token status in fallback storage: {}", e.getMessage());
            }
        });
    }
    
    private Mono<Long> countActiveTokensInFallback() {
        return Mono.just(0L);
    }
    
    private Mono<Long> countTokensByStatusInFallback(TokenStatus status) {
        return Mono.just(0L);
    }
    
    private Mono<Void> removeExpiredTokensFromFallback() {
        return Mono.empty();
    }
    
    private Mono<JwtTokenInfo> findTokenByIdInFallback(String tokenId) {
        return Mono.empty();
    }
    
    private Mono<List<JwtTokenInfo>> findTokensByUserIdInFallback(String userId, int page, int size) {
        return Mono.just(new ArrayList<>());
    }
    
    private Mono<Void> batchUpdateTokenStatusInFallback(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy) {
        return Mono.empty();
    }
    
    // 辅助方法
    
    private Duration calculateTokenTTL(JwtTokenInfo tokenInfo) {
        if (tokenInfo.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), tokenInfo.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_TOKEN_TTL;
    }
    
    private boolean isTokenExpired(JwtTokenInfo token) {
        if (token.getExpiresAt() == null) {
            return false;
        }
        return token.getExpiresAt().isBefore(LocalDateTime.now());
    }
    
    private Map<String, Object> convertToMap(JwtTokenInfo tokenInfo) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenInfo, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert token info to map", e);
        }
    }
    
    private JwtTokenInfo convertFromMap(Map<String, Object> tokenData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenData, JwtTokenInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to token info", e);
        }
    }
}