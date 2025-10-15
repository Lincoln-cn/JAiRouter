package org.unreal.modelrouter.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于StoreManager的JWT令牌持久化服务实现
 * 使用现有的StoreManager进行令牌数据的JSON序列化存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
public class JwtTokenPersistenceServiceImpl implements JwtPersistenceService {
    
    @Qualifier("jwtTokenStoreManager")
    private final StoreManager storeManager;
    
    // 存储键前缀
    private static final String TOKEN_PREFIX = "jwt_token_";
    private static final String USER_INDEX_PREFIX = "jwt_user_index_";
    private static final String STATUS_INDEX_PREFIX = "jwt_status_index_";
    private static final String TOKEN_COUNTER_KEY = "jwt_token_counter";
    
    @Override
    public Mono<Void> saveToken(JwtTokenInfo tokenInfo) {
        return Mono.fromRunnable(() -> {
            try {
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
                
                // 转换为Map进行存储
                Map<String, Object> tokenData = convertToMap(tokenInfo);
                
                // 保存令牌数据
                String tokenKey = TOKEN_PREFIX + tokenInfo.getTokenHash();
                storeManager.saveConfig(tokenKey, tokenData);
                
                // 更新用户索引
                updateUserIndex(tokenInfo.getUserId(), tokenInfo.getTokenHash(), true);
                
                // 更新状态索引
                updateStatusIndex(tokenInfo.getStatus(), tokenInfo.getTokenHash(), true);
                
                // 更新计数器
                incrementTokenCounter();
                
                log.debug("Successfully saved token for user: {}", tokenInfo.getUserId());
                
            } catch (Exception e) {
                log.error("Failed to save token: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to save token", e);
            }
        });
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenHash(String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                if (tokenHash == null || tokenHash.trim().isEmpty()) {
                    return null;
                }
                
                String tokenKey = TOKEN_PREFIX + tokenHash;
                Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                
                if (tokenData == null) {
                    return null;
                }
                
                return convertFromMap(tokenData);
                
            } catch (Exception e) {
                log.error("Failed to find token by hash: {}", e.getMessage(), e);
                return null;
            }
        });
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findActiveTokensByUserId(String userId) {
        return Mono.fromCallable(() -> {
            try {
                if (userId == null || userId.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                
                // 从用户索引获取令牌哈希列表
                List<String> tokenHashes = getUserTokenHashes(userId);
                
                // 获取所有令牌并过滤活跃的
                return tokenHashes.stream()
                    .map(hash -> {
                        try {
                            String tokenKey = TOKEN_PREFIX + hash;
                            Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                            return tokenData != null ? convertFromMap(tokenData) : null;
                        } catch (Exception e) {
                            log.warn("Failed to load token {}: {}", hash, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(token -> TokenStatus.ACTIVE.equals(token.getStatus()))
                    .filter(token -> !isTokenExpired(token))
                    .collect(Collectors.toList());
                
            } catch (Exception e) {
                log.error("Failed to find active tokens for user {}: {}", userId, e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findAllTokens(int page, int size) {
        return Mono.fromCallable(() -> {
            try {
                List<JwtTokenInfo> allTokens = new ArrayList<>();
                
                // 遍历所有令牌键
                for (String key : storeManager.getAllKeys()) {
                    if (key.startsWith(TOKEN_PREFIX)) {
                        try {
                            Map<String, Object> tokenData = storeManager.getConfig(key);
                            if (tokenData != null) {
                                JwtTokenInfo token = convertFromMap(tokenData);
                                allTokens.add(token);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load token from key {}: {}", key, e.getMessage());
                        }
                    }
                }
                
                // 按创建时间倒序排序
                allTokens.sort((a, b) -> {
                    LocalDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                    LocalDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                    return timeB.compareTo(timeA);
                });
                
                // 分页处理
                int start = page * size;
                int end = Math.min(start + size, allTokens.size());
                
                if (start >= allTokens.size()) {
                    return new ArrayList<>();
                }
                
                return allTokens.subList(start, end);
                
            } catch (Exception e) {
                log.error("Failed to find all tokens: {}", e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    @Override
    public Mono<Void> updateTokenStatus(String tokenHash, TokenStatus status) {
        return Mono.fromRunnable(() -> {
            try {
                if (tokenHash == null || status == null) {
                    throw new IllegalArgumentException("Token hash and status cannot be null");
                }
                
                String tokenKey = TOKEN_PREFIX + tokenHash;
                Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                
                if (tokenData == null) {
                    log.warn("Token not found for hash: {}", tokenHash);
                    return;
                }
                
                // 获取旧状态用于更新索引
                TokenStatus oldStatus = null;
                Object statusObj = tokenData.get("status");
                if (statusObj != null) {
                    oldStatus = TokenStatus.valueOf(statusObj.toString());
                }
                
                // 更新状态和时间
                tokenData.put("status", status.name());
                tokenData.put("updatedAt",LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                
                // 如果是撤销状态，设置撤销时间
                if (TokenStatus.REVOKED.equals(status)) {
                    tokenData.put("revokedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
                
                // 保存更新后的数据
                storeManager.updateConfig(tokenKey, tokenData);
                
                // 更新状态索引
                if (oldStatus != null && !oldStatus.equals(status)) {
                    updateStatusIndex(oldStatus, tokenHash, false);
                    updateStatusIndex(status, tokenHash, true);
                }
                
                log.debug("Successfully updated token status to {} for hash: {}", status, tokenHash);
                
            } catch (Exception e) {
                log.error("Failed to update token status: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to update token status", e);
            }
        });
    }
    
    @Override
    public Mono<Long> countActiveTokens() {
        return Mono.fromCallable(() -> {
            try {
                List<String> activeTokenHashes = getStatusTokenHashes(TokenStatus.ACTIVE);
                
                // 过滤掉已过期的令牌
                long count = activeTokenHashes.stream()
                    .mapToLong(hash -> {
                        try {
                            String tokenKey = TOKEN_PREFIX + hash;
                            Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                            if (tokenData != null) {
                                JwtTokenInfo token = convertFromMap(tokenData);
                                return isTokenExpired(token) ? 0 : 1;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check token expiry for hash {}: {}", hash, e.getMessage());
                        }
                        return 0;
                    })
                    .sum();
                
                return count;
                
            } catch (Exception e) {
                log.error("Failed to count active tokens: {}", e.getMessage(), e);
                return 0L;
            }
        });
    }
    
    @Override
    public Mono<Long> countTokensByStatus(TokenStatus status) {
        return Mono.fromCallable(() -> {
            try {
                List<String> tokenHashes = getStatusTokenHashes(status);
                return (long) tokenHashes.size();
                
            } catch (Exception e) {
                log.error("Failed to count tokens by status {}: {}", status, e.getMessage(), e);
                return 0L;
            }
        });
    }
    
    @Override
    public Mono<Void> removeExpiredTokens() {
        return Mono.fromRunnable(() -> {
            try {
                int removedCount = 0;
                
                // 遍历所有令牌
                for (String key : storeManager.getAllKeys()) {
                    if (key.startsWith(TOKEN_PREFIX)) {
                        try {
                            Map<String, Object> tokenData = storeManager.getConfig(key);
                            if (tokenData != null) {
                                JwtTokenInfo token = convertFromMap(tokenData);
                                
                                if (isTokenExpired(token)) {
                                    // 删除令牌
                                    storeManager.deleteConfig(key);
                                    
                                    // 从索引中移除
                                    if (token.getUserId() != null) {
                                        updateUserIndex(token.getUserId(), token.getTokenHash(), false);
                                    }
                                    if (token.getStatus() != null) {
                                        updateStatusIndex(token.getStatus(), token.getTokenHash(), false);
                                    }
                                    
                                    removedCount++;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to process token for expiry check: {}", key, e);
                        }
                    }
                }
                
                log.info("Removed {} expired tokens", removedCount);
                
            } catch (Exception e) {
                log.error("Failed to remove expired tokens: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to remove expired tokens", e);
            }
        });
    }
    
    @Override
    public Mono<JwtTokenInfo> findByTokenId(String tokenId) {
        return Mono.fromCallable(() -> {
            try {
                if (tokenId == null || tokenId.trim().isEmpty()) {
                    return null;
                }
                
                // 遍历所有令牌查找匹配的ID
                for (String key : storeManager.getAllKeys()) {
                    if (key.startsWith(TOKEN_PREFIX)) {
                        try {
                            Map<String, Object> tokenData = storeManager.getConfig(key);
                            if (tokenData != null) {
                                Object idObj = tokenData.get("id");
                                if (tokenId.equals(idObj)) {
                                    return convertFromMap(tokenData);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check token ID for key {}: {}", key, e.getMessage());
                        }
                    }
                }
                
                return null;
                
            } catch (Exception e) {
                log.error("Failed to find token by ID {}: {}", tokenId, e.getMessage(), e);
                return null;
            }
        });
    }
    
    @Override
    public Mono<List<JwtTokenInfo>> findTokensByUserId(String userId, int page, int size) {
        return Mono.fromCallable(() -> {
            try {
                if (userId == null || userId.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                
                // 从用户索引获取令牌哈希列表
                List<String> tokenHashes = getUserTokenHashes(userId);
                
                // 获取所有令牌
                List<JwtTokenInfo> tokens = tokenHashes.stream()
                    .map(hash -> {
                        try {
                            String tokenKey = TOKEN_PREFIX + hash;
                            Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                            return tokenData != null ? convertFromMap(tokenData) : null;
                        } catch (Exception e) {
                            log.warn("Failed to load token {}: {}", hash, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                        LocalDateTime timeB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());
                
                // 分页处理
                int start = page * size;
                int end = Math.min(start + size, tokens.size());
                
                if (start >= tokens.size()) {
                    return new ArrayList<>();
                }
                
                return tokens.subList(start, end);
                
            } catch (Exception e) {
                log.error("Failed to find tokens for user {}: {}", userId, e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    @Override
    public Mono<Void> batchUpdateTokenStatus(List<String> tokenHashes, TokenStatus status, String reason, String updatedBy) {
        return Mono.fromRunnable(() -> {
            try {
                if (tokenHashes == null || tokenHashes.isEmpty() || status == null) {
                    return;
                }
                
                int updatedCount = 0;
                
                for (String tokenHash : tokenHashes) {
                    try {
                        String tokenKey = TOKEN_PREFIX + tokenHash;
                        Map<String, Object> tokenData = storeManager.getConfig(tokenKey);
                        
                        if (tokenData != null) {
                            // 获取旧状态用于更新索引
                            TokenStatus oldStatus = null;
                            Object statusObj = tokenData.get("status");
                            if (statusObj != null) {
                                oldStatus = TokenStatus.valueOf(statusObj.toString());
                            }
                            
                            // 更新状态和相关信息
                            tokenData.put("status", status.name());
                            tokenData.put("updatedAt", LocalDateTime.now().toString());
                            
                            if (reason != null) {
                                tokenData.put("revokeReason", reason);
                            }
                            if (updatedBy != null) {
                                tokenData.put("revokedBy", updatedBy);
                            }
                            if (TokenStatus.REVOKED.equals(status)) {
                                tokenData.put("revokedAt", LocalDateTime.now().toString());
                            }
                            
                            // 保存更新后的数据
                            storeManager.updateConfig(tokenKey, tokenData);
                            
                            // 更新状态索引
                            if (oldStatus != null && !oldStatus.equals(status)) {
                                updateStatusIndex(oldStatus, tokenHash, false);
                                updateStatusIndex(status, tokenHash, true);
                            }
                            
                            updatedCount++;
                        }
                        
                    } catch (Exception e) {
                        log.warn("Failed to update token status for hash {}: {}", tokenHash, e.getMessage());
                    }
                }
                
                log.info("Batch updated {} tokens to status {}", updatedCount, status);
                
            } catch (Exception e) {
                log.error("Failed to batch update token status: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to batch update token status", e);
            }
        });
    }
    
    // 私有辅助方法
    
    /**
     * 将JwtTokenInfo转换为Map用于存储
     */
    private Map<String, Object> convertToMap(JwtTokenInfo tokenInfo) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenInfo, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to convert token info to map: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert token info to map", e);
        }
    }
    
    /**
     * 将Map转换为JwtTokenInfo
     */
    private JwtTokenInfo convertFromMap(Map<String, Object> tokenData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenData, JwtTokenInfo.class);
        } catch (Exception e) {
            log.error("Failed to convert map to token info: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert map to token info", e);
        }
    }
    
    /**
     * 检查令牌是否已过期
     */
    private boolean isTokenExpired(JwtTokenInfo token) {
        if (token.getExpiresAt() == null) {
            return false;
        }
        return token.getExpiresAt().isBefore(LocalDateTime.now());
    }
    
    /**
     * 更新用户索引
     */
    private void updateUserIndex(String userId, String tokenHash, boolean add) {
        if (userId == null || tokenHash == null) {
            return;
        }
        
        try {
            String indexKey = USER_INDEX_PREFIX + userId;
            Map<String, Object> indexData = storeManager.getConfig(indexKey);
            
            if (indexData == null) {
                indexData = new HashMap<>();
                indexData.put("tokenHashes", new ArrayList<String>());
            }
            
            @SuppressWarnings("unchecked")
            List<String> tokenHashes = (List<String>) indexData.get("tokenHashes");
            if (tokenHashes == null) {
                tokenHashes = new ArrayList<>();
            }
            
            if (add) {
                if (!tokenHashes.contains(tokenHash)) {
                    tokenHashes.add(tokenHash);
                }
            } else {
                tokenHashes.remove(tokenHash);
            }
            
            indexData.put("tokenHashes", tokenHashes);
            indexData.put("updatedAt", LocalDateTime.now().toString());
            
            storeManager.saveConfig(indexKey, indexData);
            
        } catch (Exception e) {
            log.warn("Failed to update user index for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * 更新状态索引
     */
    private void updateStatusIndex(TokenStatus status, String tokenHash, boolean add) {
        if (status == null || tokenHash == null) {
            return;
        }
        
        try {
            String indexKey = STATUS_INDEX_PREFIX + status.name();
            Map<String, Object> indexData = storeManager.getConfig(indexKey);
            
            if (indexData == null) {
                indexData = new HashMap<>();
                indexData.put("tokenHashes", new ArrayList<String>());
            }
            
            @SuppressWarnings("unchecked")
            List<String> tokenHashes = (List<String>) indexData.get("tokenHashes");
            if (tokenHashes == null) {
                tokenHashes = new ArrayList<>();
            }
            
            if (add) {
                if (!tokenHashes.contains(tokenHash)) {
                    tokenHashes.add(tokenHash);
                }
            } else {
                tokenHashes.remove(tokenHash);
            }
            
            indexData.put("tokenHashes", tokenHashes);
            indexData.put("updatedAt", LocalDateTime.now().toString());
            
            storeManager.saveConfig(indexKey, indexData);
            
        } catch (Exception e) {
            log.warn("Failed to update status index for status {}: {}", status, e.getMessage());
        }
    }
    
    /**
     * 获取用户的令牌哈希列表
     */
    private List<String> getUserTokenHashes(String userId) {
        try {
            String indexKey = USER_INDEX_PREFIX + userId;
            Map<String, Object> indexData = storeManager.getConfig(indexKey);
            
            if (indexData == null) {
                return new ArrayList<>();
            }
            
            @SuppressWarnings("unchecked")
            List<String> tokenHashes = (List<String>) indexData.get("tokenHashes");
            return tokenHashes != null ? new ArrayList<>(tokenHashes) : new ArrayList<>();
            
        } catch (Exception e) {
            log.warn("Failed to get user token hashes for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取指定状态的令牌哈希列表
     */
    private List<String> getStatusTokenHashes(TokenStatus status) {
        try {
            String indexKey = STATUS_INDEX_PREFIX + status.name();
            Map<String, Object> indexData = storeManager.getConfig(indexKey);
            
            if (indexData == null) {
                return new ArrayList<>();
            }
            
            @SuppressWarnings("unchecked")
            List<String> tokenHashes = (List<String>) indexData.get("tokenHashes");
            return tokenHashes != null ? new ArrayList<>(tokenHashes) : new ArrayList<>();
            
        } catch (Exception e) {
            log.warn("Failed to get status token hashes for status {}: {}", status, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 增加令牌计数器
     */
    private void incrementTokenCounter() {
        try {
            Map<String, Object> counterData = storeManager.getConfig(TOKEN_COUNTER_KEY);
            if (counterData == null) {
                counterData = new HashMap<>();
                counterData.put("count", 1L);
            } else {
                Object countObj = counterData.get("count");
                long count = countObj instanceof Number ? ((Number) countObj).longValue() : 0L;
                counterData.put("count", count + 1);
            }
            
            counterData.put("updatedAt", LocalDateTime.now().toString());
            storeManager.saveConfig(TOKEN_COUNTER_KEY, counterData);
            
        } catch (Exception e) {
            log.warn("Failed to increment token counter: {}", e.getMessage());
        }
    }
}