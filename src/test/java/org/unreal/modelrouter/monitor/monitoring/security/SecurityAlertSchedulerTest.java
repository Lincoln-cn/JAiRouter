package org.unreal.modelrouter.monitor.monitoring.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.scheduling.ScheduledTaskLock;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #276：窗口聚合型检查（脱敏异常增长 / JWT 异常活动）此前没有调用方，现由定时任务驱动；
 * 告警是外部可观测副作用，多副本下必须只让一个副本触发。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAlertScheduler：窗口聚合告警的定时驱动与排他")
class SecurityAlertSchedulerTest {

    @Mock
    private SecurityAlertService securityAlertService;

    @Mock
    private ScheduledTaskLock taskLock;

    private SecurityAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SecurityAlertScheduler(securityAlertService, taskLock);
    }

    @Test
    @DisplayName("未取得排他锁时不执行任何检查（多副本下只让一个副本触发）")
    void whenLockNotAcquired_doesNotCheck() {
        when(taskLock.tryAcquire(SecurityAlertScheduler.WINDOW_CHECK_LOCK_KEY, Duration.ofMinutes(5)))
                .thenReturn(false);

        scheduler.checkWindowAggregatedAlerts();

        verify(securityAlertService, never()).checkSanitizationAnomalyAlert();
        verify(securityAlertService, never()).checkJwtAnomalyAlert();
    }

    @Test
    @DisplayName("取得排他锁时两条窗口聚合检查都会执行")
    void whenLockAcquired_checksBothWindowAggregates() {
        when(taskLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(securityAlertService.checkSanitizationAnomalyAlert()).thenReturn(Mono.empty());
        when(securityAlertService.checkJwtAnomalyAlert()).thenReturn(Mono.empty());

        scheduler.checkWindowAggregatedAlerts();

        verify(taskLock).tryAcquire(SecurityAlertScheduler.WINDOW_CHECK_LOCK_KEY, Duration.ofMinutes(5));
        verify(securityAlertService).checkSanitizationAnomalyAlert();
        verify(securityAlertService).checkJwtAnomalyAlert();
    }

    @Test
    @DisplayName("检查失败不向外抛（定时任务不因单次失败中断）")
    void whenCheckFails_errorIsSwallowed() {
        when(taskLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
        when(securityAlertService.checkSanitizationAnomalyAlert())
                .thenReturn(Mono.error(new IllegalStateException("数仓不可用")));
        when(securityAlertService.checkJwtAnomalyAlert()).thenReturn(Mono.empty());

        assertDoesNotThrow(scheduler::checkWindowAggregatedAlerts);
    }
}
