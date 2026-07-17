package org.unreal.modelrouter.auth.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisConnectionHealthService 单元测试
 */
class RedisConnectionHealthServiceTest {

    private RedisConnectionHealthService service;

    @BeforeEach
    void setUp() {
        service = new RedisConnectionHealthService();
        // redisTemplate 为 null（未配置 Redis）
    }

    @Nested
    @DisplayName("checkRedisConnection 测试")
    class CheckRedisConnectionTests {
        @Test
        @DisplayName("Redis未配置时应返回健康")
        void shouldReturnHealthyWhenNotConfigured() {
            boolean result = service.checkRedisConnection();
            assertTrue(result);
        }

        @Test
        @DisplayName("短时间内应返回缓存结果")
        void shouldReturnCachedResultWithinCacheDuration() {
            boolean first = service.checkRedisConnection();
            boolean second = service.checkRedisConnection();
            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("triggerHealthCheck 测试")
    class TriggerHealthCheckTests {
        @Test
        @DisplayName("应强制执行新的健康检查")
        void shouldForceNewHealthCheck() {
            boolean result = service.triggerHealthCheck();
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("getDetailedHealthStatus 测试")
    class GetDetailedHealthStatusTests {
        @Test
        @DisplayName("应返回包含必要字段的状态Map")
        void shouldReturnStatusMapWithRequiredFields() {
            service.checkRedisConnection();

            Map<String, Object> status = service.getDetailedHealthStatus();

            assertNotNull(status);
            assertTrue(status.containsKey("healthy"));
            assertTrue(status.containsKey("configured"));
            assertTrue(status.containsKey("lastCheckTime"));
            assertTrue(status.containsKey("lastSuccessTime"));
            assertTrue(status.containsKey("lastResponseTimeMs"));
            assertTrue(status.containsKey("consecutiveFailures"));
            assertTrue(status.containsKey("totalChecks"));
            assertTrue(status.containsKey("successfulChecks"));
            assertTrue(status.containsKey("successRatePercent"));
        }

        @Test
        @DisplayName("configured 应为 false（未配置 Redis）")
        void shouldReturnFalseForConfiguredWhenNotConfigured() {
            Map<String, Object> status = service.getDetailedHealthStatus();
            assertFalse((Boolean) status.get("configured"));
        }

        @Test
        @DisplayName("未配置时不应有 connectionInfo")
        void shouldNotHaveConnectionInfoWhenNotConfigured() {
            Map<String, Object> status = service.getDetailedHealthStatus();
            assertFalse(status.containsKey("connectionInfo"));
        }
    }

    @Nested
    @DisplayName("resetHealthStats 测试")
    class ResetHealthStatsTests {
        @Test
        @DisplayName("应重置所有统计计数器")
        void shouldResetAllCounters() {
            service.checkRedisConnection();
            service.resetHealthStats();

            assertEquals(0L, service.getConsecutiveFailures());
            assertEquals(0L, service.getLastResponseTime());
        }
    }

    @Nested
    @DisplayName("getCurrentHealthStatus 测试")
    class GetCurrentHealthStatusTests {
        @Test
        @DisplayName("默认状态下应为健康")
        void shouldReturnHealthyByDefault() {
            assertTrue(service.getCurrentHealthStatus());
        }
    }

    @Nested
    @DisplayName("getLastResponseTime 测试")
    class GetLastResponseTimeTests {
        @Test
        @DisplayName("未配置时响应时间应为 0")
        void shouldReturnZeroWhenNotConfigured() {
            service.resetHealthStats();
            assertEquals(0L, service.getLastResponseTime());
        }
    }

    @Nested
    @DisplayName("getConsecutiveFailures 测试")
    class GetConsecutiveFailuresTests {
        @Test
        @DisplayName("未配置时连续失败次数应为 0")
        void shouldReturnZeroWhenNotConfigured() {
            service.resetHealthStats();
            assertEquals(0L, service.getConsecutiveFailures());
        }
    }

    @Nested
    @DisplayName("shouldAlert 测试")
    class ShouldAlertTests {
        @Test
        @DisplayName("健康状态下不应告警")
        void shouldNotAlertWhenHealthy() {
            service.resetHealthStats();
            assertFalse(service.shouldAlert());
        }
    }
}
