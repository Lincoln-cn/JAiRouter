package org.unreal.modelrouter.config.core.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.ModelRouterApplication;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigSequenceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigVersionAllocator} 在真实 H2 上的并发守护（#1135）。
 *
 * <p><b>为什么必须用真库</b>：版本号不撞号的保证完全来自数据库对 {@code config_sequences}
 * 单行的 {@code UPDATE ... SET next_version = next_version + 1} 行锁；替身没有行锁，
 * 用 Mockito 测「并发不重复」只会测到替身自己的原子性。</p>
 *
 * <p>本类关掉测试事务（{@code NOT_SUPPORTED}），让分配器的 {@code REQUIRES_NEW} 真的
 * 各自提交；否则所有线程会在同一个测试事务里排队，测不出并发。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = ModelRouterApplication.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ConfigVersionAllocator 真实 H2 并发（#1135）")
class ConfigVersionAllocatorIntegrationTest {

    private static final String KEY = "model-router-config";

    @Autowired
    private ConfigSequenceRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ConfigVersionAllocator allocator;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        allocator = new ConfigVersionAllocator(repository, transactionManager);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("真实库上首次分配从 1 开始")
    void firstAllocationOnRealDatabaseStartsAtOne() {
        assertEquals(1, allocator.allocate(KEY));
        assertEquals(1L, repository.findById(KEY).orElseThrow().getNextVersion());
    }

    @Test
    @DisplayName("真实库上并发分配：版本号互不重复且恰好覆盖 1..N")
    void concurrentAllocationsNeverCollideOnRealDatabase() throws Exception {
        final int threads = 8;
        final int perThread = 10;
        final int total = threads * perThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> allocated = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int j = 0; j < perThread; j++) {
                        try {
                            allocated.add(allocator.allocate(KEY));
                        } catch (Throwable t) {
                            errors.add(t);
                        }
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发分配在 60 秒内未完成");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(), "分配抛出异常: " + errors);
        assertEquals(total, allocated.size(), "版本号出现重复：" + allocated.size() + " 个不重复值 / 期望 " + total);
        Set<Integer> expected = new HashSet<>();
        for (int i = 1; i <= total; i++) {
            expected.add(i);
        }
        assertEquals(expected, allocated, "版本号不是从 1 开始连续分配");
    }
}
