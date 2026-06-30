package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

/**
 * ApiKeyQuotaCleanupScheduler 单元测试
 *
 * @author JAiRouter Team
 * @since v2.7.6
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyQuotaCleanupScheduler 清理调度器测试")
class ApiKeyQuotaCleanupSchedulerTest {

    @Mock
    private ApiKeyQuotaService quotaService;

    @InjectMocks
    private ApiKeyQuotaCleanupScheduler scheduler;

    @Test
    @DisplayName("cleanupExpiredDailyUsage - 正常执行清理")
    void cleanupExpiredDailyUsage_shouldCallQuotaService() {
        when(quotaService.cleanupExpiredDailyUsage()).thenReturn(5);

        scheduler.cleanupExpiredDailyUsage();

        verify(quotaService).cleanupExpiredDailyUsage();
    }

    @Test
    @DisplayName("cleanupExpiredDailyUsage - 异常不影响调度器")
    void cleanupExpiredDailyUsage_exception_shouldNotPropagate() {
        when(quotaService.cleanupExpiredDailyUsage())
            .thenThrow(new RuntimeException("模拟异常"));

        // 不应抛出异常
        scheduler.cleanupExpiredDailyUsage();

        verify(quotaService).cleanupExpiredDailyUsage();
    }

    @Test
    @DisplayName("cleanupExpiredDailyUsage - 返回 0 条时正常完成")
    void cleanupExpiredDailyUsage_nothingToClean_shouldComplete() {
        when(quotaService.cleanupExpiredDailyUsage()).thenReturn(0);

        scheduler.cleanupExpiredDailyUsage();

        verify(quotaService).cleanupExpiredDailyUsage();
    }
}
