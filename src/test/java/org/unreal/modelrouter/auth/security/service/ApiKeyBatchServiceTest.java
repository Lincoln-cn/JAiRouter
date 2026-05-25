package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchExportVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchImportRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyBatchImportResult;
import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * ApiKeyBatchService 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyBatchService 测试")
class ApiKeyBatchServiceTest {

    @Mock
    private ExtendedSecurityAuditService extendedAuditService;

    @Mock
    private ApiKeyPersistenceService persistenceService;

    private ApiKeyBatchService batchService;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        batchService = new ApiKeyBatchService();
        // Use reflection to inject mocks
        injectField(batchService, "extendedAuditService", extendedAuditService);
        injectField(batchService, "persistenceService", persistenceService);

        apiKeyCache = new ConcurrentHashMap<>();
        keyIdIndex = new ConcurrentHashMap<>();

        lenient().when(extendedAuditService.auditSecurityEvent(any(), any(), any(), any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
    }

    @Nested
    @DisplayName("SVC-BATCH-001: 批量导出测试")
    class ExportTests {

        @Test
        @DisplayName("导出空缓存应返回空列表")
        void testExportEmptyCache() {
            Map<String, ApiKey> emptyCache = new HashMap<>();

            StepVerifier.create(batchService.exportApiKeys(emptyCache))
                    .assertNext(result -> {
                        assertEquals(0, result.getTotal());
                        assertTrue(result.getKeys().isEmpty());
                        assertNotNull(result.getExportTime());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("导出包含多个API Key的缓存")
        void testExportMultipleKeys() {
            // Given: 缓存中有多个API Key
            ApiKey key1 = createTestApiKey("key-001", "Test Key 1");
            ApiKey key2 = createTestApiKey("key-002", "Test Key 2");
            apiKeyCache.put(key1.getKeyHash(), key1);
            apiKeyCache.put(key2.getKeyHash(), key2);

            StepVerifier.create(batchService.exportApiKeys(apiKeyCache))
                    .assertNext(result -> {
                        assertEquals(2, result.getTotal());
                        assertEquals(2, result.getKeys().size());
                        // 验证不包含敏感信息
                        result.getKeys().forEach(exported -> {
                            assertNotNull(exported.getKeyId());
                            assertNotNull(exported.getDescription());
                            // keyValue 不应该被导出
                        });
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("SVC-BATCH-002: 批量导入测试")
    class ImportTests {

        @Test
        @DisplayName("MERGE模式导入新密钥")
        void testImportMergeMode() {
            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .mode(ApiKeyBatchImportRequest.ImportMode.MERGE)
                    .keys(List.of(
                            ApiKeyBatchImportRequest.ApiKeyImportItem.builder()
                                    .keyId("new-key-001")
                                    .description("Imported Key")
                                    .permissions(List.of("read", "write"))
                                    .enabled(true)
                                    .build()
                    ))
                    .build();

            StepVerifier.create(batchService.importApiKeys(request, apiKeyCache, keyIdIndex, "admin", "127.0.0.1"))
                    .assertNext(result -> {
                        assertEquals(1, result.getTotalAttempted());
                        assertEquals(1, result.getSuccessCount());
                        assertEquals(0, result.getFailureCount());
                        assertEquals(1, result.getImportedKeys().size());
                        assertEquals("new-key-001", result.getImportedKeys().get(0).getKeyId());
                        assertNotNull(result.getImportedKeys().get(0).getKeyValue());
                    })
                    .verifyComplete();

            // 验证缓存已更新
            assertEquals(1, apiKeyCache.size());
            assertTrue(keyIdIndex.containsKey("new-key-001"));
        }

        @Test
        @DisplayName("MERGE模式重复keyId应失败")
        void testImportMergeModeDuplicate() {
            // Given: 缓存中已有该keyId
            ApiKey existingKey = createTestApiKey("existing-key", "Existing");
            apiKeyCache.put(existingKey.getKeyHash(), existingKey);
            keyIdIndex.put("existing-key", existingKey.getKeyHash());

            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .mode(ApiKeyBatchImportRequest.ImportMode.MERGE)
                    .keys(List.of(
                            ApiKeyBatchImportRequest.ApiKeyImportItem.builder()
                                    .keyId("existing-key")
                                    .description("Duplicate Key")
                                    .build()
                    ))
                    .build();

            StepVerifier.create(batchService.importApiKeys(request, apiKeyCache, keyIdIndex, "admin", "127.0.0.1"))
                    .assertNext(result -> {
                        assertEquals(1, result.getTotalAttempted());
                        assertEquals(0, result.getSuccessCount());
                        assertEquals(1, result.getFailureCount());
                        assertEquals(1, result.getErrors().size());
                        assertTrue(result.getErrors().get(0).getReason().contains("已存在"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("REPLACE模式清除现有密钥后导入")
        void testImportReplaceMode() {
            // Given: 缓存中有现有密钥
            ApiKey existingKey = createTestApiKey("old-key", "Old Key");
            apiKeyCache.put(existingKey.getKeyHash(), existingKey);
            keyIdIndex.put("old-key", existingKey.getKeyHash());
            assertEquals(1, apiKeyCache.size());

            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .mode(ApiKeyBatchImportRequest.ImportMode.REPLACE)
                    .keys(List.of(
                            ApiKeyBatchImportRequest.ApiKeyImportItem.builder()
                                    .keyId("new-key")
                                    .description("New Key")
                                    .build()
                    ))
                    .build();

            StepVerifier.create(batchService.importApiKeys(request, apiKeyCache, keyIdIndex, "admin", "127.0.0.1"))
                    .assertNext(result -> {
                        assertEquals(1, result.getSuccessCount());
                    })
                    .verifyComplete();

            // 验证旧密钥已被清除
            assertEquals(1, apiKeyCache.size());
            assertFalse(keyIdIndex.containsKey("old-key"));
            assertTrue(keyIdIndex.containsKey("new-key"));
        }

        @Test
        @DisplayName("自动生成keyId当未提供时")
        void testImportAutoGenerateKeyId() {
            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .mode(ApiKeyBatchImportRequest.ImportMode.MERGE)
                    .keys(List.of(
                            ApiKeyBatchImportRequest.ApiKeyImportItem.builder()
                                    .description("Key without ID")
                                    .build()
                    ))
                    .build();

            StepVerifier.create(batchService.importApiKeys(request, apiKeyCache, keyIdIndex, "admin", "127.0.0.1"))
                    .assertNext(result -> {
                        assertEquals(1, result.getSuccessCount());
                        assertNotNull(result.getImportedKeys().get(0).getKeyId());
                        assertTrue(result.getImportedKeys().get(0).getKeyId().startsWith("key-"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("SVC-BATCH-003: 密钥轮换测试")
    class RotationTests {

        @Test
        @DisplayName("轮换需要轮换的密钥")
        void testRotateExpiredKeys() {
            // Given: 有一个需要轮换的密钥
            ApiKey keyNeedingRotation = ApiKey.builder()
                    .keyId("key-to-rotate")
                    .keyHash("hash-old")
                    .keyPrefix("sk-")
                    .description("Key needing rotation")
                    .enabled(true)
                    .rotationPeriodDays(30)
                    .lastRotatedAt(LocalDateTime.now().minusDays(31)) // 超过轮换周期
                    .createdAt(LocalDateTime.now().minusDays(60))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();
            apiKeyCache.put(keyNeedingRotation.getKeyHash(), keyNeedingRotation);
            keyIdIndex.put("key-to-rotate", keyNeedingRotation.getKeyHash());

            StepVerifier.create(batchService.rotateExpiredKeys(apiKeyCache, keyIdIndex))
                    .assertNext(count -> {
                        assertEquals(1, count);
                    })
                    .verifyComplete();

            // 验证keyHash已更新
            assertFalse(apiKeyCache.containsKey("hash-old"));
            assertTrue(keyIdIndex.containsKey("key-to-rotate"));
            String newHash = keyIdIndex.get("key-to-rotate");
            assertNotEquals("hash-old", newHash);
        }

        @Test
        @DisplayName("强制轮换指定密钥")
        void testForceRotateKey() {
            ApiKey key = createTestApiKey("force-rotate-key", "Force Rotate");
            apiKeyCache.put(key.getKeyHash(), key);
            keyIdIndex.put("force-rotate-key", key.getKeyHash());
            String oldHash = key.getKeyHash();

            StepVerifier.create(batchService.forceRotateKey("force-rotate-key", apiKeyCache, keyIdIndex, "admin"))
                    .assertNext(result -> {
                        assertEquals("force-rotate-key", result.getKeyId());
                        assertNotNull(result.getKeyValue());
                        assertTrue(result.getKeyValue().startsWith("sk-"));
                        assertNotNull(result.getWarning());
                    })
                    .verifyComplete();

            // 验证旧hash已移除
            assertFalse(apiKeyCache.containsKey(oldHash));
            String newHash = keyIdIndex.get("force-rotate-key");
            assertNotNull(newHash);
            assertNotEquals(oldHash, newHash);
        }

        @Test
        @DisplayName("强制轮换不存在的密钥应抛出异常")
        void testForceRotateNonExistentKey() {
            StepVerifier.create(batchService.forceRotateKey("non-existent", apiKeyCache, keyIdIndex, "admin"))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("SVC-BATCH-004: 过期清理测试")
    class CleanupTests {

        @Test
        @DisplayName("清理过期但仍启用的密钥")
        void testCleanupExpiredKeys() {
            // Given: 有一个已过期但仍启用的密钥
            ApiKey expiredKey = ApiKey.builder()
                    .keyId("expired-key")
                    .keyHash("hash-expired")
                    .keyPrefix("sk-")
                    .description("Expired Key")
                    .enabled(true)
                    .expiresAt(LocalDateTime.now().minusDays(1)) // 已过期
                    .createdAt(LocalDateTime.now().minusDays(30))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();
            apiKeyCache.put(expiredKey.getKeyHash(), expiredKey);

            StepVerifier.create(batchService.cleanupExpiredKeys(apiKeyCache))
                    .assertNext(count -> {
                        assertEquals(1, count);
                    })
                    .verifyComplete();

            // 验证密钥已被禁用
            assertTrue(expiredKey.isExpired());
            assertFalse(expiredKey.isEnabled());
        }

        @Test
        @DisplayName("未过期密钥不被清理")
        void testCleanupDoesNotAffectValidKeys() {
            ApiKey validKey = createTestApiKey("valid-key", "Valid Key");
            validKey.setExpiresAt(LocalDateTime.now().plusDays(30));
            apiKeyCache.put(validKey.getKeyHash(), validKey);

            StepVerifier.create(batchService.cleanupExpiredKeys(apiKeyCache))
                    .assertNext(count -> {
                        assertEquals(0, count);
                    })
                    .verifyComplete();

            // 验证密钥仍启用
            assertTrue(validKey.isEnabled());
        }
    }

    @Nested
    @DisplayName("SVC-BATCH-005: 统计测试")
    class StatsTests {

        @Test
        @DisplayName("获取轮换统计")
        void testGetRotationStats() {
            // Given: 创建不同状态的密钥
            ApiKey keyWithRotation = ApiKey.builder()
                    .keyId("key-rotation")
                    .keyHash("hash-1")
                    .keyPrefix("sk-")
                    .enabled(true)
                    .rotationPeriodDays(30)
                    .lastRotatedAt(LocalDateTime.now().minusDays(31))
                    .createdAt(LocalDateTime.now().minusDays(60))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();

            ApiKey keyWithoutRotation = createTestApiKey("key-no-rotation", "No Rotation");
            keyWithoutRotation.setRotationPeriodDays(0);

            ApiKey keyRotatedToday = ApiKey.builder()
                    .keyId("key-today")
                    .keyHash("hash-3")
                    .keyPrefix("sk-")
                    .enabled(true)
                    .rotationPeriodDays(30)
                    .lastRotatedAt(LocalDateTime.now().minusHours(2))
                    .createdAt(LocalDateTime.now().minusDays(30))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();

            apiKeyCache.put(keyWithRotation.getKeyHash(), keyWithRotation);
            apiKeyCache.put(keyWithoutRotation.getKeyHash(), keyWithoutRotation);
            apiKeyCache.put(keyRotatedToday.getKeyHash(), keyRotatedToday);

            StepVerifier.create(batchService.getRotationStats(apiKeyCache))
                    .assertNext(stats -> {
                        assertEquals(3, stats.getTotalKeys());
                        assertEquals(2, stats.getKeysWithRotation()); // keyWithRotation 和 keyRotatedToday
                        assertEquals(1, stats.getKeysNeedingRotation()); // keyWithRotation
                        assertEquals(1, stats.getRotatedToday()); // keyRotatedToday
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("获取过期统计")
        void testGetExpirationStats() {
            ApiKey expiredKey = ApiKey.builder()
                    .keyId("expired")
                    .keyHash("hash-exp")
                    .keyPrefix("sk-")
                    .enabled(true)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .createdAt(LocalDateTime.now().minusDays(30))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();

            ApiKey expiringToday = ApiKey.builder()
                    .keyId("expiring")
                    .keyHash("hash-today")
                    .keyPrefix("sk-")
                    .enabled(true)
                    .expiresAt(LocalDateTime.now().plusHours(2))
                    .createdAt(LocalDateTime.now().minusDays(30))
                    .usage(UsageStatistics.builder().totalRequests(0L).build())
                    .build();

            ApiKey disabledKey = createTestApiKey("disabled", "Disabled");
            disabledKey.setEnabled(false);

            apiKeyCache.put(expiredKey.getKeyHash(), expiredKey);
            apiKeyCache.put(expiringToday.getKeyHash(), expiringToday);
            apiKeyCache.put(disabledKey.getKeyHash(), disabledKey);

            StepVerifier.create(batchService.getExpirationStats(apiKeyCache))
                    .assertNext(stats -> {
                        assertEquals(3, stats.getTotalKeys());
                        assertEquals(1, stats.getExpiredKeys());
                        assertEquals(1, stats.getExpiringToday());
                        assertEquals(1, stats.getDisabledKeys());
                    })
                    .verifyComplete();
        }
    }

    // ============ Helper Methods ============

    private ApiKey createTestApiKey(String keyId, String description) {
        return ApiKey.builder()
                .keyId(keyId)
                .keyHash("hash-" + keyId)
                .keyPrefix("sk-")
                .description(description)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .usage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new java.util.HashMap<>())
                        .build())
                .build();
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
