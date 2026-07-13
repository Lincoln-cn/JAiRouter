package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.monitor.dto.ExceptionEventDTO;
import org.unreal.modelrouter.monitor.dto.ExceptionQueryRequest;
import org.unreal.modelrouter.monitor.dto.ExceptionStatisticsDTO;
import org.unreal.modelrouter.monitor.service.ExceptionManagementService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ExceptionManagementController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExceptionManagementControllerTest {

    @Mock
    private ExceptionManagementService exceptionManagementService;

    @InjectMocks
    private ExceptionManagementController controller;

    private ExceptionEventDTO createExceptionEvent(String eventId) {
        return ExceptionEventDTO.builder()
                .eventId(eventId)
                .exceptionType("NullPointerException")
                .exceptionMessage("Test exception")
                .operation("testOperation")
                .errorCode("ERR001")
                .errorCategory("SYSTEM")
                .occurredAt(LocalDateTime.now())
                .occurrenceCount(1L)
                .isAggregated(false)
                .build();
    }

    @Nested
    @DisplayName("查询异常事件列表测试")
    class QueryExceptionEventsTests {

        @Test
        @DisplayName("查询成功 - 无筛选条件")
        void querySuccess_noFilters() {
            PagedResult<ExceptionEventDTO> pagedResult = new PagedResult<>(
                    List.of(createExceptionEvent("event-1")), 0, 20, 1
            );
            when(exceptionManagementService.queryExceptionEvents(any(ExceptionQueryRequest.class)))
                    .thenReturn(pagedResult);

            ResponseEntity<RouterResponse<PagedResult<ExceptionEventDTO>>> response =
                    controller.queryExceptionEvents(null, null, null, null, null, null, null, null, null, null, null, 0, 20, "occurredAt", "desc");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getData().getContent().size());
            verify(exceptionManagementService).queryExceptionEvents(any(ExceptionQueryRequest.class));
        }

        @Test
        @DisplayName("查询成功 - 带时间范围")
        void querySuccess_withTimeRange() {
            LocalDateTime startTime = LocalDateTime.now().minusHours(1);
            LocalDateTime endTime = LocalDateTime.now();
            PagedResult<ExceptionEventDTO> pagedResult = new PagedResult<>(Collections.emptyList(), 0, 20, 0);
            when(exceptionManagementService.queryExceptionEvents(any(ExceptionQueryRequest.class)))
                    .thenReturn(pagedResult);

            ResponseEntity<RouterResponse<PagedResult<ExceptionEventDTO>>> response =
                    controller.queryExceptionEvents(startTime, endTime, null, null, null, null, null, null, null, null, null, 0, 20, "occurredAt", "desc");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(0, response.getBody().getData().getContent().size());
        }

        @Test
        @DisplayName("查询成功 - 带异常类型筛选")
        void querySuccess_withExceptionType() {
            PagedResult<ExceptionEventDTO> pagedResult = new PagedResult<>(Collections.emptyList(), 0, 20, 0);
            when(exceptionManagementService.queryExceptionEvents(any(ExceptionQueryRequest.class)))
                    .thenReturn(pagedResult);

            ResponseEntity<RouterResponse<PagedResult<ExceptionEventDTO>>> response =
                    controller.queryExceptionEvents(null, null, "NullPointerException", null, null, null, null, null, null, null, null, 0, 20, "occurredAt", "desc");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("根据ID查询异常事件测试")
    class GetExceptionEventTests {

        @Test
        @DisplayName("查询成功")
        void getSuccess() {
            ExceptionEventDTO event = createExceptionEvent("event-1");
            when(exceptionManagementService.getExceptionEventById("event-1")).thenReturn(event);

            ResponseEntity<RouterResponse<ExceptionEventDTO>> response =
                    controller.getExceptionEvent("event-1");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals("event-1", response.getBody().getData().getEventId());
        }

        @Test
        @DisplayName("事件不存在")
        void eventNotFound() {
            when(exceptionManagementService.getExceptionEventById("nonexistent")).thenReturn(null);

            ResponseEntity<RouterResponse<ExceptionEventDTO>> response =
                    controller.getExceptionEvent("nonexistent");

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            assertEquals("404", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("获取异常统计信息测试")
    class GetExceptionStatisticsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            ExceptionStatisticsDTO statistics = new ExceptionStatisticsDTO();
            when(exceptionManagementService.getExceptionStatistics(any(), any())).thenReturn(statistics);

            ResponseEntity<RouterResponse<ExceptionStatisticsDTO>> response =
                    controller.getExceptionStatistics(null, null);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(exceptionManagementService).getExceptionStatistics(any(), any());
        }
    }

    @Nested
    @DisplayName("获取最近异常事件测试")
    class GetRecentExceptionEventsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            List<ExceptionEventDTO> events = List.of(createExceptionEvent("event-1"));
            when(exceptionManagementService.getRecentExceptionEvents(10)).thenReturn(events);

            ResponseEntity<RouterResponse<List<ExceptionEventDTO>>> response =
                    controller.getRecentExceptionEvents(10);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("获取指定类型的最近异常事件测试")
    class GetRecentExceptionEventsByTypeTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            List<ExceptionEventDTO> events = List.of(createExceptionEvent("event-1"));
            when(exceptionManagementService.getRecentExceptionEventsByType("NullPointerException", 10))
                    .thenReturn(events);

            ResponseEntity<RouterResponse<List<ExceptionEventDTO>>> response =
                    controller.getRecentExceptionEventsByType("NullPointerException", 10);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("删除过期异常事件测试")
    class DeleteOldExceptionEventsTests {

        @Test
        @DisplayName("删除成功 - 所有事件")
        void deleteAllSuccess() {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            when(exceptionManagementService.deleteOldExceptionEvents(cutoffTime)).thenReturn(5);

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.deleteOldExceptionEvents(cutoffTime, false);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(5, response.getBody().getData().get("deletedCount"));
        }

        @Test
        @DisplayName("删除成功 - 仅聚合事件")
        void deleteAggregatedOnlySuccess() {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            when(exceptionManagementService.deleteAggregatedExceptionEvents(cutoffTime)).thenReturn(3);

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.deleteOldExceptionEvents(cutoffTime, true);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(3, response.getBody().getData().get("deletedCount"));
        }
    }

    @Nested
    @DisplayName("获取仪表盘数据测试")
    class GetDashboardDataTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            ExceptionStatisticsDTO statistics = ExceptionStatisticsDTO.builder()
                    .startTime(LocalDateTime.now().minusHours(1))
                    .endTime(LocalDateTime.now())
                    .totalCount(10L)
                    .totalTypes(5)
                    .build();
            List<ExceptionEventDTO> events = List.of(createExceptionEvent("event-1"));
            when(exceptionManagementService.getExceptionStatistics(any(), any())).thenReturn(statistics);
            when(exceptionManagementService.getRecentExceptionEvents(5)).thenReturn(events);

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.getDashboardData(null, null);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNotNull(response.getBody().getData().get("statistics"));
            assertNotNull(response.getBody().getData().get("recentEvents"));
        }
    }
}
