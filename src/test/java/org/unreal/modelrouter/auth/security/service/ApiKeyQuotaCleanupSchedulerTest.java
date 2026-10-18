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

    /**
     * 开关语义（issue #487）：默认启用；显式 false 时 bean **不注册**（调度器真的关得掉）。
     *
     * <p>回归点：开关原名是 {@code app.quota.cleanup.enabled} —— 与本仓 {@code jairouter.*} 约定不符，
     * 且没有 yml 设置它，所以运维改任何键都关不掉这个每天 03:00 的清理任务。</p>
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("开关 jairouter.security.quota.cleanup.enabled")
    class SwitchSemantics {

        private final org.springframework.boot.test.context.runner.ApplicationContextRunner runner =
                new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                        .withBean(ApiKeyQuotaService.class, () -> mock(ApiKeyQuotaService.class))
                        .withUserConfiguration(ApiKeyQuotaCleanupScheduler.class);

        private static final String KEY = "jairouter.security.quota.cleanup.enabled";

        @Test
        @DisplayName("未设置 -> 默认启用（matchIfMissing）")
        void defaultEnabled() {
            runner.run(ctx -> org.junit.jupiter.api.Assertions.assertTrue(
                    ctx.containsBean("apiKeyQuotaCleanupScheduler"),
                    "未配置开关时应默认启用"));
        }

        @Test
        @DisplayName("显式 false -> 不注册（开关真的生效）")
        void explicitFalseDisables() {
            runner.withPropertyValues(KEY + "=false").run(ctx ->
                    org.junit.jupiter.api.Assertions.assertFalse(
                            ctx.containsBean("apiKeyQuotaCleanupScheduler"),
                            "显式 false 时必须不注册该调度器"));
        }

        @Test
        @DisplayName("显式 true -> 注册")
        void explicitTrueEnables() {
            runner.withPropertyValues(KEY + "=true").run(ctx ->
                    org.junit.jupiter.api.Assertions.assertTrue(
                            ctx.containsBean("apiKeyQuotaCleanupScheduler")));
        }
    }
}
