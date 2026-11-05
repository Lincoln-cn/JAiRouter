package org.unreal.modelrouter.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TokenUsageCleanupScheduler} 的用例。
 *
 * <p>核心是两条：截止时间必须由保留天数推算（不能写死），以及清理失败不能把异常抛给调度线程。</p>
 */
@ExtendWith(MockitoExtension.class)
class TokenUsageCleanupSchedulerTest {

    @Mock
    private TokenUsageService tokenUsageService;

    private TokenUsageRetentionProperties properties(int retentionDays) {
        TokenUsageRetentionProperties p = new TokenUsageRetentionProperties();
        p.setRetentionDays(retentionDays);
        return p;
    }

    @Test
    @DisplayName("按保留天数推算截止时间并调用清理")
    void cutoffIsDerivedFromRetentionDays() {
        when(tokenUsageService.deleteOldUsageRecords(any())).thenReturn(3);
        TokenUsageCleanupScheduler scheduler = new TokenUsageCleanupScheduler(tokenUsageService, properties(90));

        LocalDateTime before = LocalDateTime.now().minusDays(90);
        scheduler.cleanup();
        LocalDateTime after = LocalDateTime.now().minusDays(90);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenUsageService).deleteOldUsageRecords(captor.capture());
        assertThat(captor.getValue())
                .as("截止时间应是 now-90 天，而不是写死的常量")
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    @DisplayName("保留天数配置生效：30 天与 120 天得到的截止时间不同")
    void differentRetentionProducesDifferentCutoff() {
        when(tokenUsageService.deleteOldUsageRecords(any())).thenReturn(0);

        new TokenUsageCleanupScheduler(tokenUsageService, properties(30)).cleanup();
        ArgumentCaptor<LocalDateTime> first = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenUsageService).deleteOldUsageRecords(first.capture());

        new TokenUsageCleanupScheduler(tokenUsageService, properties(120)).cleanup();
        ArgumentCaptor<LocalDateTime> second = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenUsageService, org.mockito.Mockito.times(2)).deleteOldUsageRecords(second.capture());

        LocalDateTime cutoff30 = LocalDateTime.now().minusDays(30);
        LocalDateTime cutoff120 = LocalDateTime.now().minusDays(120);
        assertThat(first.getValue()).isAfter(cutoff30.minusDays(1));
        assertThat(second.getAllValues().get(1)).isBefore(cutoff120.plusDays(1));
    }

    @Test
    @DisplayName("保留天数非正数时跳过清理，不调用 service")
    void nonPositiveRetentionSkipsCleanup() {
        new TokenUsageCleanupScheduler(tokenUsageService, properties(0)).cleanup();
        new TokenUsageCleanupScheduler(tokenUsageService, properties(-1)).cleanup();

        verify(tokenUsageService, never()).deleteOldUsageRecords(any());
    }

    @Test
    @DisplayName("清理抛异常时不让异常逃出调度方法")
    void exceptionIsSwallowed() {
        when(tokenUsageService.deleteOldUsageRecords(any())).thenThrow(new IllegalStateException("数据库不可用"));
        TokenUsageCleanupScheduler scheduler = new TokenUsageCleanupScheduler(tokenUsageService, properties(90));

        assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
        verify(tokenUsageService).deleteOldUsageRecords(any());
    }

    @Test
    @DisplayName("默认值与审计口径一致：90 天、默认启用")
    void defaults() {
        TokenUsageRetentionProperties p = new TokenUsageRetentionProperties();
        assertThat(p.getRetentionDays()).isEqualTo(90);
        assertThat(p.isCleanupEnabled()).isTrue();
    }
}
