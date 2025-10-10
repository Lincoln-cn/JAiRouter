package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeJwtPersistenceServiceImplTest {
    
    @Mock
    private JwtPersistenceService redisService;
    
    @Mock
    private JwtPersistenceService fallbackService;
    
    @Mock
    private StorageHealthService storageHealthService;
    
    private CompositeJwtPersistenceServiceImpl compositeService;
    
    @BeforeEach
    void setUp() {
        compositeService = new CompositeJwtPersistenceServiceImpl(
            redisService, fallbackService, storageHealthService);
    }
    
    @Test
    void saveToken_WhenRedisHealthy_ShouldSaveToBothStorages() {
        // Given
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        when(fallbackService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.saveToken(tokenInfo))
            .verifyComplete();
        
        verify(redisService).saveToken(tokenInfo);
        verify(fallbackService).saveToken(tokenInfo);
    }
    
    @Test
    void saveToken_WhenRedisUnhealthy_ShouldSaveToFallbackOnly() {
        // Given
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(false));
        when(fallbackService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.saveToken(tokenInfo))
            .verifyComplete();
        
        verify(fallbackService).saveToken(tokenInfo);
        verify(redisService, never()).saveToken(any());
    }
    
    @Test
    void saveToken_WhenRedisFails_ShouldFallbackToStoreManager() {
        // Given
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.saveToken(tokenInfo)).thenReturn(Mono.error(new RuntimeException("Redis error")));
        when(fallbackService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.saveToken(tokenInfo))
            .verifyComplete();
        
        verify(redisService).saveToken(tokenInfo);
        verify(fallbackService).saveToken(tokenInfo);
    }
    
    @Test
    void findByTokenHash_WhenRedisHealthy_ShouldQueryRedisFirst() {
        // Given
        String tokenHash = "test-hash";
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.findByTokenHash(tokenHash)).thenReturn(Mono.just(tokenInfo));
        
        // When & Then
        StepVerifier.create(compositeService.findByTokenHash(tokenHash))
            .expectNext(tokenInfo)
            .verifyComplete();
        
        verify(redisService).findByTokenHash(tokenHash);
        verify(fallbackService, never()).findByTokenHash(any());
    }
    
    @Test
    void findByTokenHash_WhenRedisHealthyButEmpty_ShouldQueryFallback() {
        // Given
        String tokenHash = "test-hash";
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.findByTokenHash(tokenHash)).thenReturn(Mono.empty());
        when(fallbackService.findByTokenHash(tokenHash)).thenReturn(Mono.just(tokenInfo));
        
        // When & Then
        StepVerifier.create(compositeService.findByTokenHash(tokenHash))
            .expectNext(tokenInfo)
            .verifyComplete();
        
        verify(redisService).findByTokenHash(tokenHash);
        verify(fallbackService).findByTokenHash(tokenHash);
    }
    
    @Test
    void findByTokenHash_WhenRedisUnhealthy_ShouldQueryFallbackOnly() {
        // Given
        String tokenHash = "test-hash";
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(false));
        when(fallbackService.findByTokenHash(tokenHash)).thenReturn(Mono.just(tokenInfo));
        
        // When & Then
        StepVerifier.create(compositeService.findByTokenHash(tokenHash))
            .expectNext(tokenInfo)
            .verifyComplete();
        
        verify(fallbackService).findByTokenHash(tokenHash);
        verify(redisService, never()).findByTokenHash(any());
    }
    
    @Test
    void findActiveTokensByUserId_WhenRedisHealthy_ShouldQueryRedisFirst() {
        // Given
        String userId = "test-user";
        List<JwtTokenInfo> tokens = Arrays.asList(createTestTokenInfo());
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.findActiveTokensByUserId(userId)).thenReturn(Mono.just(tokens));
        
        // When & Then
        StepVerifier.create(compositeService.findActiveTokensByUserId(userId))
            .expectNext(tokens)
            .verifyComplete();
        
        verify(redisService).findActiveTokensByUserId(userId);
        verify(fallbackService, never()).findActiveTokensByUserId(any());
    }
    
    @Test
    void findActiveTokensByUserId_WhenRedisHealthyButEmpty_ShouldQueryFallback() {
        // Given
        String userId = "test-user";
        List<JwtTokenInfo> emptyList = Arrays.asList();
        List<JwtTokenInfo> tokens = Arrays.asList(createTestTokenInfo());
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.findActiveTokensByUserId(userId)).thenReturn(Mono.just(emptyList));
        when(fallbackService.findActiveTokensByUserId(userId)).thenReturn(Mono.just(tokens));
        
        // When & Then
        StepVerifier.create(compositeService.findActiveTokensByUserId(userId))
            .expectNext(tokens)
            .verifyComplete();
        
        verify(redisService).findActiveTokensByUserId(userId);
        verify(fallbackService).findActiveTokensByUserId(userId);
    }
    
    @Test
    void updateTokenStatus_WhenRedisHealthy_ShouldUpdateBothStorages() {
        // Given
        String tokenHash = "test-hash";
        TokenStatus status = TokenStatus.REVOKED;
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.updateTokenStatus(tokenHash, status)).thenReturn(Mono.empty());
        when(fallbackService.updateTokenStatus(tokenHash, status)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.updateTokenStatus(tokenHash, status))
            .verifyComplete();
        
        verify(redisService).updateTokenStatus(tokenHash, status);
        verify(fallbackService).updateTokenStatus(tokenHash, status);
    }
    
    @Test
    void updateTokenStatus_WhenRedisUnhealthy_ShouldUpdateFallbackOnly() {
        // Given
        String tokenHash = "test-hash";
        TokenStatus status = TokenStatus.REVOKED;
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(false));
        when(fallbackService.updateTokenStatus(tokenHash, status)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.updateTokenStatus(tokenHash, status))
            .verifyComplete();
        
        verify(fallbackService).updateTokenStatus(tokenHash, status);
        verify(redisService, never()).updateTokenStatus(any(), any());
    }
    
    @Test
    void updateTokenStatus_WhenRedisFails_ShouldFallbackToStoreManager() {
        // Given
        String tokenHash = "test-hash";
        TokenStatus status = TokenStatus.REVOKED;
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.updateTokenStatus(tokenHash, status))
            .thenReturn(Mono.error(new RuntimeException("Redis error")));
        when(fallbackService.updateTokenStatus(tokenHash, status)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.updateTokenStatus(tokenHash, status))
            .verifyComplete();
        
        verify(redisService).updateTokenStatus(tokenHash, status);
        verify(fallbackService).updateTokenStatus(tokenHash, status);
    }
    
    @Test
    void countActiveTokens_WhenRedisHealthy_ShouldCountFromRedis() {
        // Given
        Long count = 5L;
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.countActiveTokens()).thenReturn(Mono.just(count));
        
        // When & Then
        StepVerifier.create(compositeService.countActiveTokens())
            .expectNext(count)
            .verifyComplete();
        
        verify(redisService).countActiveTokens();
        verify(fallbackService, never()).countActiveTokens();
    }
    
    @Test
    void countActiveTokens_WhenRedisHealthyButZero_ShouldCountFromFallback() {
        // Given
        Long redisCount = 0L;
        Long fallbackCount = 3L;
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.countActiveTokens()).thenReturn(Mono.just(redisCount));
        when(fallbackService.countActiveTokens()).thenReturn(Mono.just(fallbackCount));
        
        // When & Then
        StepVerifier.create(compositeService.countActiveTokens())
            .expectNext(fallbackCount)
            .verifyComplete();
        
        verify(redisService).countActiveTokens();
        verify(fallbackService).countActiveTokens();
    }
    
    @Test
    void batchUpdateTokenStatus_WhenRedisHealthy_ShouldUpdateBothStorages() {
        // Given
        List<String> tokenHashes = Arrays.asList("hash1", "hash2");
        TokenStatus status = TokenStatus.REVOKED;
        String reason = "test reason";
        String updatedBy = "admin";
        
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(true));
        when(redisService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .thenReturn(Mono.empty());
        when(fallbackService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .verifyComplete();
        
        verify(redisService).batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy);
        verify(fallbackService).batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy);
    }
    
    @Test
    void batchUpdateTokenStatus_WhenRedisUnhealthy_ShouldUpdateFallbackOnly() {
        // Given
        List<String> tokenHashes = Arrays.asList("hash1", "hash2");
        TokenStatus status = TokenStatus.REVOKED;
        String reason = "test reason";
        String updatedBy = "admin";
        
        when(storageHealthService.isRedisHealthy()).thenReturn(Mono.just(false));
        when(fallbackService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(compositeService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy))
            .verifyComplete();
        
        verify(fallbackService).batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy);
        verify(redisService, never()).batchUpdateTokenStatus(any(), any(), any(), any());
    }
    
    private JwtTokenInfo createTestTokenInfo() {
        JwtTokenInfo tokenInfo = new JwtTokenInfo();
        tokenInfo.setId("test-id");
        tokenInfo.setUserId("test-user");
        tokenInfo.setTokenHash("test-hash");
        tokenInfo.setStatus(TokenStatus.ACTIVE);
        tokenInfo.setIssuedAt(LocalDateTime.now());
        tokenInfo.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenInfo.setCreatedAt(LocalDateTime.now());
        tokenInfo.setUpdatedAt(LocalDateTime.now());
        return tokenInfo;
    }
}