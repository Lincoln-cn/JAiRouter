package org.unreal.modelrouter.security.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.store.StoreManager;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT黑名单服务实现的单元测试
 */
@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceImplTest {
    
    @Mock
    private StoreManager storeManager;
    
    private JwtBlacklistServiceImpl blacklistService;
    
    @BeforeEach
    void setUp() {
        blacklistService = new JwtBlacklistServiceImpl(storeManager);
    }
    
    @Test
    void testAddToBlacklist_Success() {
        // Given
        String tokenHash = "test-token-hash";
        String reason = "Token compromised";
        String addedBy = "admin";
        
        when(storeManager.saveConfig(eq("jwt_blacklist_" + tokenHash), any())).thenReturn(null);
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_index"), any())).thenReturn(null);
        when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_stats"), any())).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.addToBlacklist(tokenHash, reason, addedBy))
            .verifyComplete();
        
        verify(storeManager).saveConfig(eq("jwt_blacklist_" + tokenHash), any());
        verify(storeManager).saveConfig(eq("jwt_blacklist_index"), any());
        verify(storeManager).saveConfig(eq("jwt_blacklist_stats"), any());
    }
    
    @Test
    void testAddToBlacklist_NullTokenHash() {
        // When & Then
        StepVerifier.create(blacklistService.addToBlacklist(null, "reason", "admin"))
            .expectError(RuntimeException.class)
            .verify();
        
        verifyNoInteractions(storeManager);
    }
    
    @Test
    void testAddToBlacklist_EmptyTokenHash() {
        // When & Then
        StepVerifier.create(blacklistService.addToBlacklist("", "reason", "admin"))
            .expectError(RuntimeException.class)
            .verify();
        
        verifyNoInteractions(storeManager);
    }
    
    @Test
    void testIsBlacklisted_TokenExists() {
        // Given
        String tokenHash = "test-token-hash";
        Map<String, Object> entryData = new HashMap<>();
        entryData.put("tokenHash", tokenHash);
        entryData.put("reason", "Test reason");
        entryData.put("addedBy", "admin");
        entryData.put("addedAt", LocalDateTime.now().toString());
        entryData.put("expiresAt", LocalDateTime.now().plusDays(1).toString());
        
        when(storeManager.getConfig("jwt_blacklist_" + tokenHash)).thenReturn(entryData);
        
        // When & Then
        StepVerifier.create(blacklistService.isBlacklisted(tokenHash))
            .expectNext(true)
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_" + tokenHash);
    }
    
    @Test
    void testIsBlacklisted_TokenNotExists() {
        // Given
        String tokenHash = "non-existent-token";
        
        when(storeManager.getConfig("jwt_blacklist_" + tokenHash)).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.isBlacklisted(tokenHash))
            .expectNext(false)
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_" + tokenHash);
    }
    
    @Test
    void testIsBlacklisted_ExpiredToken() {
        // Given
        String tokenHash = "expired-token-hash";
        Map<String, Object> entryData = new HashMap<>();
        entryData.put("tokenHash", tokenHash);
        entryData.put("reason", "Test reason");
        entryData.put("addedBy", "admin");
        entryData.put("addedAt", LocalDateTime.now().minusDays(2).toString());
        entryData.put("expiresAt", LocalDateTime.now().minusDays(1).toString()); // 已过期
        
        when(storeManager.getConfig("jwt_blacklist_" + tokenHash)).thenReturn(entryData);
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_index"), any())).thenReturn(null);
        when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_stats"), any())).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.isBlacklisted(tokenHash))
            .expectNext(false)
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_" + tokenHash);
        // 验证过期条目被异步删除
        verify(storeManager, timeout(1000)).deleteConfig("jwt_blacklist_" + tokenHash);
    }
    
    @Test
    void testIsBlacklisted_NullTokenHash() {
        // When & Then
        StepVerifier.create(blacklistService.isBlacklisted(null))
            .expectNext(false)
            .verifyComplete();
        
        verifyNoInteractions(storeManager);
    }
    
    @Test
    void testRemoveFromBlacklist_Success() {
        // Given
        String tokenHash = "test-token-hash";
        Map<String, Object> entryData = new HashMap<>();
        entryData.put("tokenHash", tokenHash);
        
        when(storeManager.getConfig("jwt_blacklist_" + tokenHash)).thenReturn(entryData);
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_index"), any())).thenReturn(null);
        when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_stats"), any())).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.removeFromBlacklist(tokenHash))
            .verifyComplete();
        
        verify(storeManager).deleteConfig("jwt_blacklist_" + tokenHash);
        verify(storeManager).saveConfig(eq("jwt_blacklist_index"), any());
        verify(storeManager).saveConfig(eq("jwt_blacklist_stats"), any());
    }
    
    @Test
    void testRemoveFromBlacklist_TokenNotExists() {
        // Given
        String tokenHash = "non-existent-token";
        
        when(storeManager.getConfig("jwt_blacklist_" + tokenHash)).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.removeFromBlacklist(tokenHash))
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_" + tokenHash);
        verify(storeManager, never()).deleteConfig(anyString());
    }
    
    @Test
    void testGetBlacklistSize() {
        // Given
        Map<String, Object> indexData = new HashMap<>();
        List<String> tokenHashes = Arrays.asList("token1", "token2", "token3");
        indexData.put("tokenHashes", tokenHashes);
        
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(indexData);
        
        // When & Then
        StepVerifier.create(blacklistService.getBlacklistSize())
            .expectNext(3L)
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_index");
    }
    
    @Test
    void testGetBlacklistSize_EmptyIndex() {
        // Given
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.getBlacklistSize())
            .expectNext(0L)
            .verifyComplete();
        
        verify(storeManager).getConfig("jwt_blacklist_index");
    }
    
    @Test
    void testCleanupExpiredEntries() {
        // Given
        Map<String, Object> indexData = new HashMap<>();
        List<String> tokenHashes = Arrays.asList("token1", "token2", "token3");
        indexData.put("tokenHashes", tokenHashes);
        
        // token1 - 未过期
        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("tokenHash", "token1");
        entry1.put("expiresAt", LocalDateTime.now().plusDays(1).toString());
        
        // token2 - 已过期
        Map<String, Object> entry2 = new HashMap<>();
        entry2.put("tokenHash", "token2");
        entry2.put("expiresAt", LocalDateTime.now().minusDays(1).toString());
        
        // token3 - 已过期
        Map<String, Object> entry3 = new HashMap<>();
        entry3.put("tokenHash", "token3");
        entry3.put("expiresAt", LocalDateTime.now().minusDays(2).toString());
        
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(indexData);
        when(storeManager.getConfig("jwt_blacklist_token1")).thenReturn(entry1);
        when(storeManager.getConfig("jwt_blacklist_token2")).thenReturn(entry2);
        when(storeManager.getConfig("jwt_blacklist_token3")).thenReturn(entry3);
        when(storeManager.saveConfig(eq("jwt_blacklist_index"), any())).thenReturn(null);
        when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(null);
        when(storeManager.saveConfig(eq("jwt_blacklist_stats"), any())).thenReturn(null);
        
        // When & Then
        StepVerifier.create(blacklistService.cleanupExpiredEntries())
            .expectNext(2L) // 应该清理2个过期条目
            .verifyComplete();
        
        verify(storeManager).deleteConfig("jwt_blacklist_token2");
        verify(storeManager).deleteConfig("jwt_blacklist_token3");
        verify(storeManager, never()).deleteConfig("jwt_blacklist_token1");
        verify(storeManager, times(2)).saveConfig(eq("jwt_blacklist_index"), any());
        verify(storeManager).saveConfig(eq("jwt_blacklist_stats"), any());
    }
    
    @Test
    void testGetBlacklistStats() {
        // Given
        Map<String, Object> indexData = new HashMap<>();
        List<String> tokenHashes = Arrays.asList("token1", "token2");
        indexData.put("tokenHashes", tokenHashes);
        
        Map<String, Object> historicalStats = new HashMap<>();
        historicalStats.put("totalAdded", 10L);
        historicalStats.put("totalRemoved", 3L);
        historicalStats.put("totalCleaned", 2L);
        
        // token1 - 活跃
        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("tokenHash", "token1");
        entry1.put("expiresAt", LocalDateTime.now().plusDays(1).toString());
        
        // token2 - 过期
        Map<String, Object> entry2 = new HashMap<>();
        entry2.put("tokenHash", "token2");
        entry2.put("expiresAt", LocalDateTime.now().minusDays(1).toString());
        
        when(storeManager.getConfig("jwt_blacklist_index")).thenReturn(indexData);
        when(storeManager.getConfig("jwt_blacklist_stats")).thenReturn(historicalStats);
        when(storeManager.getConfig("jwt_blacklist_token1")).thenReturn(entry1);
        when(storeManager.getConfig("jwt_blacklist_token2")).thenReturn(entry2);
        
        // When & Then
        StepVerifier.create(blacklistService.getBlacklistStats())
            .assertNext(stats -> {
                assertEquals(2L, stats.get("currentSize"));
                assertEquals(10L, stats.get("totalAdded"));
                assertEquals(3L, stats.get("totalRemoved"));
                assertEquals(2L, stats.get("totalCleaned"));
                assertEquals(1L, stats.get("expiredEntries"));
                assertEquals(1L, stats.get("activeEntries"));
                assertNotNull(stats.get("lastUpdated"));
            })
            .verifyComplete();
    }
    
    @Test
    void testTokenBlacklistEntry() {
        // Given
        String tokenHash = "test-hash";
        String reason = "Test reason";
        String addedBy = "admin";
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        
        // When
        JwtBlacklistService.TokenBlacklistEntry entry = new JwtBlacklistService.TokenBlacklistEntry(
            tokenHash, expiresAt, reason, addedBy
        );
        
        // Then
        assertEquals(tokenHash, entry.getTokenHash());
        assertEquals(expiresAt, entry.getExpiresAt());
        assertEquals(reason, entry.getReason());
        assertEquals(addedBy, entry.getAddedBy());
        assertNotNull(entry.getAddedAt());
        assertFalse(entry.isExpired());
    }
    
    @Test
    void testTokenBlacklistEntry_Expired() {
        // Given
        JwtBlacklistService.TokenBlacklistEntry entry = new JwtBlacklistService.TokenBlacklistEntry();
        entry.setExpiresAt(LocalDateTime.now().minusDays(1));
        
        // When & Then
        assertTrue(entry.isExpired());
    }
    
    @Test
    void testTokenBlacklistEntry_NotExpired() {
        // Given
        JwtBlacklistService.TokenBlacklistEntry entry = new JwtBlacklistService.TokenBlacklistEntry();
        entry.setExpiresAt(LocalDateTime.now().plusDays(1));
        
        // When & Then
        assertFalse(entry.isExpired());
    }
    
    @Test
    void testTokenBlacklistEntry_NullExpiresAt() {
        // Given
        JwtBlacklistService.TokenBlacklistEntry entry = new JwtBlacklistService.TokenBlacklistEntry();
        entry.setExpiresAt(null);
        
        // When & Then
        assertFalse(entry.isExpired()); // null过期时间视为未过期
    }
}