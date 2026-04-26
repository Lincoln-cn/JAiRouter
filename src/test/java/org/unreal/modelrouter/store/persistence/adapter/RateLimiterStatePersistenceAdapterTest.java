package org.unreal.modelrouter.store.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.ratelimit.impl.TokenBucketRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.SlidingWindowRateLimiter;
import org.unreal.modelrouter.ratelimit.impl.LeakyBucketRateLimiter;
import org.unreal.modelrouter.store.persistence.StatePersistenceService;
import org.unreal.modelrouter.store.persistence.StatePersistenceService.StateType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiterStatePersistenceAdapter 单元测试 - v2.4.5
 *
 * 测试内容：
 * 1. 限流器注册/注销测试
 * 2. 状态保存测试
 * 3. 状态加载测试
 * 4. 状态恢复测试
 * 5. 统计信息测试
 */
@DisplayName("RateLimiterStatePersistenceAdapter v2.4.5 测试")
@ExtendWith(MockitoExtension.class)
class RateLimiterStatePersistenceAdapterTest {

    @Mock
    private StatePersistenceService persistenceService;

    @InjectMocks
    private RateLimiterStatePersistenceAdapter adapter;

    private RateLimitConfig testConfig;
    private TokenBucketRateLimiter tokenBucketLimiter;
    private SlidingWindowRateLimiter slidingWindowLimiter;

    @BeforeEach
    void setUp() {
        testConfig = new RateLimitConfig();
        testConfig.setRate(100);
        testConfig.setCapacity(200);
        testConfig.setScope("service");

        tokenBucketLimiter = new TokenBucketRateLimiter(testConfig);
        slidingWindowLimiter = new SlidingWindowRateLimiter(testConfig);
    }

    @Test
    @DisplayName("测试 1: 注册限流器实例")
    void testRegisterRateLimiter() {
        String limiterId = "test-limiter-1";

        adapter.registerRateLimiter(limiterId, tokenBucketLimiter);

        assertTrue(adapter.getRegisteredLimiterIds().iterator().hasNext());
        assertEquals(limiterId, adapter.getRegisteredLimiterIds().iterator().next());
    }

    @Test
    @DisplayName("测试 2: 注销限流器实例")
    void testUnregisterRateLimiter() {
        String limiterId = "test-limiter-1";

        adapter.registerRateLimiter(limiterId, tokenBucketLimiter);
        adapter.unregisterRateLimiter(limiterId);

        assertFalse(adapter.getRegisteredLimiterIds().iterator().hasNext());
    }

    @Test
    @DisplayName("测试 3: 保存 TokenBucket 限流器状态")
    void testSaveTokenBucketState() {
        String limiterId = "test-limiter-1";
        adapter.registerRateLimiter(limiterId, tokenBucketLimiter);

        when(persistenceService.save(eq(StateType.RATE_LIMITER), eq(limiterId), any(Map.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(adapter.saveRateLimiterState(limiterId, tokenBucketLimiter))
                .expectNext(true)
                .verifyComplete();

        verify(persistenceService).save(eq(StateType.RATE_LIMITER), eq(limiterId), any(Map.class));
    }

    @Test
    @DisplayName("测试 4: 保存失败时记录待同步")
    void testSaveStateFailure() {
        String limiterId = "test-limiter-1";
        adapter.registerRateLimiter(limiterId, tokenBucketLimiter);

        when(persistenceService.save(eq(StateType.RATE_LIMITER), eq(limiterId), any(Map.class)))
                .thenReturn(Mono.error(new RuntimeException("Save failed")));

        // 保存失败时会触发错误，但适配器会捕获并返回 false
        StepVerifier.create(adapter.saveRateLimiterState(limiterId, tokenBucketLimiter))
                .expectNext(false)  // 返回 false 表示保存失败
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 5: 加载限流器状态")
    void testLoadRateLimiterState() {
        String limiterId = "test-limiter-1";
        Map<String, Object> savedState = new HashMap<>();
        savedState.put("algorithm", "token_bucket");
        savedState.put("requestsPerSecond", 100);
        savedState.put("capacity", 200);

        when(persistenceService.load(StateType.RATE_LIMITER, limiterId))
                .thenReturn(Mono.just(savedState));

        StepVerifier.create(adapter.loadRateLimiterState(limiterId))
                .expectNext(savedState)
                .verifyComplete();

        verify(persistenceService).load(StateType.RATE_LIMITER, limiterId);
    }

    @Test
    @DisplayName("测试 6: 加载不存在状态返回空Map")
    void testLoadNonExistentState() {
        String limiterId = "test-limiter-1";

        when(persistenceService.load(StateType.RATE_LIMITER, limiterId))
                .thenReturn(Mono.just(Collections.emptyMap()));

        StepVerifier.create(adapter.loadRateLimiterState(limiterId))
                .expectNext(Collections.emptyMap())
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 7: 恢复已注册限流器状态")
    void testRestoreRegisteredRateLimiterState() {
        String limiterId = "test-limiter-1";
        adapter.registerRateLimiter(limiterId, tokenBucketLimiter);

        Map<String, Object> savedState = new HashMap<>();
        savedState.put("algorithm", "token_bucket");
        savedState.put("requestsPerSecond", 100);

        when(persistenceService.load(StateType.RATE_LIMITER, limiterId))
                .thenReturn(Mono.just(savedState));

        StepVerifier.create(adapter.restoreRateLimiterState(limiterId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 8: 恢复未注册限流器返回false")
    void testRestoreUnregisteredRateLimiter() {
        String limiterId = "unknown-limiter";

        StepVerifier.create(adapter.restoreRateLimiterState(limiterId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 9: 删除限流器状态")
    void testDeleteRateLimiterState() {
        String limiterId = "test-limiter-1";

        when(persistenceService.delete(StateType.RATE_LIMITER, limiterId))
                .thenReturn(Mono.just(true));

        StepVerifier.create(adapter.deleteRateLimiterState(limiterId))
                .expectNext(true)
                .verifyComplete();

        verify(persistenceService).delete(StateType.RATE_LIMITER, limiterId);
    }

    @Test
    @DisplayName("测试 10: 获取统计信息")
    void testGetStats() {
        adapter.registerRateLimiter("limiter-1", tokenBucketLimiter);
        adapter.registerRateLimiter("limiter-2", slidingWindowLimiter);

        StepVerifier.create(adapter.getStats())
                .assertNext(stats -> {
                    assertEquals(2, stats.get("registeredCount"));
                    assertEquals(0, stats.get("pendingSyncCount"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 11: 同步待同步状态（无待同步时返回0）")
    void testSyncPendingStatesNoPending() {
        adapter.registerRateLimiter("limiter-1", tokenBucketLimiter);

        // 同步会触发保存，但没有待同步状态时返回0
        StepVerifier.create(adapter.syncPendingStates())
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("测试 12: 提取 SlidingWindow 限流器状态")
    void testExtractSlidingWindowState() {
        String limiterId = "sliding-window-limiter";
        adapter.registerRateLimiter(limiterId, slidingWindowLimiter);

        when(persistenceService.save(eq(StateType.RATE_LIMITER), eq(limiterId), any(Map.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(adapter.saveRateLimiterState(limiterId, slidingWindowLimiter))
                .expectNext(true)
                .verifyComplete();

        verify(persistenceService).save(eq(StateType.RATE_LIMITER), eq(limiterId), any(Map.class));
    }
}