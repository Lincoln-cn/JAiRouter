package org.unreal.modelrouter.auth.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.TokenStatus;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT令牌索引管理器
 *
 * 负责管理令牌的用户索引、状态索引和计数器。
 * 从JwtTokenPersistenceServiceImpl中提取的索引管理功能。
 *
 * @author JAiRouter Team
 * @since 2.7.25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenIndexManager {

    private final StoreManager storeManager;

    // 存储键前缀
    private static final String USER_INDEX_PREFIX = "jwt_user_index_";
    private static final String STATUS_INDEX_PREFIX = "jwt_status_index_";
    private static final String TOKEN_COUNTER_KEY = "jwt_token_counter";

    /**
     * 更新用户索引
     *
     * @param userId    用户ID
     * @param tokenHash 令牌哈希
     * @param add       true-添加, false-移除
     */
    public void updateUserIndex(final String userId, final String tokenHash, final boolean add) {
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
            indexData.put("updatedAt", LocalDateTime.now());

            storeManager.saveConfig(indexKey, indexData);

        } catch (Exception e) {
            log.warn("Failed to update user index for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 更新状态索引
     *
     * @param status    令牌状态
     * @param tokenHash 令牌哈希
     * @param add       true-添加, false-移除
     */
    public void updateStatusIndex(final TokenStatus status, final String tokenHash, final boolean add) {
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
            indexData.put("updatedAt", LocalDateTime.now());

            storeManager.saveConfig(indexKey, indexData);

        } catch (Exception e) {
            log.warn("Failed to update status index for status {}: {}", status, e.getMessage());
        }
    }

    /**
     * 获取用户的令牌哈希列表
     *
     * @param userId 用户ID
     * @return 令牌哈希列表
     */
    public List<String> getUserTokenHashes(final String userId) {
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
     *
     * @param status 令牌状态
     * @return 令牌哈希列表
     */
    public List<String> getStatusTokenHashes(final TokenStatus status) {
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
    public void incrementTokenCounter() {
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

            counterData.put("updatedAt", LocalDateTime.now());
            storeManager.saveConfig(TOKEN_COUNTER_KEY, counterData);

        } catch (Exception e) {
            log.warn("Failed to increment token counter: {}", e.getMessage());
        }
    }
}
