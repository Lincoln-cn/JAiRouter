package org.unreal.modelrouter.auth.controller;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AddBlacklistRequest;
import org.unreal.modelrouter.common.dto.BlacklistEntryDTO;
import org.unreal.modelrouter.common.dto.BlacklistStatsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistType;
import org.unreal.modelrouter.auth.security.service.SecurityBlacklistService;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SecurityBlacklistController 单元测试
 * 
 * <p>测试黑名单管理功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("SecurityBlacklistController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityBlacklistControllerTest {

    @Mock
    private SecurityBlacklistService blacklistService;

    @InjectMocks
    private SecurityBlacklistController controller;

    private BlacklistEntryDTO testEntry;
    private Principal principal;

    @BeforeEach
    void setUp() {
        testEntry = new BlacklistEntryDTO();
        testEntry.setId(1L);
        testEntry.setBlacklistType("TOKEN");
        testEntry.setTargetValue("test-token-value");
        testEntry.setStatus("ACTIVE");

        principal = () -> "admin-user";
    }

    // ==================== 获取黑名单列表测试 ====================

    @Nested
    @DisplayName("GET /api/security/blacklist/list - 获取黑名单列表测试")
    class GetBlacklistPageTests {

        @Test
        @DisplayName("BLACKLIST-001: 成功获取黑名单列表")
        void testGetBlacklistPage_success() {
            // Given
            Page<BlacklistEntryDTO> page = new PageImpl<>(List.of(testEntry));
            when(blacklistService.getBlacklistPage(null, null, 0, 20)).thenReturn(page);

            // When
            ResponseEntity<RouterResponse<Page<BlacklistEntryDTO>>> result = controller.getBlacklistPage(null, null, 0, 20);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertNotNull(result.getBody().getData());
        }

        @Test
        @DisplayName("BLACKLIST-002: 无效类型返回错误")
        void testGetBlacklistPage_invalidType() {
            // When
            ResponseEntity<RouterResponse<Page<BlacklistEntryDTO>>> result = controller.getBlacklistPage("INVALID", null, 0, 20);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("无效的黑名单类型"));
        }
    }

    // ==================== 获取黑名单统计测试 ====================

    @Nested
    @DisplayName("GET /api/security/blacklist/stats - 获取黑名单统计测试")
    class GetBlacklistStatsTests {

        @Test
        @DisplayName("BLACKLIST-004: 成功获取统计信息")
        void testGetBlacklistStats_success() {
            // Given
            BlacklistStatsDTO stats = new BlacklistStatsDTO();
            stats.setTotalActive(10L);
            stats.setTokenCount(5L);
            when(blacklistService.getBlacklistStats()).thenReturn(stats);

            // When
            ResponseEntity<RouterResponse<BlacklistStatsDTO>> result = controller.getBlacklistStats();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(10L, result.getBody().getData().getTotalActive());
        }
    }

    // ==================== 获取黑名单详情测试 ====================

    @Nested
    @DisplayName("GET /api/security/blacklist/{id} - 获取黑名单详情测试")
    class GetBlacklistEntryTests {

        @Test
        @DisplayName("BLACKLIST-005: 成功获取黑名单详情")
        void testGetBlacklistEntry_success() {
            // Given
            when(blacklistService.getBlacklistEntry(1L)).thenReturn(testEntry);

            // When
            ResponseEntity<RouterResponse<BlacklistEntryDTO>> result = controller.getBlacklistEntry(1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(1L, result.getBody().getData().getId());
        }

        @Test
        @DisplayName("BLACKLIST-006: 黑名单不存在返回错误")
        void testGetBlacklistEntry_notFound() {
            // Given
            when(blacklistService.getBlacklistEntry(999L)).thenReturn(null);

            // When
            ResponseEntity<RouterResponse<BlacklistEntryDTO>> result = controller.getBlacklistEntry(999L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertTrue(result.getBody().getMessage().contains("不存在"));
        }
    }

    // ==================== 添加黑名单测试 ====================

    @Nested
    @DisplayName("POST /api/security/blacklist/add - 添加黑名单测试")
    class AddToBlacklistTests {

        @Test
        @DisplayName("BLACKLIST-007: 成功添加黑名单")
        void testAddToBlacklist_success() {
            // Given
            AddBlacklistRequest request = new AddBlacklistRequest();
            request.setBlacklistType("TOKEN");
            request.setTargetValue("token-to-block");
            
            when(blacklistService.addToBlacklist(any(), anyString())).thenReturn(testEntry);

            // When
            ResponseEntity<RouterResponse<BlacklistEntryDTO>> result = controller.addToBlacklist(request, principal);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }

        @Test
        @DisplayName("BLACKLIST-008: 无参数返回错误")
        void testAddToBlacklist_invalidParam() {
            // Given
            AddBlacklistRequest request = new AddBlacklistRequest();
            when(blacklistService.addToBlacklist(any(), anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid parameter"));

            // When
            ResponseEntity<RouterResponse<BlacklistEntryDTO>> result = controller.addToBlacklist(request, principal);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
        }
    }

    // ==================== 批量添加黑名单测试 ====================

    @Nested
    @DisplayName("POST /api/security/blacklist/batch-add - 批量添加黑名单测试")
    class BatchAddToBlacklistTests {

        @Test
        @DisplayName("BLACKLIST-009: 成功批量添加")
        void testBatchAddToBlacklist_success() {
            // Given
            AddBlacklistRequest request1 = new AddBlacklistRequest();
            request1.setBlacklistType("TOKEN");
            request1.setTargetValue("token-1");
            
            AddBlacklistRequest request2 = new AddBlacklistRequest();
            request2.setBlacklistType("IP");
            request2.setTargetValue("192.168.1.100");
            
            when(blacklistService.batchAddToBlacklist(anyList(), anyString())).thenReturn(2);

            // When
            ResponseEntity<RouterResponse<Integer>> result = controller.batchAddToBlacklist(List.of(request1, request2), principal);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(2, result.getBody().getData());
        }
    }

    // ==================== 移除黑名单测试 ====================

    @Nested
    @DisplayName("DELETE /api/security/blacklist/{id} - 移除黑名单测试")
    class RemoveFromBlacklistTests {

        @Test
        @DisplayName("BLACKLIST-010: 成功移除黑名单")
        void testRemoveFromBlacklist_success() {
            // Given
            when(blacklistService.removeFromBlacklist(1L)).thenReturn(true);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.removeFromBlacklist(1L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertTrue(result.getBody().getData());
        }

        @Test
        @DisplayName("BLACKLIST-011: 黑名单不存在返回错误")
        void testRemoveFromBlacklist_notFound() {
            // Given
            when(blacklistService.removeFromBlacklist(999L)).thenReturn(false);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.removeFromBlacklist(999L);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
        }
    }

    // ==================== 检查黑名单测试 ====================

    @Nested
    @DisplayName("GET /api/security/blacklist/check - 检查黑名单测试")
    class CheckBlacklistTests {

        @Test
        @DisplayName("BLACKLIST-012: 目标在黑名单中")
        void testCheckBlacklist_inBlacklist() {
            // Given
            when(blacklistService.isInBlacklist(BlacklistType.TOKEN, "blocked-token")).thenReturn(true);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.checkBlacklist("TOKEN", "blocked-token");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().getData());
        }

        @Test
        @DisplayName("BLACKLIST-013: 目标不在黑名单中")
        void testCheckBlacklist_notInBlacklist() {
            // Given
            when(blacklistService.isInBlacklist(BlacklistType.IP, "192.168.1.1")).thenReturn(false);

            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.checkBlacklist("IP", "192.168.1.1");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().getData());
        }

        @Test
        @DisplayName("BLACKLIST-014: 无效类型返回错误")
        void testCheckBlacklist_invalidType() {
            // When
            ResponseEntity<RouterResponse<Boolean>> result = controller.checkBlacklist("INVALID", "value");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
        }
    }

    // ==================== 清理过期黑名单测试 ====================

    @Nested
    @DisplayName("POST /api/security/blacklist/cleanup - 清理过期黑名单测试")
    class CleanupExpiredTests {

        @Test
        @DisplayName("BLACKLIST-015: 成功清理过期条目")
        void testCleanupExpired_success() {
            // Given
            when(blacklistService.cleanupExpiredEntries()).thenReturn(5);

            // When
            ResponseEntity<RouterResponse<Integer>> result = controller.cleanupExpired();

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(5, result.getBody().getData());
        }
    }
}
