package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.store.MemoryStoreManager;
import org.unreal.modelrouter.store.StoreManager;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT令牌持久化服务实现测试
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenPersistenceServiceImplTest {
    
    private JwtTokenPersistenceServiceImpl persistenceService;
    private StoreManager storeManager;
    
    @BeforeEach
    void setUp() {
        storeManager = new MemoryStoreManager();
        persistenceService = new JwtTokenPersistenceServiceImpl(storeManager);
    }
    
    @Test
    void testSaveToken() {
        // 准备测试数据
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        
        // 执行保存操作
        StepVerifier.create(persistenceService.saveToken(tokenInfo))
            .verifyComplete();
        
        // 验证令牌已保存
        StepVerifier.create(persistenceService.findByTokenHash(tokenInfo.getTokenHash()))
            .assertNext(savedToken -> {
                assertNotNull(savedToken);
                assertEquals(tokenInfo.getUserId(), savedToken.getUserId());
                assertEquals(tokenInfo.getTokenHash(), savedToken.getTokenHash());
                assertEquals(TokenStatus.ACTIVE, savedToken.getStatus());
                assertNotNull(savedToken.getCreatedAt());
                assertNotNull(savedToken.getUpdatedAt());
            })
            .verifyComplete();
    }
    
    @Test
    void testFindByTokenHash() {
        // 准备测试数据
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        
        // 保存令牌
        persistenceService.saveToken(tokenInfo).block();
        
        // 查找令牌
        StepVerifier.create(persistenceService.findByTokenHash(tokenInfo.getTokenHash()))
            .assertNext(foundToken -> {
                assertNotNull(foundToken);
                assertEquals(tokenInfo.getUserId(), foundToken.getUserId());
                assertEquals(tokenInfo.getTokenHash(), foundToken.getTokenHash());
            })
            .verifyComplete();
        
        // 查找不存在的令牌
        StepVerifier.create(persistenceService.findByTokenHash("nonexistent"))
            .verifyComplete();
    }
    
    @Test
    void testFindActiveTokensByUserId() {
        // 准备测试数据
        String userId = "testUser";
        JwtTokenInfo activeToken = createTestTokenInfo();
        activeToken.setUserId(userId);
        activeToken.setStatus(TokenStatus.ACTIVE);
        
        JwtTokenInfo revokedToken = createTestTokenInfo();
        revokedToken.setUserId(userId);
        revokedToken.setTokenHash("revoked_hash");
        revokedToken.setStatus(TokenStatus.REVOKED);
        
        // 保存令牌
        persistenceService.saveToken(activeToken).block();
        persistenceService.saveToken(revokedToken).block();
        
        // 查找活跃令牌
        StepVerifier.create(persistenceService.findActiveTokensByUserId(userId))
            .assertNext(tokens -> {
                assertEquals(1, tokens.size());
                assertEquals(activeToken.getTokenHash(), tokens.get(0).getTokenHash());
                assertEquals(TokenStatus.ACTIVE, tokens.get(0).getStatus());
            })
            .verifyComplete();
    }
    
    @Test
    void testUpdateTokenStatus() {
        // 准备测试数据
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        
        // 保存令牌
        persistenceService.saveToken(tokenInfo).block();
        
        // 更新状态为撤销
        StepVerifier.create(persistenceService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .verifyComplete();
        
        // 验证状态已更新
        StepVerifier.create(persistenceService.findByTokenHash(tokenInfo.getTokenHash()))
            .assertNext(updatedToken -> {
                assertEquals(TokenStatus.REVOKED, updatedToken.getStatus());
                assertNotNull(updatedToken.getRevokedAt());
            })
            .verifyComplete();
    }
    
    @Test
    void testCountActiveTokens() {
        // 准备测试数据
        JwtTokenInfo activeToken1 = createTestTokenInfo();
        activeToken1.setTokenHash("active1");
        activeToken1.setStatus(TokenStatus.ACTIVE);
        
        JwtTokenInfo activeToken2 = createTestTokenInfo();
        activeToken2.setTokenHash("active2");
        activeToken2.setStatus(TokenStatus.ACTIVE);
        
        JwtTokenInfo revokedToken = createTestTokenInfo();
        revokedToken.setTokenHash("revoked");
        revokedToken.setStatus(TokenStatus.REVOKED);
        
        // 保存令牌
        persistenceService.saveToken(activeToken1).block();
        persistenceService.saveToken(activeToken2).block();
        persistenceService.saveToken(revokedToken).block();
        
        // 统计活跃令牌数量
        StepVerifier.create(persistenceService.countActiveTokens())
            .assertNext(count -> assertEquals(2L, count))
            .verifyComplete();
    }
    
    @Test
    void testBatchUpdateTokenStatus() {
        // 准备测试数据
        JwtTokenInfo token1 = createTestTokenInfo();
        token1.setTokenHash("batch1");
        
        JwtTokenInfo token2 = createTestTokenInfo();
        token2.setTokenHash("batch2");
        
        // 保存令牌
        persistenceService.saveToken(token1).block();
        persistenceService.saveToken(token2).block();
        
        // 批量更新状态
        List<String> tokenHashes = List.of("batch1", "batch2");
        StepVerifier.create(persistenceService.batchUpdateTokenStatus(
            tokenHashes, TokenStatus.REVOKED, "Batch revocation", "admin"))
            .verifyComplete();
        
        // 验证状态已更新
        StepVerifier.create(persistenceService.findByTokenHash("batch1"))
            .assertNext(token -> {
                assertEquals(TokenStatus.REVOKED, token.getStatus());
                assertEquals("Batch revocation", token.getRevokeReason());
                assertEquals("admin", token.getRevokedBy());
            })
            .verifyComplete();
        
        StepVerifier.create(persistenceService.findByTokenHash("batch2"))
            .assertNext(token -> {
                assertEquals(TokenStatus.REVOKED, token.getStatus());
                assertEquals("Batch revocation", token.getRevokeReason());
                assertEquals("admin", token.getRevokedBy());
            })
            .verifyComplete();
    }
    
    @Test
    void testRemoveExpiredTokens() {
        // 准备测试数据 - 过期令牌
        JwtTokenInfo expiredToken = createTestTokenInfo();
        expiredToken.setTokenHash("expired");
        expiredToken.setExpiresAt(LocalDateTime.now().minusHours(1)); // 1小时前过期
        
        // 准备测试数据 - 未过期令牌
        JwtTokenInfo validToken = createTestTokenInfo();
        validToken.setTokenHash("valid");
        validToken.setExpiresAt(LocalDateTime.now().plusHours(1)); // 1小时后过期
        
        // 保存令牌
        persistenceService.saveToken(expiredToken).block();
        persistenceService.saveToken(validToken).block();
        
        // 清理过期令牌
        StepVerifier.create(persistenceService.removeExpiredTokens())
            .verifyComplete();
        
        // 验证过期令牌已删除
        StepVerifier.create(persistenceService.findByTokenHash("expired"))
            .verifyComplete();
        
        // 验证有效令牌仍存在
        StepVerifier.create(persistenceService.findByTokenHash("valid"))
            .assertNext(token -> assertNotNull(token))
            .verifyComplete();
    }
    
    private JwtTokenInfo createTestTokenInfo() {
        JwtTokenInfo tokenInfo = new JwtTokenInfo();
        tokenInfo.setId(UUID.randomUUID().toString());
        tokenInfo.setUserId("testUser");
        tokenInfo.setTokenHash("test_token_hash");
        tokenInfo.setToken("test.jwt.token");
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setIssuedAt(LocalDateTime.now());
        tokenInfo.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenInfo.setStatus(TokenStatus.ACTIVE);
        tokenInfo.setIpAddress("127.0.0.1");
        tokenInfo.setUserAgent("Test Agent");
        return tokenInfo;
    }
}