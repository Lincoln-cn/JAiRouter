package org.unreal.modelrouter.auth.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT黑名单索引和统计辅助类
 *
 * 从 JwtBlacklistServiceImpl 中提取的索引管理和统计方法。
 * 非Spring Bean，由 JwtBlacklistServiceImpl 直接构造并持有。
 *
 * @since v2.30.0
 */
@Slf4j
public class BlacklistIndexHelper {

    private static final String BLACKLIST_INDEX_KEY = "jwt_blacklist_index";
    private static final String BLACKLIST_STATS_KEY = "jwt_blacklist_stats";

    private final StoreManager storeManager;
    private final String blacklistPrefix;

    public BlacklistIndexHelper(final StoreManager storeManager, final String blacklistPrefix) {
        this.storeManager = storeManager;
        this.blacklistPrefix = blacklistPrefix;
    }

    /**
     * 更新黑名单索引
     */
    void updateBlacklistIndex(final String tokenHash, final boolean add) {
        try {
            Map<String, Object> indexData = storeManager.getConfig(BLACKLIST_INDEX_KEY);

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
            indexData.put("updatedAt", LocalDateTime.now());

            storeManager.saveConfig(BLACKLIST_INDEX_KEY, indexData);

        } catch (Exception e) {
            log.warn("Failed to update blacklist index: { }", e.getMessage());
        }
    }

    /**
     * 获取黑名单索引
     */
    List<String> getBlacklistIndex() {
        try {
            Map<String, Object> indexData = storeManager.getConfig(BLACKLIST_INDEX_KEY);

            if (indexData == null) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<String> tokenHashes = (List<String>) indexData.get("tokenHashes");
            return tokenHashes != null ? new ArrayList<>(tokenHashes) : new ArrayList<>();

        } catch (Exception e) {
            log.warn("Failed to get blacklist index: { }", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 更新黑名单统计信息
     */
    void updateBlacklistStats(final long sizeChange, final long cleanedCount) {
        try {
            Map<String, Object> statsData = storeManager.getConfig(BLACKLIST_STATS_KEY);

            if (statsData == null) {
                statsData = new HashMap<>();
                statsData.put("totalAdded", 0L);
                statsData.put("totalRemoved", 0L);
                statsData.put("totalCleaned", 0L);
            }

            // 更新统计数据
            if (sizeChange > 0) {
                long totalAdded = ((Number) statsData.getOrDefault("totalAdded", 0L)).longValue();
                statsData.put("totalAdded", totalAdded + sizeChange);
            } else if (sizeChange < 0) {
                long totalRemoved = ((Number) statsData.getOrDefault("totalRemoved", 0L)).longValue();
                statsData.put("totalRemoved", totalRemoved + Math.abs(sizeChange));
            }

            if (cleanedCount > 0) {
                long totalCleaned = ((Number) statsData.getOrDefault("totalCleaned", 0L)).longValue();
                statsData.put("totalCleaned", totalCleaned + cleanedCount);
            }

            statsData.put("lastUpdated", LocalDateTime.now());

            storeManager.saveConfig(BLACKLIST_STATS_KEY, statsData);

        } catch (Exception e) {
            log.warn("Failed to update blacklist stats: { }", e.getMessage());
        }
    }

    /**
     * 计算过期条目数量
     */
    long countExpiredEntries() {
        try {
            long expiredCount = 0;
            List<String> blacklistTokens = getBlacklistIndex();

            for (String tokenHash : blacklistTokens) {
                try {
                    String blacklistKey = blacklistPrefix + tokenHash;
                    Map<String, Object> entryData = storeManager.getConfig(blacklistKey);

                    if (entryData != null) {
                        TokenBlacklistEntry entry = convertFromMap(entryData);
                        if (isEntryExpired(entry)) {
                            expiredCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to check expiry for blacklist entry: { }", tokenHash, e);
                }
            }

            return expiredCount;

        } catch (Exception e) {
            log.warn("Failed to count expired blacklist entries: { }", e.getMessage());
            return 0L;
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

    /**
     * 检查黑名单条目是否过期
     */
    private boolean isEntryExpired(final TokenBlacklistEntry entry) {
        return entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(LocalDateTime.now());
    }
}
