package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApiCallHistoryService 单元测试
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryService 测试")
class ApiCallHistoryServiceTest {

    @Mock
    private ApiCallHistoryRepository repository;

    @InjectMocks
    private ApiCallHistoryService service;

    private CallHistoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CallHistoryProperties();
        properties.setEnabled(true);
        properties.setRetentionDays(30);
        properties.setRequestBodySummaryEnabled(true);
        properties.setRequestBodySummaryMaxLength(200);
        properties.setResponseBodySummaryEnabled(true);
        properties.setResponseBodySummaryMaxLength(200);

        // 使用反射注入 properties
        try {
            var field = ApiCallHistoryService.class.getDeclaredField("properties");
            field.setAccessible(true);
            field.set(service, properties);
        } catch (Exception e) {
            // 忽略
        }
    }

    private CallHistoryRecordDTO createTestRecord() {
        return CallHistoryRecordDTO.builder()
                .traceId("trace-001")
                .requestId("req-001")
                .requestMethod("POST")
                .requestPath("/v1/chat/completions")
                .contentType("application/json")
                .serviceType("chat")
                .modelName("gpt-4")
                .provider("openai")
                .instanceName("instance-1")
                .instanceUrl("http://localhost:8080")
                .httpStatusCode(200)
                .promptTokens(100L)
                .completionTokens(50L)
                .totalTokens(150L)
                .responseTimeMs(500L)
                .isSuccess(true)
                .apiKeyId("key-001")
                .userId("user-001")
                .clientIp("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .rateLimited(false)
                .circuitBroken(false)
                .build();
    }

    @Nested
    @DisplayName("记录调用历史测试")
    class RecordTests {

        @Test
        @DisplayName("记录单条调用历史")
        void testRecord() {
            CallHistoryRecordDTO dto = createTestRecord();
            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenReturn(new ApiCallHistoryEntity());

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result);
            verify(repository).save(any(ApiCallHistoryEntity.class));
        }

        @Test
        @DisplayName("记录调用历史时异常不应抛出")
        void testRecordException() {
            CallHistoryRecordDTO dto = createTestRecord();
            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenThrow(new RuntimeException("Database error"));

            assertDoesNotThrow(() -> service.record(dto));
            verify(repository).save(any(ApiCallHistoryEntity.class));
        }

        @Test
        @DisplayName("批量记录调用历史")
        void testBatchRecord() {
            List<CallHistoryRecordDTO> records = List.of(createTestRecord(), createTestRecord());
            when(repository.saveAll(anyList()))
                    .thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> service.batchRecord(records));
            verify(repository).saveAll(anyList());
        }

        @Test
        @DisplayName("批量记录空列表")
        void testBatchRecordEmpty() {
            assertDoesNotThrow(() -> service.batchRecord(Collections.emptyList()));
            verify(repository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("查询调用历史测试")
    class QueryTests {

        @Test
        @DisplayName("分页查询调用历史")
        void testQuery() {
            Page<ApiCallHistoryEntity> page = new PageImpl<>(Collections.emptyList());
            when(repository.findWithFilters(any(), any(), any(), any(), any(), any(), any(), any(PageRequest.class)))
                    .thenReturn(page);

            var result = service.query(new org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO());

            assertNotNull(result);
            verify(repository).findWithFilters(any(), any(), any(), any(), any(), any(), any(), any(PageRequest.class));
        }

        @Test
        @DisplayName("根据 traceId 查询调用链路")
        void testFindByTraceId() {
            when(repository.findByTraceId("trace-001"))
                    .thenReturn(Collections.singletonList(new ApiCallHistoryEntity()));

            var result = service.findByTraceId("trace-001");

            assertNotNull(result);
            assertEquals(1, result.size());
            verify(repository).findByTraceId("trace-001");
        }

        @Test
        @DisplayName("获取最近的调用记录")
        void testFindRecent() {
            when(repository.findAll(any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(Collections.singletonList(new ApiCallHistoryEntity())));

            var result = service.findRecent(10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("查询错误调用")
        void testFindErrors() {
            when(repository.findErrors(any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            var result = service.findErrors(null, null, 50);

            assertNotNull(result);
            verify(repository).findErrors(any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class));
        }

        @Test
        @DisplayName("查询慢调用")
        void testFindSlowCalls() {
            when(repository.findSlowCalls(eq(1000L), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            var result = service.findSlowCalls(1000L, null, null, 50);

            assertNotNull(result);
            verify(repository).findSlowCalls(eq(1000L), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("统计测试")
    class StatisticsTests {

        @Test
        @DisplayName("获取统计信息")
        void testGetStatistics() {
            when(repository.getSummary(any(), any()))
                    .thenReturn(new Object[]{100L, 15000L, 500.0, 90L});
            when(repository.countByModel(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(repository.countByServiceType(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(repository.countByDay(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(repository.countByHour(anyString()))
                    .thenReturn(Collections.emptyList());
            when(repository.countByStatusCode(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(repository.countByErrorCode(any(), any()))
                    .thenReturn(Collections.emptyList());

            var result = service.getStatistics(null, null);

            assertNotNull(result);
            assertEquals(100L, result.getTotalRequests());
            assertEquals(15000L, result.getTotalTokens());
            assertEquals(500.0, result.getAvgResponseTimeMs());
            assertEquals(90.0, result.getSuccessRate());
        }
    }

    @Nested
    @DisplayName("清理测试")
    class CleanupTests {

        @Test
        @DisplayName("清理过期数据")
        void testCleanup() {
            when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class)))
                    .thenReturn(10);

            int result = service.cleanup(LocalDateTime.now().minusDays(30));

            assertEquals(10, result);
            verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("根据保留天数清理")
        void testCleanupByRetentionDays() {
            when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class)))
                    .thenReturn(5);

            int result = service.cleanupByRetentionDays();

            assertEquals(5, result);
        }

        @Test
        @DisplayName("获取总记录数")
        void testCountAll() {
            when(repository.countAll()).thenReturn(100L);

            long result = service.countAll();

            assertEquals(100L, result);
            verify(repository).countAll();
        }
    }

    @Nested
    @DisplayName("实体构建测试")
    class BuildEntityTests {

        @Test
        @DisplayName("构建实体 - 正常数据")
        void testBuildEntity() {
            CallHistoryRecordDTO dto = createTestRecord();
            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenReturn(new ApiCallHistoryEntity());

            var result = service.record(dto);

            assertNotNull(result);
            verify(repository).save(any(ApiCallHistoryEntity.class));
        }

        @Test
        @DisplayName("构建实体 - 空数据使用默认值")
        void testBuildEntityDefaults() {
            CallHistoryRecordDTO dto = CallHistoryRecordDTO.builder().build();
            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenReturn(new ApiCallHistoryEntity());

            var result = service.record(dto);

            assertNotNull(result);
            verify(repository).save(any(ApiCallHistoryEntity.class));
        }

        @Test
        @DisplayName("构建实体 - 请求体摘要截断")
        void testBuildEntityRequestBodyTruncation() {
            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody("a".repeat(500));
            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenReturn(new ApiCallHistoryEntity());

            var result = service.record(dto);

            assertNotNull(result);
            verify(repository).save(any(ApiCallHistoryEntity.class));
        }
    }
}
