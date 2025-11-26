package org.unreal.modelrouter.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.DataSyncService;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import org.unreal.modelrouter.security.service.impl.*;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 复合存储策略集成测试
 * 测试Redis缓存和StoreManager持久化存储的协同工作
 */
@ExtendWith(MockitoExtension.class)
class CompositeStorageIntegrationTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private StoreManager storeManager;
    
    private StorageHealthService storageHealthService;
    private JwtPersistenceService redisTokenService;
    private JwtPersistenceService fallbackTokenService;
    private JwtPersistenceService compositeTokenService;
    private JwtBlacklistService redisBlacklistService;
    private JwtBlacklistService fallbackBlacklistService;
    private JwtBlacklistService compositeBlacklistService;
    private DataSyncService dataSyncService;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.scan()).thenReturn(Flux.empty());
        
        // 创建存储健康服务
        storageHealthService = new StorageHealthServiceImpl(redisTemplate, storeManager);
        
        // 创建Redis服务（模拟）
        redisTokenService = mock(JwtPersistenceService.class);
        redisBlacklistService = mock(JwtBlacklistService.class);
        
        // 创建fallback服务（模拟）
        fallbackTokenService = mock(JwtPersistenceService.class);
        fallbackBlacklistService = mock(JwtBlacklistService.class);
        
        // 创建复合服务
        compositeTokenService = new CompositeJwtPersistenceServiceImpl(
            redisTokenService, fallbackTokenService, storageHealthService);
        compositeBlacklistService = new CompositeJwtBlacklistServiceImpl(
            redisBlacklistService, fallbackBlacklistService, storageHealthService);
        
        // 创建数据同步服务
        dataSyncService = new DataSyncServiceImpl(redisTemplate, storeManager, storageHealthService);
    }
    
    @Test
    void testCompleteTokenLifecycleWithCompositeStorage() {
        // Given - Redis健康
        mockRedisHealthy();
        
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        
        when(redisTokenService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        when(fallbackTokenService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        when(redisTokenService.findByTokenHash(tokenInfo.getTokenHash())).thenReturn(Mono.just(tokenInfo));
        when(redisTokenService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .thenReturn(Mono.empty());
        when(fallbackTokenService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .thenReturn(Mono.empty());
        
        // When & Then - 保存令牌
        StepVerifier.create(compositeTokenService.saveToken(tokenInfo))
            .verifyComplete();
        
        // 验证同时保存到Redis和fallback
        verify(redisTokenService).saveToken(tokenInfo);
        verify(fallbackTokenService).saveToken(tokenInfo);
        
        // When & Then - 查找令牌
        StepVerifier.create(compositeTokenService.findByTokenHash(tokenInfo.getTokenHash()))
            .expectNext(tokenInfo)
            .verifyComplete();
        
        // 验证优先从Redis查询
        verify(redisTokenService).findByTokenHash(tokenInfo.getTokenHash());
        verify(fallbackTokenService, never()).findByTokenHash(any());
        
        // When & Then - 更新令牌状态
        StepVerifier.create(compositeTokenService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .verifyComplete();
        
        // 验证同时更新Redis和fallback
        verify(redisTokenService).updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED);
        verify(fallbackTokenService).updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED);
    }
    
    @Test
    void testTokenLifecycleWithRedisFailure() {
        // Given - Redis不健康
        mockRedisUnhealthy();
        
        JwtTokenInfo tokenInfo = createTestTokenInfo();
        
        when(fallbackTokenService.saveToken(tokenInfo)).thenReturn(Mono.empty());
        when(fallbackTokenService.findByTokenHash(tokenInfo.getTokenHash())).thenReturn(Mono.just(tokenInfo));
        when(fallbackTokenService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .thenReturn(Mono.empty());
        
        // When & Then - 保存令牌
        StepVerifier.create(compositeTokenService.saveToken(tokenInfo))
            .verifyComplete();
        
        // 验证只保存到fallback
        verify(fallbackTokenService).saveToken(tokenInfo);
        verify(redisTokenService, never()).saveToken(any());
        
        // When & Then - 查找令牌
        StepVerifier.create(compositeTokenService.findByTokenHash(tokenInfo.getTokenHash()))
            .expectNext(tokenInfo)
            .verifyComplete();
        
        // 验证只从fallback查询
        verify(fallbackTokenService).findByTokenHash(tokenInfo.getTokenHash());
        verify(redisTokenService, never()).findByTokenHash(any());
        
        // When & Then - 更新令牌状态
        StepVerifier.create(compositeTokenService.updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED))
            .verifyComplete();
        
        // 验证只更新fallback
        verify(fallbackTokenService).updateTokenStatus(tokenInfo.getTokenHash(), TokenStatus.REVOKED);
        verify(redisTokenService, never()).updateTokenStatus(any(), any());
    }
    
    @Test
    void testBlacklistLifecycleWithCompositeStorage() {
        // Given - Redis健康
        mockRedisHealthy();
        
        String tokenHash = "test-hash";
        String reason = "Manual revocation";
        String addedBy = "admin";
        
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setTokenHash(tokenHash);
        entry.setReason(reason);
        entry.setAddedBy(addedBy);
        entry.setAddedAt(LocalDateTime.now());
        entry.setExpiresAt(LocalDateTime.now().plusHours(24));
        
        when(redisBlacklistService.addToBlacklist(tokenHash, reason, addedBy)).thenReturn(Mono.empty());
        when(fallbackBlacklistService.addToBlacklist(tokenHash, reason, addedBy)).thenReturn(Mono.empty());
        when(redisBlacklistService.isBlacklisted(tokenHash)).thenReturn(Mono.just(true));
        when(redisBlacklistService.removeFromBlacklist(tokenHash)).thenReturn(Mono.empty());
        when(fallbackBlacklistService.removeFromBlacklist(tokenHash)).thenReturn(Mono.empty());
        
        // When & Then - 添加到黑名单
        StepVerifier.create(compositeBlacklistService.addToBlacklist(tokenHash, reason, addedBy))
            .verifyComplete();
        
        // 验证同时添加到Redis和fallback
        verify(redisBlacklistService).addToBlacklist(tokenHash, reason, addedBy);
        verify(fallbackBlacklistService).addToBlacklist(tokenHash, reason, addedBy);
        
        // When & Then - 检查黑名单状态
        StepVerifier.create(compositeBlacklistService.isBlacklisted(tokenHash))
            .expectNext(true)
            .verifyComplete();
        
        // 验证优先从Redis查询
        verify(redisBlacklistService).isBlacklisted(tokenHash);
        verify(fallbackBlacklistService, never()).isBlacklisted(any());
        
        // When & Then - 从黑名单移除
        StepVerifier.create(compositeBlacklistService.removeFromBlacklist(tokenHash))
            .verifyComplete();
        
        // 验证同时从Redis和fallback移除
        verify(redisBlacklistService).removeFromBlacklist(tokenHash);
        verify(fallbackBlacklistService).removeFromBlacklist(tokenHash);
    }
    
    @Test
    void testBlacklistFallbackWhenRedisEmpty() {
        // Given - Redis健康但没有数据
        mockRedisHealthy();
        
        String tokenHash = "test-hash";
        
        when(redisBlacklistService.isBlacklisted(tokenHash)).thenReturn(Mono.just(false));
        when(fallbackBlacklistService.isBlacklisted(tokenHash)).thenReturn(Mono.just(true));
        
        // When & Then - 检查黑名单状态
        StepVerifier.create(compositeBlacklistService.isBlacklisted(tokenHash))
            .expectNext(true)
            .verifyComplete();
        
        // 验证先查Redis，再查fallback
        verify(redisBlacklistService).isBlacklisted(tokenHash);
        verify(fallbackBlacklistService).isBlacklisted(tokenHash);
    }
    
    @Test
    void testDataSyncRecoveryProcess() {
        // Given - 模拟启动恢复场景
        mockRedisHealthy();
        mockStoreManagerHealthy();
        
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // 模拟StoreManager中有数据
        List<String> storeKeys = Arrays.asList(
            "jwt_token_hash1",
            "jwt_token_hash2",
            "jwt_blacklist_hash3"
        );
        when(storeManager.getAllKeys()).thenReturn(storeKeys);
        
        Map<String, Object> tokenData1 = createMockTokenData("hash1", "user1");
        Map<String, Object> tokenData2 = createMockTokenData("hash2", "user2");
        Map<String, Object> blacklistData = createMockBlacklistData("hash3");
        
        when(storeManager.getConfig("jwt_token_hash1")).thenReturn(tokenData1);
        when(storeManager.getConfig("jwt_token_hash2")).thenReturn(tokenData2);
        when(storeManager.getConfig("jwt_blacklist_hash3")).thenReturn(blacklistData);
        
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // When & Then - 执行启动恢复
        StepVerifier.create(dataSyncService.performStartupRecovery())
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getProcessedCount()).isEqualTo(3);
                assertThat(result.getSuccessCount()).isEqualTo(3);
                assertThat(result.getFailureCount()).isEqualTo(0);
            })
            .verifyComplete();
        
        // 验证Redis操作被调用
        verify(valueOperations, times(3)).set(anyString(), anyString(), any(Duration.class));
    }
    
    @Test
    void testDataConsistencyCheck() {
        // Given
        when(redisTemplate.scan()).thenReturn(Flux.just(
            "jwt:token:hash1",
            "jwt:token:hash2"
        ));
        
        when(storeManager.getAllKeys()).thenReturn(Arrays.asList(
            "jwt_token_hash1",
            "jwt_token_hash3"
        ));
        
        // When & Then
        StepVerifier.create(dataSyncService.checkDataConsistency())
            .assertNext(result -> {
                assertThat(result.getRedisCount()).isEqualTo(2);
                assertThat(result.getStoreManagerCount()).isEqualTo(2);
                // Note: 实际的一致性检查逻辑在当前实现中是简化的
            })
            .verifyComplete();
    }
    
    @Test
    void testStorageHealthMonitoring() {
        // Given - Redis健康检查成功
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just("health_check_test"));
        
        // StoreManager健康检查成功
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
        
        // When & Then - 检查所有存储健康状态
        StepVerifier.create(storageHealthService.getAllStorageHealth())
            .assertNext(healthMap -> {
                assertThat(healthMap.get("redis")).isEqualTo(true);
                assertThat(healthMap.get("storemanager")).isEqualTo(true);
            })
            .verifyComplete();
        
        // When & Then - 获取详细健康信息
        StepVerifier.create(storageHealthService.getDetailedHealthInfo())
            .assertNext(detailedInfo -> {
                assertThat(detailedInfo).containsKey("health");
                assertThat(detailedInfo).containsKey("stats");
                assertThat(detailedInfo).containsKey("config");
            })
            .verifyComplete();
    }
    
    // Helper methods
    
    private void mockRedisHealthy() {
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.just("health_check_test"));
    }
    
    private void mockRedisUnhealthy() {
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
    }
    
    private void mockStoreManagerHealthy() {
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        when(storeManager.getConfig(anyString())).thenReturn(testData);
        when(storeManager.exists(anyString())).thenReturn(true);
        doNothing().when(storeManager).saveConfig(anyString(), any());
        doNothing().when(storeManager).deleteConfig(anyString());
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
    
    private Map<String, Object> createMockTokenData(String tokenHash, String userId) {
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("id", "token-id-" + tokenHash);
        tokenData.put("userId", userId);
        tokenData.put("tokenHash", tokenHash);
        tokenData.put("status", "ACTIVE");
        tokenData.put("issuedAt", "2024-01-01T10:00:00");
        tokenData.put("expiresAt", "2024-01-01T11:00:00");
        tokenData.put("createdAt", "2024-01-01T10:00:00");
        tokenData.put("updatedAt", "2024-01-01T10:00:00");
        return tokenData;
    }
    
    private Map<String, Object> createMockBlacklistData(String tokenHash) {
        Map<String, Object> blacklistData = new HashMap<>();
        blacklistData.put("tokenHash", tokenHash);
        blacklistData.put("reason", "Manual revocation");
        blacklistData.put("addedBy", "admin");
        blacklistData.put("addedAt", "2024-01-01T10:00:00");
        blacklistData.put("expiresAt", "2024-01-01T11:00:00");
        return blacklistData;
    }
}