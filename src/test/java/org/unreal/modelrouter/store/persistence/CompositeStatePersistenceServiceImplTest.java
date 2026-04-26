package org.unreal.modelrouter.store.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 组合状态持久化服务测试
 *
 * v2.4.6: 测试三层退坡策略、健康检查、恢复同步
 *
 * @author JAiRouter Team
 * @since 2.4.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeStatePersistenceService Tests")
public class CompositeStatePersistenceServiceImplTest {

    @Mock
    private RedisStatePersistenceServiceImpl redisService;

    @Mock
    private H2StatePersistenceServiceImpl h2Service;

    @Mock
    private FileStatePersistenceServiceImpl fileService;

    @InjectMocks
    private CompositeStatePersistenceServiceImpl compositeService;

    private StatePersistenceService.StateType stateType = StatePersistenceService.StateType.CIRCUIT_BREAKER;

    @BeforeEach
    void setUp() {
        // 默认配置：Redis(优先级1) -> H2(优先级2) -> File(优先级3)
        Mockito.lenient().when(redisService.getTierName()).thenReturn("redis");
        Mockito.lenient().when(redisService.getTierPriority()).thenReturn(1);
        Mockito.lenient().when(redisService.isHealthy()).thenReturn(Mono.just(false)); // Redis 不可用

        Mockito.lenient().when(h2Service.getTierName()).thenReturn("h2");
        Mockito.lenient().when(h2Service.getTierPriority()).thenReturn(2);
        Mockito.lenient().when(h2Service.isHealthy()).thenReturn(Mono.just(true)); // H2 可用

        Mockito.lenient().when(fileService.getTierName()).thenReturn("file");
        Mockito.lenient().when(fileService.getTierPriority()).thenReturn(3);
        Mockito.lenient().when(fileService.isHealthy()).thenReturn(Mono.just(true)); // File 可用
    }

    /* ===================== 退坡策略测试 ===================== */

    @Test
    @DisplayName("退坡策略 - Redis失败自动切换H2")
    void testFallbackFromRedisToH2() {
        Map<String, Object> stateData = createTestState("test-key");

        // Redis 不可用
        Mockito.when(redisService.isHealthy()).thenReturn(Mono.just(false));

        // H2 可用并成功
        Mockito.when(h2Service.save(eq(stateType), eq("test-key"), any()))
                .thenReturn(Mono.just(true));

        // 初始化服务 - 会选择 H2 作为活跃层
        simulateInitialization();

        // 执行保存 - 使用 H2
        StepVerifier.create(compositeService.save(stateType, "test-key", stateData))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("退坡策略 - 全部失败使用File兜底")
    void testFallbackToFile() {
        // Redis 和 H2 都不可用，只有 File 可用
        Mockito.when(redisService.isHealthy()).thenReturn(Mono.just(false));
        Mockito.when(h2Service.isHealthy()).thenReturn(Mono.just(false));
        Mockito.when(fileService.isHealthy()).thenReturn(Mono.just(true));

        simulateInitialization();

        // 当 Redis 和 H2 不可用时，应该选择 File 作为活跃层
        assertEquals("file", compositeService.getActiveTierName());
    }

    /* ===================== 健康检查测试 ===================== */

    @Test
    @DisplayName("健康检查 - 返回活跃服务状态")
    void testIsHealthy() {
        simulateInitialization();

        Mockito.when(h2Service.isHealthy()).thenReturn(Mono.just(true));

        StepVerifier.create(compositeService.isHealthy())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("健康检查 - 获取所有层状态")
    void testGetAllTierStatus() {
        simulateInitialization();

        Map<String, Boolean> status = compositeService.getAllTierStatus();

        assertNotNull(status);
        assertTrue(status.containsKey("redis"));
        assertTrue(status.containsKey("h2"));
        assertTrue(status.containsKey("file"));
    }

    @Test
    @DisplayName("健康检查 - 刷新状态")
    void testRefreshHealthStatus() {
        // 初始状态：Redis 不可用
        Mockito.when(redisService.isHealthy()).thenReturn(Mono.just(false));
        simulateInitialization();

        assertEquals("h2", compositeService.getActiveTierName());

        // Redis 变为可用
        Mockito.when(redisService.isHealthy()).thenReturn(Mono.just(true));

        compositeService.refreshHealthStatus();

        // 现在应该切换到 Redis
        assertEquals("redis", compositeService.getActiveTierName());
    }

    /* ===================== 层切换测试 ===================== */

    @Test
    @DisplayName("手动切换层 - 切换到可用层")
    void testSwitchToAvailableTier() {
        simulateInitialization();

        // H2 是可用的
        Mockito.when(h2Service.isHealthy()).thenReturn(Mono.just(true));

        boolean result = compositeService.switchTier("h2");

        assertTrue(result);
        assertEquals("h2", compositeService.getActiveTierName());
    }

    @Test
    @DisplayName("手动切换层 - 切换到不可用层失败")
    void testSwitchToUnavailableTier() {
        simulateInitialization();

        // Redis 不可用
        Mockito.when(redisService.isHealthy()).thenReturn(Mono.just(false));

        boolean result = compositeService.switchTier("redis");

        assertFalse(result);
    }

    @Test
    @DisplayName("手动切换层 - 切换到未知层失败")
    void testSwitchToUnknownTier() {
        simulateInitialization();

        boolean result = compositeService.switchTier("unknown");

        assertFalse(result);
    }

    /* ===================== CRUD 操作测试 ===================== */

    @Test
    @DisplayName("保存操作 - 成功")
    void testSaveSuccess() {
        Map<String, Object> stateData = createTestState("test-key");
        simulateInitialization();

        Mockito.when(h2Service.save(eq(stateType), eq("test-key"), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(compositeService.save(stateType, "test-key", stateData))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("加载操作 - 成功")
    void testLoadSuccess() {
        Map<String, Object> stateData = createTestState("test-key");
        simulateInitialization();

        Mockito.when(h2Service.load(eq(stateType), eq("test-key")))
                .thenReturn(Mono.just(stateData));

        StepVerifier.create(compositeService.load(stateType, "test-key"))
                .expectNextMatches(data -> data.containsKey("stateId"))
                .verifyComplete();
    }

    @Test
    @DisplayName("删除操作 - 成功")
    void testDeleteSuccess() {
        simulateInitialization();

        Mockito.when(h2Service.delete(eq(stateType), eq("test-key")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(compositeService.delete(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("存在检查 - 成功")
    void testExistsSuccess() {
        simulateInitialization();

        Mockito.when(h2Service.exists(eq(stateType), eq("test-key")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(compositeService.exists(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 批量操作测试 ===================== */

    @Test
    @DisplayName("批量保存 - 成功")
    void testSaveBatchSuccess() {
        Map<String, Map<String, Object>> batchData = new HashMap<>();
        batchData.put("key-1", createTestState("key-1"));
        batchData.put("key-2", createTestState("key-2"));

        simulateInitialization();

        Mockito.when(h2Service.saveBatch(eq(stateType), any()))
                .thenReturn(Mono.just(2));

        StepVerifier.create(compositeService.saveBatch(stateType, batchData))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("批量加载 - 成功")
    void testLoadBatchSuccess() {
        List<String> keys = List.of("key-1", "key-2");
        Map<String, Map<String, Object>> batchResult = new HashMap<>();
        batchResult.put("key-1", createTestState("key-1"));
        batchResult.put("key-2", createTestState("key-2"));

        simulateInitialization();

        Mockito.when(h2Service.loadBatch(eq(stateType), any()))
                .thenReturn(Mono.just(batchResult));

        StepVerifier.create(compositeService.loadBatch(stateType, keys))
                .expectNextMatches(result -> result.size() == 2)
                .verifyComplete();
    }

    @Test
    @DisplayName("清除所有 - 成功")
    void testClearAllSuccess() {
        simulateInitialization();

        Mockito.when(h2Service.clearAll(eq(stateType)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(compositeService.clearAll(stateType))
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 状态查询测试 ===================== */

    @Test
    @DisplayName("获取活跃层名称")
    void testGetActiveTierName() {
        simulateInitialization();

        String activeTier = compositeService.getActiveTierName();

        // 默认应该选择 H2（因为 Redis 不可用）
        assertEquals("h2", activeTier);
    }

    @Test
    @DisplayName("获取活跃层优先级")
    void testGetActiveTierPriority() {
        simulateInitialization();

        int priority = compositeService.getActiveTierPriority();

        assertEquals(2, priority); // H2 的优先级
    }

    /* ===================== 辅助方法 ===================== */

    private void simulateInitialization() {
        // 模拟 ApplicationReadyEvent 触发的初始化
        compositeService.onApplicationReady();
    }

    private Map<String, Object> createTestState(String stateId) {
        Map<String, Object> state = new HashMap<>();
        state.put("stateId", stateId);
        state.put("timestamp", System.currentTimeMillis());
        state.put("status", "ACTIVE");
        return state;
    }
}