package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.ModelCallStats;
import org.unreal.modelrouter.monitor.service.ModelCallAnalyzer;
import reactor.test.StepVerifier;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ModelCallStatsController RESTful 接口测试
 *
 * 测试范围：
 * - GET /api/model-stats/summary - 获取统计摘要
 * - GET /api/model-stats/models - 获取所有模型统计
 * - GET /api/model-stats/service-types/{serviceType} - 按服务类型获取统计
 * - GET /api/model-stats/models/{serviceType}/{modelName} - 获取指定模型统计
 * - GET /api/model-stats/top/active - 获取Top 10活跃模型
 * - GET /api/model-stats/unhealthy - 获取健康状态异常模型
 * - GET /api/model-stats/grouped-by-service-type - 按服务类型分组统计
 * - GET /api/model-stats/trend - 获取调用趋势
 * - POST /api/model-stats/refresh - 刷新统计
 * - DELETE /api/model-stats/clear - 清空统计
 *
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ModelCallStatsController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class ModelCallStatsControllerTest {

    @Mock
    private ModelCallAnalyzer modelCallAnalyzer;

    @InjectMocks
    private ModelCallStatsController controller;

    private List<ModelCallStats> mockStatsList;

    @BeforeEach
    void setUp() {
        mockStatsList = createMockStatsList();
    }

    private List<ModelCallStats> createMockStatsList() {
        List<ModelCallStats> list = new ArrayList<>();
        ModelCallStats stats1 = ModelCallStats.builder()
                .modelName("gpt-4")
                .serviceType("chat")
                .totalCalls(1000L)
                .successCount(950L)
                .failureCount(50L)
                .build();
        list.add(stats1);

        ModelCallStats stats2 = ModelCallStats.builder()
                .modelName("text-embedding-3")
                .serviceType("embedding")
                .totalCalls(500L)
                .successCount(495L)
                .failureCount(5L)
                .build();
        list.add(stats2);

        return list;
    }

    // ==================== 获取统计摘要测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/summary - 获取统计摘要测试")
    class GetSummaryTests {

        @Test
        @DisplayName("STATS-001: 成功获取统计摘要")
        void testGetSummary_success() {
            // Given
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalModels", 10);
            summary.put("totalCalls", 10000L);
            summary.put("avgSuccessRate", 0.95);
            when(modelCallAnalyzer.getStatsSummary()).thenReturn(summary);

            // When
            var result = controller.getSummary();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                        assertNotNull(response.getBody().getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("STATS-002: 异常处理")
        void testGetSummary_exception() {
            // Given
            when(modelCallAnalyzer.getStatsSummary()).thenThrow(new RuntimeException("Database error"));

            // When
            var result = controller.getSummary();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                        assertFalse(response.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取所有模型统计测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/models - 获取所有模型统计测试")
    class GetAllModelStatsTests {

        @Test
        @DisplayName("STATS-003: 成功获取所有模型统计")
        void testGetAllModelStats_success() {
            // Given
            Map<String, Object> result = new HashMap<>();
            result.put("data", mockStatsList);
            result.put("total", 2L);
            when(modelCallAnalyzer.getAllModelStats(isNull(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(result);

            // When
            var response = controller.getAllModelStats(null, 1, 20, "totalCalls", false);

            // Then
            StepVerifier.create(response)
                    .assertNext(r -> {
                        assertEquals(HttpStatus.OK, r.getStatusCode());
                        assertTrue(r.getBody().isSuccess());
                        assertNotNull(r.getBody().getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("STATS-004: 带服务类型过滤")
        void testGetAllModelStats_withServiceType() {
            // Given
            Map<String, Object> result = new HashMap<>();
            result.put("data", Collections.singletonList(mockStatsList.get(0)));
            result.put("total", 1L);
            when(modelCallAnalyzer.getAllModelStats(eq("chat"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(result);

            // When
            var response = controller.getAllModelStats("chat", 1, 20, "totalCalls", false);

            // Then
            StepVerifier.create(response)
                    .assertNext(r -> {
                        assertEquals(HttpStatus.OK, r.getStatusCode());
                        assertTrue(r.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 按服务类型获取统计测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/service-types/{serviceType} - 按服务类型获取统计测试")
    class GetStatsByServiceTypeTests {

        @Test
        @DisplayName("STATS-005: 成功按服务类型获取统计")
        void testGetStatsByServiceType_success() {
            // Given
            when(modelCallAnalyzer.getStatsByServiceType("chat"))
                    .thenReturn(Collections.singletonList(mockStatsList.get(0)));

            // When
            var result = controller.getStatsByServiceType("chat");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                        assertNotNull(response.getBody().getData());
                        assertEquals(1, response.getBody().getData().size());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取指定模型统计测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/models/{serviceType}/{modelName} - 获取指定模型统计测试")
    class GetModelStatsTests {

        @Test
        @DisplayName("STATS-006: 成功获取指定模型统计")
        void testGetModelStats_success() {
            // Given
            when(modelCallAnalyzer.getModelStats("chat", "gpt-4")).thenReturn(mockStatsList.get(0));

            // When
            var result = controller.getModelStats("chat", "gpt-4");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                        assertNotNull(response.getBody().getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("STATS-007: 模型不存在返回404")
        void testGetModelStats_notFound() {
            // Given
            when(modelCallAnalyzer.getModelStats("chat", "unknown")).thenReturn(null);

            // When
            var result = controller.getModelStats("chat", "unknown");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取Top 10活跃模型测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/top/active - 获取Top 10活跃模型测试")
    class GetTop10ActiveModelsTests {

        @Test
        @DisplayName("STATS-008: 成功获取Top 10活跃模型")
        void testGetTop10ActiveModels_success() {
            // Given
            when(modelCallAnalyzer.getTop10ActiveModels()).thenReturn(mockStatsList);

            // When
            var result = controller.getTop10ActiveModels();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                        assertNotNull(response.getBody().getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取健康状态异常模型测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/unhealthy - 获取健康状态异常模型测试")
    class GetUnhealthyModelsTests {

        @Test
        @DisplayName("STATS-009: 成功获取异常模型")
        void testGetUnhealthyModels_success() {
            // Given
            when(modelCallAnalyzer.getUnhealthyModels()).thenReturn(Collections.emptyList());

            // When
            var result = controller.getUnhealthyModels();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取分组统计测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/grouped-by-service-type - 获取分组统计测试")
    class GetGroupedByServiceTypeTests {

        @Test
        @DisplayName("STATS-010: 成功获取分组统计")
        void testGetGroupedByServiceType_success() {
            // Given
            Map<String, Object> grouped = new HashMap<>();
            grouped.put("chat", Map.of("count", 5, "totalCalls", 5000L));
            when(modelCallAnalyzer.getGroupedByServiceType()).thenReturn(grouped);

            // When
            var result = controller.getGroupedByServiceType();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取调用趋势测试 ====================

    @Nested
    @DisplayName("GET /api/model-stats/trend - 获取调用趋势测试")
    class GetCallTrendTests {

        @Test
        @DisplayName("STATS-011: 成功获取调用趋势")
        void testGetCallTrend_success() {
            // Given
            List<Map<String, Object>> trend = new ArrayList<>();
            trend.add(Map.of("time", "10:00", "calls", 100L));
            when(modelCallAnalyzer.getCallTrend(60)).thenReturn(trend);

            // When
            var result = controller.getCallTrend(60);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 刷新统计测试 ====================

    @Nested
    @DisplayName("POST /api/model-stats/refresh - 刷新统计测试")
    class RefreshStatsTests {

        @Test
        @DisplayName("STATS-012: 成功刷新统计")
        void testRefreshStats_success() {
            // Given
            doNothing().when(modelCallAnalyzer).refreshQps();

            // When
            var result = controller.refreshStats();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                        assertEquals("统计已刷新", response.getBody().getData().get("message"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 清空统计测试 ====================

    @Nested
    @DisplayName("DELETE /api/model-stats/clear - 清空统计测试")
    class ClearStatsTests {

        @Test
        @DisplayName("STATS-013: 成功清空统计")
        void testClearStats_success() {
            // Given
            doNothing().when(modelCallAnalyzer).clearAllStats();

            // When
            var result = controller.clearStats();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertTrue(response.getBody().isSuccess());
                    })
                    .verifyComplete();
        }
    }
}
