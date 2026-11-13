package org.unreal.modelrouter.config.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigSequenceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigSequenceRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ConfigVersionAllocator} 的分支语义。
 *
 * <p>本类用有状态替身覆盖四条分支：首次分配、连续分配、并发首次插入冲突后重试、
 * CAS 被抢占后重试。<b>并发不撞号</b>这件事在替身上测不出来（替身没有真的数据库写锁），
 * 由 {@code ConfigVersionAllocatorIntegrationTest} 在真实 H2 上守。</p>
 *
 * <p>替身模拟的是「读当前值 → CAS 写入」这一事务内语义：
 * {@code findById} 读共享 Map，{@code compareAndSet} 只在 Map 里仍是期望值时写入。</p>
 */
@DisplayName("ConfigVersionAllocator 分配分支（#1135）")
class ConfigVersionAllocatorTest {

    private static final String KEY = "model-router-config";

    private final Map<String, Long> sequences = new ConcurrentHashMap<>();

    private ConfigSequenceRepository repository;
    private ConfigVersionAllocator allocator;
    private AtomicInteger saveCalls;
    private AtomicInteger casCalls;

    /** 第一次 saveAndFlush 模拟撞主键（另一副本刚好抢先插入）。 */
    private boolean collideOnFirstInsert;

    /** 第一次 CAS 模拟被别人抢先（写入行数为 0）。 */
    private boolean stealFirstCas;

    @BeforeEach
    void setUp() {
        sequences.clear();
        saveCalls = new AtomicInteger();
        casCalls = new AtomicInteger();
        collideOnFirstInsert = false;
        stealFirstCas = false;
        repository = mock(ConfigSequenceRepository.class);

        when(repository.findById(anyString())).thenAnswer(inv -> {
            Long value = sequences.get(inv.getArgument(0));
            return value == null ? Optional.empty() : Optional.of(ConfigSequenceEntity.builder()
                    .configKey(inv.getArgument(0))
                    .nextVersion(value)
                    .build());
        });

        when(repository.saveAndFlush(any(ConfigSequenceEntity.class))).thenAnswer(inv -> {
            ConfigSequenceEntity entity = inv.getArgument(0);
            if (saveCalls.incrementAndGet() == 1 && collideOnFirstInsert) {
                // 另一副本抢先建行：它拿到了 1，本副本重试后应拿到 2。
                sequences.put(entity.getConfigKey(), 1L);
                throw new DataIntegrityViolationException("主键冲突（模拟另一副本刚插入）");
            }
            sequences.put(entity.getConfigKey(), entity.getNextVersion());
            return entity;
        });

        when(repository.compareAndSet(anyString(), anyLong(), anyLong())).thenAnswer(inv -> {
            if (casCalls.incrementAndGet() == 1 && stealFirstCas) {
                // 模拟另一个副本在本副本读之后、写之前完成了自增。
                sequences.put(inv.getArgument(0), ((Long) inv.getArgument(1)) + 1);
                return 0;
            }
            String key = inv.getArgument(0);
            Long current = sequences.get(key);
            long expected = inv.getArgument(1);
            long next = inv.getArgument(2);
            if (current == null || current != expected) {
                return 0;
            }
            sequences.put(key, next);
            return 1;
        });

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(inv -> mock(TransactionStatus.class));

        allocator = new ConfigVersionAllocator(repository, transactionManager);
    }

    @Test
    @DisplayName("首次分配：没有分配器记录时返回 1")
    void firstAllocationStartsAtOne() {
        assertEquals(1, allocator.allocate(KEY));
    }

    @Test
    @DisplayName("连续分配：版本号严格 +1，不跳号不复用")
    void sequentialAllocationsAreStrictlyIncreasing() {
        assertEquals(1, allocator.allocate(KEY));
        assertEquals(2, allocator.allocate(KEY));
        assertEquals(3, allocator.allocate(KEY));
    }

    @Test
    @DisplayName("并发首次分配：另一副本抢先插入时重试并拿到下一个号")
    void retriesWhenFirstInsertCollides() {
        collideOnFirstInsert = true;
        assertEquals(2, allocator.allocate(KEY));
    }

    @Test
    @DisplayName("CAS 被抢占：重读后重试，最终拿到下一个号")
    void retriesWhenCompareAndSetLoses() {
        sequences.put(KEY, 4L);
        stealFirstCas = true;
        assertEquals(6, allocator.allocate(KEY));
    }

    @Test
    @DisplayName("不同配置键各自独立计数")
    void differentKeysHaveIndependentSequences() {
        assertEquals(1, allocator.allocate(KEY));
        assertEquals(1, allocator.allocate("security.api-keys"));
        assertEquals(2, allocator.allocate(KEY));
    }
}
