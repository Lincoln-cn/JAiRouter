package org.unreal.modelrouter.auth.security.service;

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
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.TokenStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JwtTokenManagementService 单元测试
 *
 * <p>测试JWT令牌管理功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("JwtTokenManagementService 令牌管理服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenManagementServiceTest {

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @InjectMocks
    private JwtTokenManagementService tokenManagementService;

    private String sampleToken;
    private String sampleTokenHash;
    private JwtTokenInfo sampleTokenInfo;

    @BeforeEach
    void setUp() {
        sampleToken = "eyJhbGciOiJIUzUxMiJ9.test.signature";
        sampleTokenHash = tokenManagementService.calculateTokenHash(sampleToken);

        sampleTokenInfo = new JwtTokenInfo();
        sampleTokenInfo.setTokenHash(sampleTokenHash);
        sampleTokenInfo.setUserId("user-123");
        sampleTokenInfo.setStatus(TokenStatus.ACTIVE);
        sampleTokenInfo.setCreatedAt(LocalDateTime.now());
        sampleTokenInfo.setExpiresAt(LocalDateTime.now().plusHours(1));
    }

    // ==================== 令牌哈希计算测试 ====================

    @Nested
    @DisplayName("令牌哈希计算测试")
    class TokenHashTests {

        @Test
        @DisplayName("JWT-001: 计算令牌哈希值")
        void testCalculateTokenHash() {
            // When
            String hash = tokenManagementService.calculateTokenHash(sampleToken);

            // Then
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
            // 相同输入应产生相同哈希
            assertEquals(hash, tokenManagementService.calculateTokenHash(sampleToken));
        }

        @Test
        @DisplayName("JWT-002: 不同令牌产生不同哈希")
        void testDifferentTokensProduceDifferentHashes() {
            // Given
            String token1 = "token1";
            String token2 = "token2";

            // When
            String hash1 = tokenManagementService.calculateTokenHash(token1);
            String hash2 = tokenManagementService.calculateTokenHash(token2);

            // Then
            assertNotEquals(hash1, hash2);
        }
    }

    // ==================== 令牌状态更新测试 ====================

    @Nested
    @DisplayName("令牌状态更新测试")
    class UpdateTokenStatusTests {

        @Test
        @DisplayName("JWT-003: 更新令牌状态成功")
        void testUpdateTokenStatus_Success() {
            // Given
            when(jwtPersistenceService.findByTokenHash(anyString())).thenReturn(Mono.just(sampleTokenInfo));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.updateTokenStatus(
                sampleToken, TokenStatus.REVOKED, "测试撤销", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(jwtPersistenceService).findByTokenHash(anyString());
            verify(jwtPersistenceService).saveToken(any());
        }

        @Test
        @DisplayName("JWT-004: 持久化服务不可用时静默返回")
        void testUpdateTokenStatus_PersistenceServiceUnavailable() {
            // Given - 创建没有持久化服务的实例
            JwtTokenManagementService serviceWithoutPersistence = new JwtTokenManagementService();

            // When
            Mono<Void> result = serviceWithoutPersistence.updateTokenStatus(
                sampleToken, TokenStatus.REVOKED, "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-005: 令牌不存在时静默返回")
        void testUpdateTokenStatus_TokenNotFound() {
            // Given
            when(jwtPersistenceService.findByTokenHash(anyString())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.updateTokenStatus(
                sampleToken, TokenStatus.REVOKED, "测试撤销", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-006: 更新失败时静默返回")
        void testUpdateTokenStatus_UpdateFailed() {
            // Given
            when(jwtPersistenceService.findByTokenHash(anyString())).thenReturn(Mono.just(sampleTokenInfo));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.error(new RuntimeException("保存失败")));

            // When
            Mono<Void> result = tokenManagementService.updateTokenStatus(
                sampleToken, TokenStatus.REVOKED, "测试撤销", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 批量状态更新测试 ====================

    @Nested
    @DisplayName("批量状态更新测试")
    class BatchUpdateStatusTests {

        @Test
        @DisplayName("JWT-007: 批量更新令牌状态成功")
        void testBatchUpdateTokenStatus_Success() {
            // Given
            List<String> tokens = Arrays.asList("token1", "token2", "token3");
            when(jwtPersistenceService.batchUpdateTokenStatus(anyList(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.batchUpdateTokenStatus(
                tokens, TokenStatus.REVOKED, "批量撤销", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(jwtPersistenceService).batchUpdateTokenStatus(anyList(), eq(TokenStatus.REVOKED), eq("批量撤销"), eq("admin"));
        }

        @Test
        @DisplayName("JWT-008: 空令牌列表静默返回")
        void testBatchUpdateTokenStatus_EmptyList() {
            // When
            Mono<Void> result = tokenManagementService.batchUpdateTokenStatus(
                Collections.emptyList(), TokenStatus.REVOKED, "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(jwtPersistenceService, never()).batchUpdateTokenStatus(anyList(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("JWT-009: null令牌列表静默返回")
        void testBatchUpdateTokenStatus_NullList() {
            // When
            Mono<Void> result = tokenManagementService.batchUpdateTokenStatus(
                null, TokenStatus.REVOKED, "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 令牌撤销测试 ====================

    @Nested
    @DisplayName("令牌撤销测试")
    class RevokeTokenTests {

        @Test
        @DisplayName("JWT-010: 通过哈希撤销令牌成功")
        void testRevokeTokenByHash_Success() {
            // Given
            when(jwtPersistenceService.findByTokenHash(sampleTokenHash)).thenReturn(Mono.just(sampleTokenInfo));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());
            when(jwtBlacklistService.addToBlacklist(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.revokeTokenByHash(
                sampleTokenHash, "安全原因", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(jwtPersistenceService).findByTokenHash(sampleTokenHash);
            verify(jwtPersistenceService).saveToken(any());
            verify(jwtBlacklistService).addToBlacklist(sampleTokenHash, "安全原因", "admin");
        }

        @Test
        @DisplayName("JWT-011: 撤销不存在的令牌返回错误")
        void testRevokeTokenByHash_TokenNotFound() {
            // Given
            when(jwtPersistenceService.findByTokenHash(anyString())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.revokeTokenByHash(
                "non-existent-hash", "测试", "admin");

            // Then
            StepVerifier.create(result)
                .expectErrorMatches(ex -> ex.getMessage().contains("令牌不存在"))
                .verify();
        }

        @Test
        @DisplayName("JWT-012: 持久化服务不可用时返回错误")
        void testRevokeTokenByHash_PersistenceServiceUnavailable() {
            // Given - 创建没有持久化服务的实例
            JwtTokenManagementService serviceWithoutPersistence = new JwtTokenManagementService();

            // When
            Mono<Void> result = serviceWithoutPersistence.revokeTokenByHash(
                "some-hash", "测试", "admin");

            // Then
            StepVerifier.create(result)
                .expectErrorMatches(ex -> ex.getMessage().contains("令牌持久化服务未启用"))
                .verify();
        }

        @Test
        @DisplayName("JWT-013: 撤销时黑名单服务失败不影响结果")
        void testRevokeTokenByHash_BlacklistServiceFails() {
            // Given
            when(jwtPersistenceService.findByTokenHash(sampleTokenHash)).thenReturn(Mono.just(sampleTokenInfo));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());
            when(jwtBlacklistService.addToBlacklist(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("黑名单服务异常")));

            // When
            Mono<Void> result = tokenManagementService.revokeTokenByHash(
                sampleTokenHash, "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-014: 无黑名单服务时仍可撤销")
        void testRevokeTokenByHash_WithoutBlacklistService() {
            // Given
            JwtTokenManagementService serviceWithoutBlacklist = new JwtTokenManagementService();
            // 通过反射注入 persistence service
            try {
                java.lang.reflect.Field persistenceField = JwtTokenManagementService.class
                    .getDeclaredField("jwtPersistenceService");
                persistenceField.setAccessible(true);
                persistenceField.set(serviceWithoutBlacklist, jwtPersistenceService);
            } catch (Exception e) {
                // ignore
            }

            when(jwtPersistenceService.findByTokenHash(sampleTokenHash)).thenReturn(Mono.just(sampleTokenInfo));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = serviceWithoutBlacklist.revokeTokenByHash(
                sampleTokenHash, "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 批量撤销测试 ====================

    @Nested
    @DisplayName("批量撤销测试")
    class BatchRevokeTests {

        @Test
        @DisplayName("JWT-015: 批量撤销令牌成功")
        void testBatchRevokeTokensByHash_Success() {
            // Given
            List<String> tokenHashes = Arrays.asList("hash1", "hash2");
            JwtTokenInfo info1 = new JwtTokenInfo();
            info1.setTokenHash("hash1");
            info1.setStatus(TokenStatus.ACTIVE);

            JwtTokenInfo info2 = new JwtTokenInfo();
            info2.setTokenHash("hash2");
            info2.setStatus(TokenStatus.ACTIVE);

            when(jwtPersistenceService.findByTokenHash("hash1")).thenReturn(Mono.just(info1));
            when(jwtPersistenceService.findByTokenHash("hash2")).thenReturn(Mono.just(info2));
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());
            when(jwtBlacklistService.addToBlacklist(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.batchRevokeTokensByHash(
                tokenHashes, "批量撤销", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-016: 空列表静默返回")
        void testBatchRevokeTokensByHash_EmptyList() {
            // When
            Mono<Void> result = tokenManagementService.batchRevokeTokensByHash(
                Collections.emptyList(), "测试", "admin");

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

        @Test
        @DisplayName("JWT-017: 部分失败不影响其他令牌")
        void testBatchRevokeTokensByHash_PartialFailure() {
            // Given
            List<String> tokenHashes = Arrays.asList("hash1", "hash2");
            JwtTokenInfo info1 = new JwtTokenInfo();
            info1.setTokenHash("hash1");

            when(jwtPersistenceService.findByTokenHash("hash1")).thenReturn(Mono.just(info1));
            when(jwtPersistenceService.findByTokenHash("hash2")).thenReturn(Mono.empty()); // 不存在
            when(jwtPersistenceService.saveToken(any())).thenReturn(Mono.empty());
            when(jwtBlacklistService.addToBlacklist(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenManagementService.batchRevokeTokensByHash(
                tokenHashes, "批量撤销", "admin");

            // Then - 应该完成而不是报错
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 服务可用性检查测试 ====================

    @Nested
    @DisplayName("服务可用性检查测试")
    class ServiceAvailabilityTests {

        @Test
        @DisplayName("JWT-018: 检查持久化服务可用")
        void testIsPersistenceServiceAvailable_True() {
            // When
            boolean available = tokenManagementService.isPersistenceServiceAvailable();

            // Then
            assertTrue(available);
        }

        @Test
        @DisplayName("JWT-019: 检查持久化服务不可用")
        void testIsPersistenceServiceAvailable_False() {
            // Given
            JwtTokenManagementService serviceWithoutPersistence = new JwtTokenManagementService();

            // When
            boolean available = serviceWithoutPersistence.isPersistenceServiceAvailable();

            // Then
            assertFalse(available);
        }

        @Test
        @DisplayName("JWT-020: 检查黑名单服务可用")
        void testIsBlacklistServiceAvailable_True() {
            // When
            boolean available = tokenManagementService.isBlacklistServiceAvailable();

            // Then
            assertTrue(available);
        }

        @Test
        @DisplayName("JWT-021: 检查黑名单服务不可用")
        void testIsBlacklistServiceAvailable_False() {
            // Given
            JwtTokenManagementService serviceWithoutBlacklist = new JwtTokenManagementService();

            // When
            boolean available = serviceWithoutBlacklist.isBlacklistServiceAvailable();

            // Then
            assertFalse(available);
        }
    }
}
