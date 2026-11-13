package org.unreal.modelrouter.config.core.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigSequenceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigSequenceRepository;

import java.util.Optional;

/**
 * 配置版本号分配器（#1135）。
 *
 * <p>把「先读当前版本、再加一」的读-改-写改成数据库 CAS：读出 {@code config_sequences}
 * 当前值后，只有「表里仍是这个值」时才写入「当前值 + 1」，写入行数为 0 说明被别的副本抢先，
 * 本次重试。两个副本并发分配时拿到的一定是不同的版本号。</p>
 *
 * <p>为什么不用「行锁 + 自增」：依赖数据库的 {@code SELECT ... FOR UPDATE} 锁语义，
 * 在不同引擎上的行为并不一致（真实 H2 并发测试曾出现 80 次分配只出 79 个不同值）。
 * CAS 的原子性由单条 {@code UPDATE ... WHERE next_version = ?} 的写锁保证，可移植且可测。</p>
 *
 * <p>为什么要独立成组件而不是内联在调用方：主配置（{@code model-router-config}）、
 * API Key 配置（{@code security.api-keys}）等写入路径都需要同一套分配语义，内联会导致
 * 每处各写一遍且容易漏掉「首次分配」与「并发首次」两种边界。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@Component
public class ConfigVersionAllocator {

    /** 并发分配的写冲突重试上限：跨副本争抢时需要多试几次，单 JVM 内由本地锁串行化。 */
    private static final int MAX_ATTEMPTS = 8;

    private final ConfigSequenceRepository repository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 单 JVM 内按配置键串行化分配。
     *
     * <p>CAS 能保证跨副本不撞号，但同一进程内的多个线程会在 CAS 上互相抢占、反复重试；
     * 加一层本地锁把同键分配串起来，把「多线程争抢」降成「多副本争抢」——
     * 后者才是真实场景（配置保存是低频操作）。</p>
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> keyLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ConfigVersionAllocator(final ConfigSequenceRepository repository,
                                  final PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // 分配必须独立提交：调用方后续写配置失败时，已发出的版本号不回滚——
        // 版本号只增不复用，留一个空洞比复用同一个号更安全。
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 为指定配置键分配下一个版本号。
     *
     * @param configKey 配置键（{@code config_data.config_key} 的取值空间）
     * @return 新的版本号，从 1 开始严格递增
     * @throws IllegalStateException 多次并发冲突仍无法分配时
     */
    public int allocate(final String configKey) {
        final Object lock = keyLocks.computeIfAbsent(configKey, key -> new Object());
        synchronized (lock) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    final Integer allocated = transactionTemplate.execute(status -> allocateInTransaction(configKey));
                    if (allocated == null) {
                        throw new IllegalStateException("配置版本号分配事务未返回结果: " + configKey);
                    }
                    return allocated;
                } catch (DataIntegrityViolationException e) {
                    // 两个副本同时为同一个新键插入分配器记录，后到者撞主键；
                    // 此时另一个副本已建好行，重试走自增分支即可。
                    log.debug("配置版本号首次分配并发冲突，重试 {}/{}: key={}", attempt, MAX_ATTEMPTS, configKey);
                    backoff(attempt);
                } catch (OptimisticLockingFailureException e) {
                    // CAS 失败：读到的值在写入前被别的副本改动，重新读取后再试。
                    log.debug("配置版本号自增被并发抢占，重试 {}/{}: key={}", attempt, MAX_ATTEMPTS, configKey);
                    backoff(attempt);
                }
            }
        }
        throw new IllegalStateException("配置版本号分配失败（多次并发冲突）: " + configKey);
    }

    /**
     * 冲突后的微退避：让出 CPU，避免多个副本在本地锁释放后立刻重新撞在一起。
     *
     * @param attempt 第几次尝试（越大退避越久）
     */
    private static void backoff(final int attempt) {
        try {
            Thread.sleep(Math.min(20L, 2L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 事务内分配：CAS 自增；没有记录时插入首行。
     *
     * @param configKey 配置键
     * @return 本次分配到的版本号
     * @throws OptimisticLockingFailureException CAS 未命中，调用方重试
     */
    private int allocateInTransaction(final String configKey) {
        final Optional<ConfigSequenceEntity> existing = repository.findById(configKey);
        if (existing.isPresent()) {
            final long current = existing.get().getNextVersion();
            final long next = current + 1;
            if (repository.compareAndSet(configKey, current, next) == 1) {
                return (int) next;
            }
            throw new OptimisticLockingFailureException("配置版本号被并发修改: " + configKey);
        }
        repository.saveAndFlush(ConfigSequenceEntity.builder()
                .configKey(configKey)
                .nextVersion(1L)
                .build());
        return 1;
    }
}
