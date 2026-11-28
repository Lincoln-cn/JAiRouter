package org.unreal.modelrouter.persistence.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code saveConfig} 首插竞态的守护（#1249）.
 *
 * <p>缺陷史：{@code saveConfig} 先查 {@code is_latest=true} 的行，查不到就以
 * {@code version=1} 建行 —— 两个请求同时走到「查不到」分支会各写一行，库里出现重复的
 * {@code (config_key, version)}；之后每次启动都在 {@code VersionSyncService} 里按单行查询
 * 抛 {@code NonUniqueResultException}，进程起不来，只能人工删数据。</p>
 *
 * <p>假仓库实现 {@code (config_key, version)} 唯一（第二次写同 key+version 抛
 * {@code DataIntegrityViolationException}），用来模拟 V21 迁移建的
 * {@code uk_config_data_key_version}。断言：同一实例上并发保存只留下一行；
 * 两个实例（模拟两个副本）并发时由数据库约束兜底 —— 一个成功、一个明确报错，
 * 而不是安静地写坏数据。</p>
 */
@DisplayName("JpaStoreManager saveConfig 首插竞态")
class JpaStoreManagerSaveRaceTest {

    private static final String KEY = "pool_definitions";

    private ConfigRepository repository;
    private final List<ConfigEntity> rows = new ArrayList<>();
    private long nextId = 1;

    /**
     * 模拟「两个副本在各自的事务快照里都看不到对方」：开启后，两方都进入
     * {@code findFirstByConfigKeyAndIsLatestTrue} 且表为空时先互相等待，
     * 保证双方都拿到「查不到」的结论再往下走。真实跨副本场景正是这样撞上的。
     */
    private volatile boolean staleRead = false;

    @BeforeEach
    void setUp() {
        rows.clear();
        nextId = 1;
        repository = mock(ConfigRepository.class);

        when(repository.findFirstByConfigKeyAndIsLatestTrue(anyString()))
                .thenAnswer(inv -> {
                    if (staleRead) {
                        // 模拟两个副本各自的快照：谁都看不见对方尚未提交的插入。
                        // 早前用「仅在 rows 为空时同步等待」的写法是 flaky 的 —— 一方先插入后，
                        // 另一方到达时 rows 已非空、不再等待，于是读到已提交行走更新分支，
                        // 两边都成功、断言「必有一方失败」落空。
                        return java.util.Optional.<ConfigEntity>empty();
                    }
                    return rows.stream()
                            .filter(row -> KEY.equals(row.getConfigKey())
                                    && Boolean.TRUE.equals(row.getIsLatest()))
                            .findFirst();
                });

        when(repository.save(any(ConfigEntity.class))).thenAnswer(inv -> {
            ConfigEntity entity = inv.getArgument(0);
            synchronized (rows) {
                if (entity.getId() == null) {
                    boolean duplicated = rows.stream().anyMatch(row ->
                            row.getConfigKey().equals(entity.getConfigKey())
                                    && row.getVersion().equals(entity.getVersion()));
                    if (duplicated) {
                        throw new DataIntegrityViolationException(
                                "uk_config_data_key_version: duplicate (" + entity.getConfigKey()
                                        + ", " + entity.getVersion() + ")");
                    }
                    entity.setId(nextId++);
                    rows.add(entity);
                }
                return entity;
            }
        });
    }

    private Map<String, Object> payload(final String marker) {
        return Map.of("marker", marker);
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "并发未能按时起跑");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("同一实例并发保存只落一行（按 key 串行化 check-then-act）")
    void concurrentSavesOnSameInstanceLeaveOneRow() throws Exception {
        JpaStoreManager storeManager = new JpaStoreManager(repository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable task = () -> {
            ready.countDown();
            await(fire);
            try {
                storeManager.saveConfig(KEY, payload("x"));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        Thread a = new Thread(task, "save-a");
        Thread b = new Thread(task, "save-b");
        a.start();
        b.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        fire.countDown();
        a.join(5000);
        b.join(5000);

        assertEquals(null, failure.get(), "同一实例的并发保存不应抛唯一约束冲突");
        assertEquals(1, rows.size(), "同一 (config_key, version) 只能落一行");
    }

    @Test
    @DisplayName("两个实例（模拟两副本）并发时由数据库唯一约束兜底：一个成功、一个明确报错")
    void concurrentSavesAcrossInstancesFailLoudly() throws Exception {
        staleRead = true;
        JpaStoreManager replicaA = new JpaStoreManager(repository);
        JpaStoreManager replicaB = new JpaStoreManager(repository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicReference<Throwable> first = new AtomicReference<>();
        AtomicReference<Throwable> second = new AtomicReference<>();

        Runnable taskA = () -> {
            ready.countDown();
            await(fire);
            try {
                replicaA.saveConfig(KEY, payload("a"));
            } catch (Throwable t) {
                first.compareAndSet(null, t);
            }
        };
        Runnable taskB = () -> {
            ready.countDown();
            await(fire);
            try {
                replicaB.saveConfig(KEY, payload("b"));
            } catch (Throwable t) {
                second.compareAndSet(null, t);
            }
        };

        Thread a = new Thread(taskA, "replica-a");
        Thread b = new Thread(taskB, "replica-b");
        a.start();
        b.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        fire.countDown();
        a.join(5000);
        b.join(5000);

        int failures = (first.get() != null ? 1 : 0) + (second.get() != null ? 1 : 0);
        assertEquals(1, failures,
                "跨副本并发时数据库约束必须让其中一方失败，而不是两边都写入成功");
        assertEquals(1, rows.size(), "唯一约束生效后库里只能留一行");
        Throwable thrown = first.get() != null ? first.get() : second.get();
        assertTrue(thrown instanceof DataIntegrityViolationException,
                "失败方应拿到唯一约束冲突（可被上层识别为「重试或更新」），实际是 " + thrown);
    }
}
