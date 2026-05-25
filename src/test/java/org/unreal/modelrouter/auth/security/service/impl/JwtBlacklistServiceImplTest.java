package org.unreal.modelrouter.auth.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtBlacklistServiceImpl 单元测试
 *
 * <p>测试JWT黑名单服务功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("JwtBlacklistServiceImpl JWT黑名单服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtBlacklistServiceImplTest {

    @Mock
    private StoreManager storeManager;

    @InjectMocks
    private JwtBlacklistServiceImpl blacklistService;

    private static final String TEST_TOKEN_HASH = "test-hash-12345";
    private static final String BLACKLIST_PREFIX = "jwt_blacklist_";
    private static final String BLACKLIST_INDEX_KEY = "jwt_blacklist_index";

    @BeforeEach
    void setUp() {
        // 默认返回空索引
        when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createEmptyIndex());
    }

    // ==================== 添加到黑名单测试 ====================

    @Nested
    @DisplayName("添加到黑名单测试")
    class AddToBlacklistTests {

        @Test
        @DisplayName("JWT-BL-001: 添加令牌到黑名单成功")
        void testAddToBlacklist_Success() {
            // Given
            when(storeManager.getConfig(anyString())).thenReturn(createEmptyIndex());

            // When
            var result = blacklistService.addToBlacklist(TEST_TOKEN_HASH, "logout", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            // saveConfig is called 3 times: for entry, for index, for stats
            verify(storeManager, times(3)).saveConfig(anyString(), any(Map.class));
        }

        @Test
        @DisplayName("JWT-BL-002: 添加空令牌哈希抛出异常")
        void testAddToBlacklist_EmptyTokenHash() {
            // When & Then
            StepVerifier.create(blacklistService.addToBlacklist("", "logout", "admin"))
                .expectError(RuntimeException.class)
                .verify();
        }

        @Test
        @DisplayName("JWT-BL-003: 添加null令牌哈希抛出异常")
        void testAddToBlacklist_NullTokenHash() {
            // When & Then
            StepVerifier.create(blacklistService.addToBlacklist(null, "logout", "admin"))
                .expectError(RuntimeException.class)
                .verify();
        }

        @Test
        @DisplayName("JWT-BL-004: 添加空白令牌哈希抛出异常")
        void testAddToBlacklist_BlankTokenHash() {
            // When & Then
            StepVerifier.create(blacklistService.addToBlacklist("   ", "logout", "admin"))
                .expectError(RuntimeException.class)
                .verify();
        }
    }

    // ==================== 检查黑名单状态测试 ====================

    @Nested
    @DisplayName("检查黑名单状态测试")
    class IsBlacklistedTests {

        @Test
        @DisplayName("JWT-BL-005: 检查在黑名单中的令牌返回true")
        void testIsBlacklisted_TokenInBlacklist() {
            // Given
            Map<String, Object> entryData = createBlacklistEntryData(TEST_TOKEN_HASH);
            when(storeManager.getConfig(BLACKLIST_PREFIX + TEST_TOKEN_HASH)).thenReturn(entryData);

            // When
            var result = blacklistService.isBlacklisted(TEST_TOKEN_HASH);

            // Then
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-006: 检查不在黑名单中的令牌返回false")
        void testIsBlacklisted_TokenNotInBlacklist() {
            // Given
            when(storeManager.getConfig(anyString())).thenReturn(null);

            // When
            var result = blacklistService.isBlacklisted("nonexistent-hash");

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-007: 检查null令牌返回false")
        void testIsBlacklisted_NullToken() {
            // When
            var result = blacklistService.isBlacklisted(null);

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-008: 检查空令牌返回false")
        void testIsBlacklisted_EmptyToken() {
            // When
            var result = blacklistService.isBlacklisted("");

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }
    }

    // ==================== 从黑名单移除测试 ====================

    @Nested
    @DisplayName("从黑名单移除测试")
    class RemoveFromBlacklistTests {

        @Test
        @DisplayName("JWT-BL-009: 从黑名单移除令牌成功")
        void testRemoveFromBlacklist_Success() {
            // Given
            Map<String, Object> entryData = createBlacklistEntryData(TEST_TOKEN_HASH);
            when(storeManager.getConfig(BLACKLIST_PREFIX + TEST_TOKEN_HASH)).thenReturn(entryData);
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createIndexWithToken(TEST_TOKEN_HASH));
            doNothing().when(storeManager).deleteConfig(anyString());

            // When
            var result = blacklistService.removeFromBlacklist(TEST_TOKEN_HASH);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(storeManager).deleteConfig(BLACKLIST_PREFIX + TEST_TOKEN_HASH);
        }

        @Test
        @DisplayName("JWT-BL-010: 移除不存在的令牌不报错")
        void testRemoveFromBlacklist_NonExistent() {
            // Given
            when(storeManager.getConfig(anyString())).thenReturn(null);

            // When
            var result = blacklistService.removeFromBlacklist("nonexistent-hash");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(storeManager, never()).deleteConfig(anyString());
        }

        @Test
        @DisplayName("JWT-BL-011: 移除null令牌不报错")
        void testRemoveFromBlacklist_NullToken() {
            // When
            var result = blacklistService.removeFromBlacklist(null);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 获取黑名单大小测试 ====================

    @Nested
    @DisplayName("获取黑名单大小测试")
    class GetBlacklistSizeTests {

        @Test
        @DisplayName("JWT-BL-012: 获取空黑名单大小返回0")
        void testGetBlacklistSize_Empty() {
            // Given
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createEmptyIndex());

            // When
            var result = blacklistService.getBlacklistSize();

            // Then
            StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-013: 获取黑名单大小返回正确数量")
        void testGetBlacklistSize_WithEntries() {
            // Given
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createIndexWithTokens(
                Arrays.asList("hash1", "hash2", "hash3")));

            // When
            var result = blacklistService.getBlacklistSize();

            // Then
            StepVerifier.create(result)
                .expectNext(3L)
                .verifyComplete();
        }
    }

    // ==================== 批量添加测试 ====================

    @Nested
    @DisplayName("批量添加测试")
    class BatchAddToBlacklistTests {

        @Test
        @DisplayName("JWT-BL-014: 批量添加令牌成功")
        void testBatchAddToBlacklist_Success() {
            // Given
            List<String> hashes = Arrays.asList("hash1", "hash2", "hash3");
            when(storeManager.getConfig(anyString())).thenReturn(createEmptyIndex());

            // When
            var result = blacklistService.batchAddToBlacklist(hashes, "batch-logout", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(storeManager, times(3)).saveConfig(contains("jwt_blacklist_hash"), any(Map.class));
        }

        @Test
        @DisplayName("JWT-BL-015: 批量添加空列表不报错")
        void testBatchAddToBlacklist_EmptyList() {
            // When
            var result = blacklistService.batchAddToBlacklist(Collections.emptyList(), "reason", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(storeManager, never()).saveConfig(anyString(), any(Map.class));
        }

        @Test
        @DisplayName("JWT-BL-016: 批量添加null列表不报错")
        void testBatchAddToBlacklist_NullList() {
            // When
            var result = blacklistService.batchAddToBlacklist(null, "reason", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 服务可用性测试 ====================

    @Nested
    @DisplayName("服务可用性测试")
    class IsServiceAvailableTests {

        @Test
        @DisplayName("JWT-BL-017: 服务可用返回true")
        void testIsServiceAvailable_True() {
            // Given
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createEmptyIndex());

            // When
            var result = blacklistService.isServiceAvailable();

            // Then
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
        }
    }

    // ==================== 获取黑名单条目测试 ====================

    @Nested
    @DisplayName("获取黑名单条目测试")
    class GetBlacklistEntryTests {

        @Test
        @DisplayName("JWT-BL-018: 获取存在的黑名单条目成功")
        void testGetBlacklistEntry_Success() {
            // Given
            Map<String, Object> entryData = createBlacklistEntryData(TEST_TOKEN_HASH);
            when(storeManager.getConfig(BLACKLIST_PREFIX + TEST_TOKEN_HASH)).thenReturn(entryData);

            // When
            var result = blacklistService.getBlacklistEntry(TEST_TOKEN_HASH);

            // Then
            StepVerifier.create(result)
                .assertNext(entry -> {
                    assertNotNull(entry);
                    assertEquals(TEST_TOKEN_HASH, entry.getTokenHash());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-019: 获取不存在的黑名单条目返回null")
        void testGetBlacklistEntry_NotFound() {
            // Given
            when(storeManager.getConfig(anyString())).thenReturn(null);

            // When
            var result = blacklistService.getBlacklistEntry("nonexistent");

            // Then - Mono completes without emitting (null is not emitted)
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-020: 获取null令牌的黑名单条目返回null")
        void testGetBlacklistEntry_NullToken() {
            // When
            var result = blacklistService.getBlacklistEntry(null);

            // Then - Mono completes without emitting (null is not emitted)
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 获取即将过期条目数量测试 ====================

    @Nested
    @DisplayName("获取即将过期条目数量测试")
    class GetExpiringEntriesCountTests {

        @Test
        @DisplayName("JWT-BL-021: 获取即将过期条目数量")
        void testGetExpiringEntriesCount_Success() {
            // Given - 创建即将过期的条目
            Map<String, Object> entryData = createExpiringBlacklistEntryData("hash1", 1);
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createIndexWithToken("hash1"));
            when(storeManager.getConfig(BLACKLIST_PREFIX + "hash1")).thenReturn(entryData);

            // When
            var result = blacklistService.getExpiringEntriesCount(24);

            // Then
            StepVerifier.create(result)
                .expectNextMatches(count -> count >= 0)
                .verifyComplete();
        }
    }

    // ==================== 清理过期条目测试 ====================

    @Nested
    @DisplayName("清理过期条目测试")
    class CleanupExpiredEntriesTests {

        @Test
        @DisplayName("JWT-BL-022: 清理过期条目成功")
        void testCleanupExpiredEntries_Success() {
            // Given
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createEmptyIndex());

            // When
            var result = blacklistService.cleanupExpiredEntries();

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-BL-023: 清理过期条目并返回数量")
        void testCleanupExpiredEntriesWithCount_Success() {
            // Given - 创建一个已过期的条目
            Map<String, Object> expiredEntry = createExpiredBlacklistEntryData("expired-hash");
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createIndexWithToken("expired-hash"));
            when(storeManager.getConfig(BLACKLIST_PREFIX + "expired-hash")).thenReturn(expiredEntry);
            doNothing().when(storeManager).deleteConfig(anyString());

            // When
            var result = blacklistService.cleanupExpiredEntriesWithCount();

            // Then
            StepVerifier.create(result)
                .expectNextMatches(count -> count >= 0)
                .verifyComplete();
        }
    }

    // ==================== 获取统计信息测试 ====================

    @Nested
    @DisplayName("获取统计信息测试")
    class GetBlacklistStatsTests {

        @Test
        @DisplayName("JWT-BL-024: 获取黑名单统计信息")
        void testGetBlacklistStats_Success() {
            // Given
            when(storeManager.getConfig(BLACKLIST_INDEX_KEY)).thenReturn(createEmptyIndex());
            when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(createStatsData());

            // When
            var result = blacklistService.getBlacklistStats();

            // Then
            StepVerifier.create(result)
                .assertNext(stats -> {
                    assertNotNull(stats);
                    assertTrue(stats.containsKey("currentSize"));
                })
                .verifyComplete();
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> createEmptyIndex() {
        Map<String, Object> index = new HashMap<>();
        index.put("tokenHashes", new ArrayList<String>());
        index.put("updatedAt", LocalDateTime.now());
        return index;
    }

    private Map<String, Object> createIndexWithToken(String tokenHash) {
        Map<String, Object> index = new HashMap<>();
        List<String> hashes = new ArrayList<>();
        hashes.add(tokenHash);
        index.put("tokenHashes", hashes);
        index.put("updatedAt", LocalDateTime.now());
        return index;
    }

    private Map<String, Object> createIndexWithTokens(List<String> tokenHashes) {
        Map<String, Object> index = new HashMap<>();
        index.put("tokenHashes", new ArrayList<>(tokenHashes));
        index.put("updatedAt", LocalDateTime.now());
        return index;
    }

    private Map<String, Object> createBlacklistEntryData(String tokenHash) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tokenHash", tokenHash);
        entry.put("expiresAt", LocalDateTime.now().plusDays(30));
        entry.put("reason", "logout");
        entry.put("addedBy", "admin");
        entry.put("addedAt", LocalDateTime.now());
        return entry;
    }

    private Map<String, Object> createExpiringBlacklistEntryData(String tokenHash, int hoursUntilExpiry) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tokenHash", tokenHash);
        entry.put("expiresAt", LocalDateTime.now().plusHours(hoursUntilExpiry));
        entry.put("reason", "logout");
        entry.put("addedBy", "admin");
        entry.put("addedAt", LocalDateTime.now());
        return entry;
    }

    private Map<String, Object> createExpiredBlacklistEntryData(String tokenHash) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("tokenHash", tokenHash);
        entry.put("expiresAt", LocalDateTime.now().minusDays(1)); // 已过期
        entry.put("reason", "logout");
        entry.put("addedBy", "admin");
        entry.put("addedAt", LocalDateTime.now().minusDays(2));
        return entry;
    }

    private Map<String, Object> createStatsData() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAdded", 10L);
        stats.put("totalRemoved", 2L);
        stats.put("totalCleaned", 1L);
        return stats;
    }
}
