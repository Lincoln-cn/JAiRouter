package org.unreal.modelrouter.persistence.store.persistence.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.persistence.store.persistence.adapter.RateLimiterStatePersistenceAdapter;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流器持久化集成测试
 *
 * v2.4.6: 测试 RateLimitManager 与状态持久化的集成
 *
 * @author JAiRouter Team
 * @since 2.4.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterPersistenceIntegration Tests")
public class RateLimiterPersistenceIntegrationTest {

    @Mock
    private RateLimitManager rateLimitManager;

    @Mock
    private RateLimiterStatePersistenceAdapter persistenceAdapter;

    @Mock
    private ModelRouterProperties properties;

    @InjectMocks
    private RateLimiterPersistenceIntegration integration;

    @BeforeEach
    void setUp() {
        // 默认配置
        Mockito.lenient().when(rateLimitManager.getAllRateLimiterStatus())
                .thenReturn(createMockStatus());

        Mockito.lenient().when(persistenceAdapter.restoreAllRateLimiterStates())
                .thenReturn(Mono.just(Map.of("test-limiter", true)));

        Mockito.lenient().when(persistenceAdapter.syncPendingStates())
                .thenReturn(Mono.just(0));

        Mockito.lenient().when(persistenceAdapter.getStats())
                .thenReturn(Mono.just(Map.of("registeredCount", 5, "pendingSyncCount", 0)));
    }

    /* ===================== 初始化测试 ===================== */

    @Test
    @DisplayName("初始化 - 成功")
    void testInitializationSuccess() {
        integration.onApplicationReady();

        PersistenceIntegrationStatus status = integration.getIntegrationStatus();

        assertTrue(status.isInitialized());
    }

    /* ===================== 恢复测试 ===================== */

    @Test
    @DisplayName("手动恢复 - 成功")
    void testManualRecoverySuccess() {
        integration.onApplicationReady();

        Map<String, Boolean> results = integration.triggerManualRecovery();

        assertNotNull(results);
        assertTrue(results.containsKey("test-limiter"));
    }

    /* ===================== 同步测试 ===================== */

    @Test
    @DisplayName("手动同步 - 成功")
    void testManualSyncSuccess() {
        integration.onApplicationReady();

        int count = integration.triggerManualSync();

        assertEquals(0, count); // 默认 mock 返回 0
    }

    @Test
    @DisplayName("手动同步 - 未初始化时返回 0")
    void testManualSyncNotInitialized() {
        int count = integration.triggerManualSync();

        assertEquals(0, count);
    }

    /* ===================== 状态查询测试 ===================== */

    @Test
    @DisplayName("获取集成状态")
    void testGetIntegrationStatus() {
        integration.onApplicationReady();

        PersistenceIntegrationStatus status = integration.getIntegrationStatus();

        assertNotNull(status);
        assertTrue(status.isInitialized());
        assertEquals(30000, status.getSyncIntervalMs());
        assertEquals(10000, status.getRecoveryTimeoutMs());
    }

    @Test
    @DisplayName("获取集成状态 - 未初始化")
    void testGetIntegrationStatusNotInitialized() {
        PersistenceIntegrationStatus status = integration.getIntegrationStatus();

        assertFalse(status.isInitialized());
    }

    @Test
    @DisplayName("获取集成状态 - 包含统计信息")
    void testGetIntegrationStatusWithStats() {
        integration.onApplicationReady();

        PersistenceIntegrationStatus status = integration.getIntegrationStatus();

        assertEquals(5, status.getRegisteredCount());
        assertEquals(0, status.getPendingSyncCount());
    }

    /* ===================== 辅助方法 ===================== */

    private Map<String, Object> createMockStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("global", "token-bucket(capacity=100,rate=10)");
        status.put("service", Map.of());
        status.put("instance", Map.of());
        status.put("stats", Map.of("serviceRateLimiters", 0, "instanceRateLimiters", 0));
        return status;
    }
}