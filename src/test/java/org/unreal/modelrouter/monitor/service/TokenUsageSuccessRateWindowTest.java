package org.unreal.modelrouter.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.repository.TokenUsageRepository;

/**
 * #714 守卫测试：成功率的分母必须是「请求的时间范围内」的请求数。
 *
 * <p>修复前分母取 {@code countAllUsage()}（无 WHERE），分子取时间范围计数，
 * 于是历史数据越多成功率越低（值班会误判上游大面积失败）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#714 用量成功率统计口径")
class TokenUsageSuccessRateWindowTest {

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    @InjectMocks
    private TokenUsageService tokenUsageService;

    private void stubEmptyBreakdowns() {
        when(tokenUsageRepository.countTokensByServiceType(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByModel(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByProvider(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByDay(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByWeek(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByMonth(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByHour(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByApiKey(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.countTokensByUser(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("区间内 2 条（1 成功 1 失败）+ 历史 1000 条 ⇒ 成功率 50%")
    void successRateUsesTheSameTimeWindowAsTheNumerator() {
        final LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        final LocalDateTime endTime = LocalDateTime.now();

        // 全量计数故意给一个远大于区间计数的值：修复前它会成为分母（成功率 0.1%）
        // lenient：修复后这条桩本就不该被调用，verify(never()) 才是断言本体
        lenient().when(tokenUsageRepository.countAllUsage()).thenReturn(1000L);
        when(tokenUsageRepository.countByTimeRange(any(), any())).thenReturn(2L);
        when(tokenUsageRepository.countTotalTokensByTimeRange(any(), any())).thenReturn(40L);
        when(tokenUsageRepository.countSuccessByTimeRange(any(), any())).thenReturn(1L);
        when(tokenUsageRepository.countFailedByTimeRange(any(), any())).thenReturn(1L);
        when(tokenUsageRepository.avgResponseTimeByTimeRange(any(), any())).thenReturn(120.0);
        stubEmptyBreakdowns();

        final TokenUsageStatisticsDTO result = tokenUsageService.getTokenUsageStatistics(startTime, endTime);

        assertEquals(2L, result.getTotalRequests(), "分母必须是区间内的请求数");
        assertEquals(50.0, result.getSuccessRate(), 0.001, "区间 1 成功 1 失败 ⇒ 50%");
        verify(tokenUsageRepository, never()).countAllUsage();
    }

    @Test
    @DisplayName("区间内没有请求时不调用全量计数，成功率为 0")
    void emptyWindowDoesNotFallBackToTheAllTimeCount() {
        // lenient：修复后这条桩本就不该被调用，verify(never()) 才是断言本体
        lenient().when(tokenUsageRepository.countAllUsage()).thenReturn(1000L);
        when(tokenUsageRepository.countByTimeRange(any(), any())).thenReturn(0L);
        when(tokenUsageRepository.countTotalTokensByTimeRange(any(), any())).thenReturn(0L);
        when(tokenUsageRepository.countSuccessByTimeRange(any(), any())).thenReturn(0L);
        when(tokenUsageRepository.countFailedByTimeRange(any(), any())).thenReturn(0L);
        when(tokenUsageRepository.avgResponseTimeByTimeRange(any(), any())).thenReturn(0.0);
        stubEmptyBreakdowns();

        final TokenUsageStatisticsDTO result = tokenUsageService.getTokenUsageStatistics(null, null);

        assertEquals(0L, result.getTotalRequests());
        assertEquals(0.0, result.getSuccessRate(), 0.001);
        verify(tokenUsageRepository, never()).countAllUsage();
    }
}
