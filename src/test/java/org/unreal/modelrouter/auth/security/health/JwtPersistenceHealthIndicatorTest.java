package org.unreal.modelrouter.auth.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtPersistenceHealthIndicator 单元测试
 */
class JwtPersistenceHealthIndicatorTest {

    private JwtPersistenceHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new JwtPersistenceHealthIndicator();
        // redisTemplate 为 null（未配置 Redis）
    }

    @Nested
    @DisplayName("getHealthStatus 测试")
    class GetHealthStatusTests {
        @Test
        @DisplayName("应返回健康状态Map")
        void shouldReturnHealthStatusMap() {
            Map<String, Object> result = indicator.getHealthStatus();

            assertNotNull(result);
            assertTrue(result.containsKey("status"));
            assertTrue(result.containsKey("checkTime"));
            assertTrue(result.containsKey("overallStatus"));
        }

        @Test
        @DisplayName("Redis未配置时整体状态应为UP")
        void shouldReturnUpWhenRedisNotConfigured() {
            Map<String, Object> result = indicator.getHealthStatus();

            assertEquals("UP", result.get("status"));
            assertEquals("UP", result.get("overallStatus"));
        }

        @Test
        @DisplayName("应包含redis健康信息")
        void shouldContainRedisHealthInfo() {
            Map<String, Object> result = indicator.getHealthStatus();

            assertTrue(result.containsKey("redis"));
            @SuppressWarnings("unchecked")
            Map<String, Object> redis = (Map<String, Object>) result.get("redis");
            assertEquals("DISABLED", redis.get("status"));
        }

        @Test
        @DisplayName("应包含memory健康信息")
        void shouldContainMemoryHealthInfo() {
            Map<String, Object> result = indicator.getHealthStatus();

            assertTrue(result.containsKey("memory"));
            @SuppressWarnings("unchecked")
            Map<String, Object> memory = (Map<String, Object>) result.get("memory");
            assertTrue(memory.containsKey("status"));
            assertTrue(memory.containsKey("totalMemoryMB"));
            assertTrue(memory.containsKey("usedMemoryMB"));
            assertTrue(memory.containsKey("usagePercent"));
        }

        @Test
        @DisplayName("应包含storageSync健康信息")
        void shouldContainStorageSyncHealthInfo() {
            Map<String, Object> result = indicator.getHealthStatus();

            assertTrue(result.containsKey("storageSync"));
            @SuppressWarnings("unchecked")
            Map<String, Object> sync = (Map<String, Object>) result.get("storageSync");
            assertEquals("UP", sync.get("status"));
        }

        @Test
        @DisplayName("checkTime 应为有效时间字符串")
        void shouldHaveValidCheckTime() {
            Map<String, Object> result = indicator.getHealthStatus();

            String checkTime = (String) result.get("checkTime");
            assertNotNull(checkTime);
            assertFalse(checkTime.isEmpty());
        }
    }
}
