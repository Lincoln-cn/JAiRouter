package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * #121: serviceLimiters 原为 EnumMap，配置重载/管理线程写入时请求线程并发读
 * 会抛 ConcurrentModificationException 或读到不一致状态。
 * 本测试在读路径（tryAcquire / 状态 / 指标）上并发执行 setRateLimiter/removeRateLimiter，
 * 断言无异常且结束后状态一致。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitManager: serviceLimiters 并发读写安全")
class RateLimitManagerConcurrencyTest {

    @Mock
    private ComponentFactory componentFactory;

    @Mock
    private ServiceTypeResolver serviceTypeResolver;

    @Mock
    private ConfigConverterHelper configConverterHelper;

    private ModelRouterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ModelRouterProperties();
        lenient().when(componentFactory.createScopedRateLimiter(any(RateLimitConfig.class)))
                .thenAnswer(inv -> new FixedAllowLimiter(inv.getArgument(0)));
    }

    /** 简单始终放行的限流器（手写 fake） */
    private static final class FixedAllowLimiter implements RateLimiter {
        private final RateLimitConfig config;

        private FixedAllowLimiter(final RateLimitConfig config) {
            this.config = config;
        }

        @Override
        public boolean tryAcquire(final RateLimitContext context) {
            return true;
        }

        @Override
        public RateLimitConfig getConfig() {
            return config;
        }
    }

    private static RateLimitConfig enabledConfig() {
        RateLimitConfig cfg = new RateLimitConfig();
        cfg.setEnabled(true);
        cfg.setAlgorithm("token-bucket");
        cfg.setCapacity(100L);
        cfg.setRate(10L);
        cfg.setScope("service");
        return cfg;
    }

    @Test
    @DisplayName("读路径与 setRateLimiter/removeRateLimiter 并发无异常且终态一致")
    void concurrentReadWhileMutating_shouldNotThrow_andFinalStateConsistent() throws Exception {
        final RateLimitManager manager =
                new RateLimitManager(componentFactory, serviceTypeResolver, configConverterHelper, properties);

        final ModelServiceRegistry.ServiceType[] types = ModelServiceRegistry.ServiceType.values();
        final int readerThreads = 8;
        final int writerThreads = 4;
        final CountDownLatch ready = new CountDownLatch(readerThreads + writerThreads);
        final CountDownLatch writersDone = new CountDownLatch(writerThreads);
        final CountDownLatch readersDone = new CountDownLatch(readerThreads);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        final ExecutorService pool = Executors.newFixedThreadPool(readerThreads + writerThreads);
        try {
            // 读者：热路径 get + forEach 状态/指标，直到写者全部结束
            for (int i = 0; i < readerThreads; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        while (!stop.get()) {
                            for (ModelServiceRegistry.ServiceType t : types) {
                                RateLimitContext ctx = new RateLimitContext(
                                        t, "m", "127.0.0.1", 1, null, null);
                                manager.tryAcquire(ctx);
                            }
                            manager.getAllRateLimiterStatus();
                            manager.getRateLimiterMetrics();
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        readersDone.countDown();
                    }
                });
            }
            // 写者：管理/重载线程改写 serviceLimiters
            for (int i = 0; i < writerThreads; i++) {
                final int seed = i;
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        for (int round = 0; round < 100; round++) {
                            ModelServiceRegistry.ServiceType t = types[(seed + round) % types.length];
                            manager.setRateLimiter(t, enabledConfig());
                            if (round % 3 == 0) {
                                manager.removeRateLimiter(t);
                            }
                            if (round % 50 == 0) {
                                manager.updateConfiguration();
                            }
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        writersDone.countDown();
                    }
                });
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "worker 未就绪");
            assertTrue(writersDone.await(30, TimeUnit.SECONDS), "写者任务超时");
            stop.set(true);
            assertTrue(readersDone.await(10, TimeUnit.SECONDS), "读者任务超时");
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(), "并发读写抛出异常: " + errors);

        // 终态一致：并发结束后串行写入全部类型，map 必须完整可见
        for (ModelServiceRegistry.ServiceType t : types) {
            manager.setRateLimiter(t, enabledConfig());
        }
        Map<?, ?> serviceLimiters = serviceLimiters(manager);
        assertEquals(types.length, serviceLimiters.size(), "终态服务级限流器数量应等于服务类型数");
        for (ModelServiceRegistry.ServiceType t : types) {
            assertNotNull(serviceLimiters.get(t), "终态缺少服务级限流器: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> serviceLimiters(final RateLimitManager manager) {
        try {
            java.lang.reflect.Field field = RateLimitManager.class.getDeclaredField("serviceLimiters");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(manager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
