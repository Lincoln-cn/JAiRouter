package org.unreal.modelrouter.auth.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback黑名单操作封装
 * 处理当Redis不可用时的StoreManager降级存储操作
 */
@Slf4j
@RequiredArgsConstructor
public class FallbackBlacklistOperations {

    private final StoreManager fallbackStoreManager;

    private static final String BLACKLIST_KEY_PREFIX = "jwt_blacklist_";

    /**
     * 添加黑名单条目到Fallback存储
     */
    public Mono<Void> addToBlacklist(final TokenBlacklistEntry entry) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> entryData = convertToMap(entry);
                String blacklistKey = BLACKLIST_KEY_PREFIX + entry.getTokenHash();
                fallbackStoreManager.saveConfig(blacklistKey, entryData);
            } catch (Exception e) {
                log.warn("Failed to add token to blacklist in fallback storage: {}", e.getMessage());
            }
        });
    }

    /**
     * 检查token是否在Fallback黑名单中
     */
    public Mono<Boolean> isBlacklisted(final String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                String blacklistKey = BLACKLIST_KEY_PREFIX + tokenHash;
                Map<String, Object> entryData = fallbackStoreManager.getConfig(blacklistKey);

                if (entryData == null) {
                    return false;
                }

                TokenBlacklistEntry entry = convertFromMap(entryData);

                if (entry.getExpiresAt() != null
                && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                    fallbackStoreManager.deleteConfig(blacklistKey);
                    return false;
                }

                return true;
            } catch (Exception e) {
                log.warn("Failed to check blacklist in fallback storage: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 从Fallback存储移除黑名单条目
     */
    public Mono<Void> removeFromBlacklist(final String tokenHash) {
        return Mono.fromRunnable(() -> {
            try {
                String blacklistKey = BLACKLIST_KEY_PREFIX + tokenHash;
                if (fallbackStoreManager.exists(blacklistKey)) {
                    fallbackStoreManager.deleteConfig(blacklistKey);
                }
            } catch (Exception e) {
                log.warn("Failed to remove token from blacklist in fallback storage: {}", e.getMessage());
            }
        });
    }

    /**
     * 获取Fallback黑名单大小（不支持，返回0）
     */
    public Mono<Long> getBlacklistSize() {
        return Mono.just(0L);
    }

    /**
     * 清理Fallback过期条目（不支持）
     */
    public Mono<Void> cleanupExpiredEntries() {
        return Mono.empty();
    }

    /**
     * 获取Fallback黑名单统计信息
     */
    public Mono<Map<String, Object>> getBlacklistStats(final int memoryCacheSize) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("error", "Fallback storage stats not available");
        stats.put("memoryCache", memoryCacheSize);
        return Mono.just(stats);
    }

    /**
     * 获取单个黑名单条目
     */
    public Mono<TokenBlacklistEntry> getBlacklistEntry(final String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                String blacklistKey = BLACKLIST_KEY_PREFIX + tokenHash;
                Map<String, Object> entryData = fallbackStoreManager.getConfig(blacklistKey);
                return entryData != null ? convertFromMap(entryData) : null;
            } catch (Exception e) {
                log.warn("Failed to get blacklist entry from fallback storage: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * 批量添加黑名单条目（不支持）
     */
    public Mono<Void> batchAddToBlacklist(final List<String> tokenHashes, final String reason, final String addedBy) {
        return Mono.empty();
    }

    /**
     * 获取即将过期的条目数量（不支持，返回0）
     */
    public Mono<Long> getExpiringEntriesCount(final int hoursUntilExpiry) {
        return Mono.just(0L);
    }

    // 私有辅助方法

    private Map<String, Object> convertToMap(final TokenBlacklistEntry entry) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entry, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert blacklist entry to map", e);
        }
    }

    private TokenBlacklistEntry convertFromMap(final Map<String, Object> entryData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entryData, TokenBlacklistEntry.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to blacklist entry", e);
        }
    }
}
