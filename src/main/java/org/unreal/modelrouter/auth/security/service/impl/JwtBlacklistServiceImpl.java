package org.unreal.modelrouter.auth.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.common.util.JacksonHelper;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于StoreManager的JWT黑名单服务实现
 * 使用现有的StoreManager进行黑名单数据的持久化存储
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.persistence.enabled", havingValue = "true")
public class JwtBlacklistServiceImpl implements JwtBlacklistService {
    
    private final StoreManager storeManager;
    private final BlacklistIndexHelper blacklistIndexHelper;
    
    // 存储键前缀
    private static final String BLACKLIST_PREFIX = "jwt_blacklist_";
    private static final String BLACKLIST_INDEX_KEY = "jwt_blacklist_index";
    private static final String BLACKLIST_STATS_KEY = "jwt_blacklist_stats";
    
    @Autowired
    public JwtBlacklistServiceImpl(final StoreManager storeManager) {
        this.storeManager = storeManager;
        this.blacklistIndexHelper = new BlacklistIndexHelper(storeManager, BLACKLIST_PREFIX);
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("=== JwtBlacklistServiceImpl initialized with StoreManager: { } ===", 
                storeManager.getClass().getSimpleName());
        log.info("JWT Blacklist persistence is ENABLED and using H2 database storage");
    }
    
    @Override
    public Mono<Void> addToBlacklist(final String tokenHash, final String reason, final String addedBy) {
        return Mono.fromRunnable(() -> {
            try {
                if (tokenHash == null || tokenHash.trim().isEmpty()) {
                    throw new IllegalArgumentException("Token hash cannot be null or empty");
                }
                
                // 创建黑名单条目
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiresAt = now.plusDays(30); // 默认30天后过期
                TokenBlacklistEntry entry = new TokenBlacklistEntry(tokenHash, expiresAt, reason, addedBy, now);
                
                // 转换为Map进行存储
                Map<String, Object> entryData = convertToMap(entry);
                
                // 保存黑名单条目
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                storeManager.saveConfig(blacklistKey, entryData);
                
                // 更新黑名单索引
                blacklistIndexHelper.updateBlacklistIndex(tokenHash, true);
                
                // 更新统计信息
                blacklistIndexHelper.updateBlacklistStats(1, 0);
                
                log.debug("Successfully added token to blacklist: { }", tokenHash);
                
            } catch (Exception e) {
                log.error("Failed to add token to blacklist: { }", e.getMessage(), e);
                throw new RuntimeException("Failed to add token to blacklist", e);
            }
        });
    }
    
    @Override
    public Mono<Boolean> isBlacklisted(final String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                if (tokenHash == null || tokenHash.trim().isEmpty()) {
                    return false;
                }
                
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                Map<String, Object> entryData = storeManager.getConfig(blacklistKey);
                
                if (entryData == null) {
                    return false;
                }
                
                // 检查条目是否过期
                TokenBlacklistEntry entry = convertFromMap(entryData);
                if (isEntryExpired(entry)) {
                    // 异步清理过期条目
                    removeFromBlacklist(tokenHash).subscribe();
                    return false;
                }
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to check blacklist status for token: { }", e.getMessage(), e);
                return false;
            }
        });
    }
    
    @Override
    public Mono<Void> removeFromBlacklist(final String tokenHash) {
        return Mono.fromRunnable(() -> {
            try {
                if (tokenHash == null || tokenHash.trim().isEmpty()) {
                    return;
                }
                
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                
                // 检查条目是否存在
                if (storeManager.getConfig(blacklistKey) != null) {
                    // 删除黑名单条目
                    storeManager.deleteConfig(blacklistKey);
                    
                    // 更新黑名单索引
                    blacklistIndexHelper.updateBlacklistIndex(tokenHash, false);
                    
                    // 更新统计信息
                    blacklistIndexHelper.updateBlacklistStats(-1, 0);
                    
                    log.debug("Successfully removed token from blacklist: { }", tokenHash);
                }
                
            } catch (Exception e) {
                log.error("Failed to remove token from blacklist: { }", e.getMessage(), e);
                throw new RuntimeException("Failed to remove token from blacklist", e);
            }
        });
    }
    
    @Override
    public Mono<Long> getBlacklistSize() {
        return Mono.fromCallable(() -> {
            try {
                List<String> blacklistTokens = blacklistIndexHelper.getBlacklistIndex();
                return (long) blacklistTokens.size();
                
            } catch (Exception e) {
                log.error("Failed to get blacklist size: { }", e.getMessage(), e);
                return 0L;
            }
        });
    }
    
    @Override
    public Mono<Void> cleanupExpiredEntries() {
        return Mono.fromRunnable(() -> {
            try {
                long removedCount = 0;
                List<String> blacklistTokens = blacklistIndexHelper.getBlacklistIndex();
                
                for (String tokenHash : blacklistTokens) {
                    try {
                        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                        Map<String, Object> entryData = storeManager.getConfig(blacklistKey);
                        
                        if (entryData != null) {
                            TokenBlacklistEntry entry = convertFromMap(entryData);
                            
                            if (isEntryExpired(entry)) {
                                // 删除过期条目
                                storeManager.deleteConfig(blacklistKey);
                                blacklistIndexHelper.updateBlacklistIndex(tokenHash, false);
                                removedCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process blacklist entry for cleanup: { }", tokenHash, e);
                    }
                }
                
                if (removedCount > 0) {
                    // 更新统计信息
                    blacklistIndexHelper.updateBlacklistStats(-removedCount, removedCount);
                    log.info("Cleaned up { } expired blacklist entries", removedCount);
                }
                
            } catch (Exception e) {
                log.error("Failed to cleanup expired blacklist entries: { }", e.getMessage(), e);
                throw new RuntimeException("Failed to cleanup expired blacklist entries", e);
            }
        });
    }
    

    
    @Override
    public Mono<Map<String, Object>> getBlacklistStats() {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> stats = new HashMap<>();
                
                // 获取当前黑名单大小
                long currentSize = getBlacklistSize().block();
                stats.put("currentSize", currentSize);
                
                // 获取历史统计信息
                Map<String, Object> historicalStats = storeManager.getConfig(BLACKLIST_STATS_KEY);
                if (historicalStats != null) {
                    stats.putAll(historicalStats);
                }
                
                // 添加当前时间戳
                stats.put("lastUpdated", LocalDateTime.now());
                
                // 计算过期条目数量
                long expiredCount = blacklistIndexHelper.countExpiredEntries();
                stats.put("expiredEntries", expiredCount);
                
                // 计算活跃条目数量
                stats.put("activeEntries", currentSize - expiredCount);
                
                return stats;
                
            } catch (Exception e) {
                log.error("Failed to get blacklist stats: { }", e.getMessage(), e);
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", e.getMessage());
                errorStats.put("currentSize", 0L);
                return errorStats;
            }
        });
    }
    
    // 私有辅助方法
    
    /**
     * 将TokenBlacklistEntry转换为Map用于存储
     */
    private Map<String, Object> convertToMap(final TokenBlacklistEntry entry) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entry, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.error("Failed to convert blacklist entry to map: { }", e.getMessage(), e);
            throw new RuntimeException("Failed to convert blacklist entry to map", e);
        }
    }
    
    /**
     * 将Map转换为TokenBlacklistEntry
     */
    private TokenBlacklistEntry convertFromMap(final Map<String, Object> entryData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entryData, TokenBlacklistEntry.class);
        } catch (Exception e) {
            log.error("Failed to convert map to blacklist entry: { }", e.getMessage(), e);
            throw new RuntimeException("Failed to convert map to blacklist entry", e);
        }
    }
    
    @Override
    public Mono<TokenBlacklistEntry> getBlacklistEntry(final String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                if (tokenHash == null || tokenHash.trim().isEmpty()) {
                    return null;
                }
                
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                Map<String, Object> entryData = storeManager.getConfig(blacklistKey);
                
                if (entryData == null) {
                    return null;
                }
                
                return convertFromMap(entryData);
                
            } catch (Exception e) {
                log.error("Failed to get blacklist entry for token: { }", e.getMessage(), e);
                return null;
            }
        });
    }
    
    @Override
    public Mono<Void> batchAddToBlacklist(final List<String> tokenHashes, final String reason, final String addedBy) {
        return Mono.fromRunnable(() -> {
            try {
                if (tokenHashes == null || tokenHashes.isEmpty()) {
                    return;
                }
                
                int addedCount = 0;
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiresAt = now.plusDays(30);
                
                for (String tokenHash : tokenHashes) {
                    try {
                        if (tokenHash != null && !tokenHash.trim().isEmpty()) {
                            // 创建黑名单条目
                            TokenBlacklistEntry entry = new TokenBlacklistEntry(
                                    tokenHash, expiresAt, reason, addedBy, now);
                            
                            // 转换为Map进行存储
                            Map<String, Object> entryData = convertToMap(entry);
                            
                            // 保存黑名单条目
                            String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                            storeManager.saveConfig(blacklistKey, entryData);
                            
                            // 更新黑名单索引
                            blacklistIndexHelper.updateBlacklistIndex(tokenHash, true);
                            
                            addedCount++;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to add token to blacklist in batch: { }", tokenHash, e);
                    }
                }
                
                if (addedCount > 0) {
                    // 更新统计信息
                    blacklistIndexHelper.updateBlacklistStats(addedCount, 0);
                    log.info("Batch added { } tokens to blacklist", addedCount);
                }
                
            } catch (Exception e) {
                log.error("Failed to batch add tokens to blacklist: { }", e.getMessage(), e);
                throw new RuntimeException("Failed to batch add tokens to blacklist", e);
            }
        });
    }
    
    @Override
    public Mono<Boolean> isServiceAvailable() {
        return Mono.fromCallable(() -> {
            try {
                // 简单的健康检查 - 尝试获取黑名单大小
                getBlacklistSize().block();
                return true;
            } catch (Exception e) {
                log.warn("Blacklist service health check failed: { }", e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public Mono<Long> getExpiringEntriesCount(final int hoursUntilExpiry) {
        return Mono.fromCallable(() -> {
            try {
                long expiringCount = 0;
                LocalDateTime cutoffTime = LocalDateTime.now().plusHours(hoursUntilExpiry);
                List<String> blacklistTokens = blacklistIndexHelper.getBlacklistIndex();
                
                for (String tokenHash : blacklistTokens) {
                    try {
                        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                        Map<String, Object> entryData = storeManager.getConfig(blacklistKey);
                        
                        if (entryData != null) {
                            TokenBlacklistEntry entry = convertFromMap(entryData);
                            if (entry.getExpiresAt() != null
                                    && entry.getExpiresAt().isBefore(cutoffTime)
                                    && entry.getExpiresAt().isAfter(LocalDateTime.now())) {
                                expiringCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to check expiry for blacklist entry: { }", tokenHash, e);
                    }
                }
                
                return expiringCount;
                
            } catch (Exception e) {
                log.warn("Failed to count expiring blacklist entries: { }", e.getMessage());
                return 0L;
            }
        });
    }
    
    @Override
    public Mono<Long> cleanupExpiredEntriesWithCount() {
        return Mono.fromCallable(() -> {
            try {
                long removedCount = 0;
                List<String> blacklistTokens = blacklistIndexHelper.getBlacklistIndex();
                
                for (String tokenHash : blacklistTokens) {
                    try {
                        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                        Map<String, Object> entryData = storeManager.getConfig(blacklistKey);
                        
                        if (entryData != null) {
                            TokenBlacklistEntry entry = convertFromMap(entryData);
                            
                            if (isEntryExpired(entry)) {
                                // 删除过期条目
                                storeManager.deleteConfig(blacklistKey);
                                blacklistIndexHelper.updateBlacklistIndex(tokenHash, false);
                                removedCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process blacklist entry for cleanup: { }", tokenHash, e);
                    }
                }
                
                if (removedCount > 0) {
                    // 更新统计信息
                    blacklistIndexHelper.updateBlacklistStats(-removedCount, removedCount);
                    log.info("Cleaned up { } expired blacklist entries", removedCount);
                }
                
                return removedCount;
                
            } catch (Exception e) {
                log.error("Failed to cleanup expired blacklist entries: { }", e.getMessage(), e);
                return 0L;
            }
        });
    }
    
    /**
     * 检查黑名单条目是否过期
     */
    private boolean isEntryExpired(final TokenBlacklistEntry entry) {
        return entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(LocalDateTime.now());
    }
}
