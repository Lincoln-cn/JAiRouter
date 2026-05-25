package org.unreal.modelrouter.monitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.persistence.jpa.repository.TokenUsageRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * TokenUsageService 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenUsageService 测试")
class TokenUsageServiceTest {

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    @InjectMocks
    private TokenUsageService tokenUsageService;

    private TokenUsageRecordDTO createTestRecord() {
        return TokenUsageRecordDTO.builder()
                .traceId("trace-001")
                .serviceType("chat")
                .modelName("gpt-4")
                .provider("openai")
                .instanceName("instance-1")
                .instanceUrl("http://localhost:8080")
                .promptTokens(100L)
                .completionTokens(50L)
                .totalTokens(150L)
                .apiKeyId("key-001")
                .userId("user-001")
                .clientIp("127.0.0.1")
                .isSuccess(true)
                .responseTimeMs(500L)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("SVC-TOKEN-001: 记录使用量测试")
    class RecordUsageTests {

        @Test
        @DisplayName("记录单条Token使用量")
        void testRecordTokenUsage() {
            TokenUsageRecordDTO record = createTestRecord();

            when(tokenUsageRepository.save(any(TokenUsageEntity.class)))
                    .thenReturn(new TokenUsageEntity());

            tokenUsageService.recordTokenUsage(record);

            verify(tokenUsageRepository).save(any(TokenUsageEntity.class));
        }

        @Test
        @DisplayName("记录使用量时异常不应抛出")
        void testRecordTokenUsageException() {
            TokenUsageRecordDTO record = createTestRecord();

            when(tokenUsageRepository.save(any(TokenUsageEntity.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // 不应该抛出异常
            assertDoesNotThrow(() -> tokenUsageService.recordTokenUsage(record));
        }

        @Test
        @DisplayName("批量记录Token使用量")
        void testRecordTokenUsageBatch() {
            TokenUsageRecordDTO record1 = createTestRecord();
            TokenUsageRecordDTO record2 = TokenUsageRecordDTO.builder()
                    .traceId("trace-002")
                    .serviceType("chat")
                    .modelName("gpt-4")
                    .provider("openai")
                    .promptTokens(200L)
                    .completionTokens(100L)
                    .totalTokens(300L)
                    .isSuccess(true)
                    .build();

            List<TokenUsageRecordDTO> records = List.of(record1, record2);

            when(tokenUsageRepository.save(any(TokenUsageEntity.class)))
                    .thenReturn(new TokenUsageEntity());

            tokenUsageService.recordTokenUsageBatch(records);

            verify(tokenUsageRepository, times(2)).save(any(TokenUsageEntity.class));
        }
    }

    @Nested
    @DisplayName("SVC-TOKEN-002: 查询使用记录测试")
    class QueryUsageTests {

        @Test
        @DisplayName("获取最近使用记录")
        void testGetRecentUsage() {
            when(tokenUsageRepository.findRecentUsage(anyInt()))
                    .thenReturn(Collections.emptyList());

            List<TokenUsageEntity> result = tokenUsageService.getRecentUsage(10);

            assertNotNull(result);
            verify(tokenUsageRepository).findRecentUsage(10);
        }

        @Test
        @DisplayName("获取最近使用记录限制最大数量为100")
        void testGetRecentUsageLimitMax() {
            when(tokenUsageRepository.findRecentUsage(anyInt()))
                    .thenReturn(Collections.emptyList());

            tokenUsageService.getRecentUsage(1000);

            verify(tokenUsageRepository).findRecentUsage(100);
        }

        @Test
        @DisplayName("按模型获取最近使用记录")
        void testGetRecentUsageByModel() {
            when(tokenUsageRepository.findRecentUsageByModel(anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<TokenUsageEntity> result = tokenUsageService.getRecentUsageByModel("gpt-4", 10);

            assertNotNull(result);
            verify(tokenUsageRepository).findRecentUsageByModel("gpt-4", 10);
        }
    }

    @Nested
    @DisplayName("SVC-TOKEN-003: 删除过期记录测试")
    class DeleteOldRecordsTests {

        @Test
        @DisplayName("删除过期使用记录")
        void testDeleteOldUsageRecords() {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30);
            when(tokenUsageRepository.deleteByOccurredAtBefore(any(LocalDateTime.class)))
                    .thenReturn(100);

            int deleted = tokenUsageService.deleteOldUsageRecords(cutoffTime);

            assertEquals(100, deleted);
            verify(tokenUsageRepository).deleteByOccurredAtBefore(cutoffTime);
        }
    }

    @Nested
    @DisplayName("SVC-TOKEN-004: 获取排名测试")
    class TopRankingTests {

        @Test
        @DisplayName("获取模型使用量排名")
        void testGetTopModels() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            LocalDateTime endTime = LocalDateTime.now();

            when(tokenUsageRepository.countTokensByModel(any(), any()))
                    .thenReturn(Collections.emptyList());

            List<?> result = tokenUsageService.getTopModels(startTime, endTime, 10);

            assertNotNull(result);
            verify(tokenUsageRepository).countTokensByModel(startTime, endTime);
        }

        @Test
        @DisplayName("获取服务类型使用量排名")
        void testGetTopServiceTypes() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            LocalDateTime endTime = LocalDateTime.now();

            when(tokenUsageRepository.countTokensByServiceType(any(), any()))
                    .thenReturn(Collections.emptyList());

            List<?> result = tokenUsageService.getTopServiceTypes(startTime, endTime, 10);

            assertNotNull(result);
            verify(tokenUsageRepository).countTokensByServiceType(startTime, endTime);
        }
    }

    @Nested
    @DisplayName("SVC-TOKEN-005: 获取统计信息测试")
    class StatisticsTests {

        @Test
        @DisplayName("获取Token使用量统计信息")
        void testGetTokenUsageStatistics() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            LocalDateTime endTime = LocalDateTime.now();

            // Mock 所有统计查询
            when(tokenUsageRepository.countAllUsage()).thenReturn(100L);
            when(tokenUsageRepository.countTotalTokensByTimeRange(any(), any())).thenReturn(1000L);
            when(tokenUsageRepository.countSuccessByTimeRange(any(), any())).thenReturn(95L);
            when(tokenUsageRepository.countFailedByTimeRange(any(), any())).thenReturn(5L);
            when(tokenUsageRepository.avgResponseTimeByTimeRange(any(), any())).thenReturn(250.0);
            when(tokenUsageRepository.countTokensByServiceType(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByModel(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByProvider(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByDay(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByWeek(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByMonth(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByHour(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByApiKey(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByUser(any(), any())).thenReturn(Collections.emptyList());

            TokenUsageStatisticsDTO result = tokenUsageService.getTokenUsageStatistics(startTime, endTime);

            assertNotNull(result);
            assertEquals(100L, result.getTotalRequests());
            assertEquals(1000L, result.getTotalTokens());
            assertEquals(95L, result.getSuccessfulRequests());
            assertEquals(5L, result.getFailedRequests());
            assertEquals(95.0, result.getSuccessRate(), 0.1);
        }

        @Test
        @DisplayName("获取统计信息使用默认时间范围")
        void testGetTokenUsageStatisticsDefaultTimeRange() {
            when(tokenUsageRepository.countAllUsage()).thenReturn(0L);
            when(tokenUsageRepository.countTotalTokensByTimeRange(any(), any())).thenReturn(0L);
            when(tokenUsageRepository.countSuccessByTimeRange(any(), any())).thenReturn(0L);
            when(tokenUsageRepository.countFailedByTimeRange(any(), any())).thenReturn(0L);
            when(tokenUsageRepository.avgResponseTimeByTimeRange(any(), any())).thenReturn(0.0);
            when(tokenUsageRepository.countTokensByServiceType(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByModel(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByProvider(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByDay(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByWeek(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByMonth(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByHour(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByApiKey(any(), any())).thenReturn(Collections.emptyList());
            when(tokenUsageRepository.countTokensByUser(any(), any())).thenReturn(Collections.emptyList());

            // 不传时间参数，使用默认值
            TokenUsageStatisticsDTO result = tokenUsageService.getTokenUsageStatistics(null, null);

            assertNotNull(result);
            assertNotNull(result.getStartTime());
            assertNotNull(result.getEndTime());
        }
    }
}
