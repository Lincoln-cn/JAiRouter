package org.unreal.modelrouter.persistence.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.monitor.service.TokenUsageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * TokenUsageController 单元测试
 * 
 * <p>测试 Token 使用量统计功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("TokenUsageController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenUsageControllerTest {

    @Mock
    private TokenUsageService tokenUsageService;

    @InjectMocks
    private TokenUsageController controller;

    private TokenUsageRecordDTO testRecord;
    private TokenUsageEntity testEntity;
    private TokenUsageStatisticsDTO testStats;

    @BeforeEach
    void setUp() {
        testRecord = new TokenUsageRecordDTO();
        testRecord.setModelName("gpt-4");
        testRecord.setServiceType("chat");
        testRecord.setPromptTokens(100L);
        testRecord.setCompletionTokens(200L);
        testRecord.setTotalTokens(300L);

        testEntity = new TokenUsageEntity();
        testEntity.setId(1L);
        testEntity.setModelName("gpt-4");
        testEntity.setServiceType("chat");

        testStats = new TokenUsageStatisticsDTO();
        testStats.setTotalRequests(10L);
        testStats.setTotalPromptTokens(1000L);
        testStats.setTotalCompletionTokens(2000L);
        testStats.setTotalTokens(3000L);
        testStats.setStartTime(LocalDateTime.now().minusDays(7));
        testStats.setEndTime(LocalDateTime.now());
    }

    // ==================== 记录使用量测试 ====================

    @Nested
    @DisplayName("POST /api/token-usage/record - 记录使用量测试")
    class RecordTokenUsageTests {

        @Test
        @DisplayName("TOKEN-001: 成功记录Token使用量")
        void testRecordTokenUsage_success() {
            // Given
            doNothing().when(tokenUsageService).recordTokenUsage(any());

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.recordTokenUsage(testRecord);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            verify(tokenUsageService).recordTokenUsage(testRecord);
        }
    }

    // ==================== 批量记录测试 ====================

    @Nested
    @DisplayName("POST /api/token-usage/record/batch - 批量记录测试")
    class RecordTokenUsageBatchTests {

        @Test
        @DisplayName("TOKEN-002: 成功批量记录")
        void testRecordTokenUsageBatch_success() {
            // Given
            doNothing().when(tokenUsageService).recordTokenUsageBatch(anyList());

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.recordTokenUsageBatch(List.of(testRecord));

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }
    }

    // ==================== 获取统计信息测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/statistics - 获取统计信息测试")
    class GetTokenUsageStatisticsTests {

        @Test
        @DisplayName("TOKEN-003: 成功获取统计信息")
        void testGetTokenUsageStatistics_success() {
            // Given
            when(tokenUsageService.getTokenUsageStatistics(any(), any())).thenReturn(testStats);

            // When
            ResponseEntity<RouterResponse<TokenUsageStatisticsDTO>> result = controller.getTokenUsageStatistics(null, null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(10L, result.getBody().getData().getTotalRequests());
        }
    }

    // ==================== 获取最近使用记录测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/recent - 获取最近使用记录测试")
    class GetRecentUsageTests {

        @Test
        @DisplayName("TOKEN-004: 成功获取最近使用记录")
        void testGetRecentUsage_success() {
            // Given
            when(tokenUsageService.getRecentUsage(20)).thenReturn(List.of(testEntity));

            // When
            ResponseEntity<RouterResponse<List<TokenUsageEntity>>> result = controller.getRecentUsage(20);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().getData().size());
        }
    }

    // ==================== 按模型获取最近使用测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/recent/{modelName} - 按模型获取最近使用测试")
    class GetRecentUsageByModelTests {

        @Test
        @DisplayName("TOKEN-005: 成功获取指定模型的最近使用")
        void testGetRecentUsageByModel_success() {
            // Given
            when(tokenUsageService.getRecentUsageByModel("gpt-4", 20)).thenReturn(List.of(testEntity));

            // When
            ResponseEntity<RouterResponse<List<TokenUsageEntity>>> result = controller.getRecentUsageByModel("gpt-4", 20);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().getData().size());
        }
    }

    // ==================== 获取模型排名测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/top/models - 获取模型排名测试")
    class GetTopModelsTests {

        @Test
        @DisplayName("TOKEN-006: 成功获取模型排名")
        void testGetTopModels_success() {
            // Given
            Map<String, Object> modelStats = Map.of("modelName", "gpt-4", "totalTokens", 5000L);
            when(tokenUsageService.getTopModels(any(), any(), anyInt())).thenReturn(List.of(modelStats));

            // When
            ResponseEntity<RouterResponse<List<Map<String, Object>>>> result = controller.getTopModels(null, null, 10);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().getData().size());
        }
    }

    // ==================== 获取服务类型排名测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/top/services - 获取服务类型排名测试")
    class GetTopServiceTypesTests {

        @Test
        @DisplayName("TOKEN-007: 成功获取服务类型排名")
        void testGetTopServiceTypes_success() {
            // Given
            Map<String, Object> serviceStats = Map.of("serviceType", "chat", "totalTokens", 10000L);
            when(tokenUsageService.getTopServiceTypes(any(), any(), anyInt())).thenReturn(List.of(serviceStats));

            // When
            ResponseEntity<RouterResponse<List<Map<String, Object>>>> result = controller.getTopServiceTypes(null, null, 10);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().getData().size());
        }
    }

    // ==================== 获取仪表盘数据测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/dashboard - 获取仪表盘数据测试")
    class GetDashboardDataTests {

        @Test
        @DisplayName("TOKEN-008: 成功获取仪表盘数据")
        void testGetDashboardData_success() {
            // Given
            when(tokenUsageService.getTokenUsageStatistics(any(), any())).thenReturn(testStats);
            when(tokenUsageService.getTopModels(any(), any(), anyInt())).thenReturn(List.of());
            when(tokenUsageService.getTopServiceTypes(any(), any(), anyInt())).thenReturn(List.of());
            when(tokenUsageService.getRecentUsage(anyInt())).thenReturn(List.of(testEntity));

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result = controller.getDashboardData(null, null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody().getData());
            assertTrue(result.getBody().getData().containsKey("statistics"));
            assertTrue(result.getBody().getData().containsKey("topModels"));
            assertTrue(result.getBody().getData().containsKey("topServiceTypes"));
            assertTrue(result.getBody().getData().containsKey("recentUsage"));
        }
    }

    // ==================== 删除过期记录测试 ====================

    @Nested
    @DisplayName("DELETE /api/token-usage/cleanup - 删除过期记录测试")
    class DeleteOldUsageRecordsTests {

        @Test
        @DisplayName("TOKEN-009: 成功删除过期记录")
        void testDeleteOldUsageRecords_success() {
            // Given
            when(tokenUsageService.deleteOldUsageRecords(any())).thenReturn(100);

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result = controller.deleteOldUsageRecords(LocalDateTime.now().minusDays(30));

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(100, result.getBody().getData().get("deletedCount"));
        }
    }
}
