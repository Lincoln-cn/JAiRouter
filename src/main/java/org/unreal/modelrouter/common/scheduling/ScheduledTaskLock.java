package org.unreal.modelrouter.common.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.util.InstanceIdentity;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务的**跨副本排他锁**。
 *
 * <p>多副本部署下，{@code @Scheduled} 任务会在每个副本各执行一遍。对于删除过期数据这类
 * **幂等**任务，重复执行无害；但对于**副作用可被外部观测**的任务（发告警、出日报、
 * 落归档文件、发轮换通知），重复执行会产生重复告警与重复产物。本组件为后者提供排他。</p>
 *
 * <h2>设计取舍</h2>
 * <ul>
 *   <li><b>不释放锁，只靠 TTL 过期。</b>任务按固定周期触发，持锁到租约到期最多让下一个周期被跳过，
 *       这是无害的；而「执行完主动删锁」需要 compare-and-delete 才能避免误删他人的锁，
 *       复杂度不值得。调用方只需传入触发周期，租约由本组件按周期推导（见 {@code leaseOf}）。</li>
 *   <li><b>Redis 不可用时 fail-open（返回可执行）。</b>这些任务多为清理与巡检，若因 Redis 不可用
 *       而长期不执行，后果（数据无限增长、无人巡检）比重复执行更严重；且这正是当前单实例下的行为。
 *       降级时会告警，便于运维发现。</li>
 *   <li><b>未配置 Redis 时等价于单实例</b>——直接放行，行为与引入本组件前完全一致。</li>
 * </ul>
 *
 * <p>开关：{@code jairouter.scheduling.distributed-lock.enabled}（默认 {@code true}），
 * 置 {@code false} 可完全回退到「每个副本各跑一遍」的旧行为。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@Slf4j
@Component
public class ScheduledTaskLock {

    /** 锁 key 前缀。 */
    private static final String KEY_PREFIX = "jairouter:sched:lock:";

    /** 取锁调用的阻塞上限，避免 Redis 抖动拖住调度线程。 */
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(2);

    private final ObjectProvider<ReactiveStringRedisTemplate> redisProvider;

    private final boolean enabled;

    /** 降级告警只打一次，避免每分钟刷屏。 */
    private final AtomicBoolean degradedWarned = new AtomicBoolean();

    public ScheduledTaskLock(final ObjectProvider<ReactiveStringRedisTemplate> redisProvider,
                             @Value("${jairouter.scheduling.distributed-lock.enabled:true}")
                             final boolean enabled) {
        this.redisProvider = redisProvider;
        this.enabled = enabled;
    }

    /**
     * 尝试取得该任务名的排他锁。
     *
     * @param taskName 任务名（同名的任务共用一把锁）
     * @param period   该任务的**触发周期**；实际租约取 {@code period} 的 90%
     * @return {@code true} 表示可以执行；{@code false} 表示其他副本正在执行，本次应跳过并直接 return
     */
    public boolean tryAcquire(final String taskName, final Duration period) {
        if (!enabled) {
            return true;
        }
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            warnDegradedOnce("未配置 Redis 客户端");
            return true;
        }
        Duration lease = leaseOf(period);
        try {
            boolean acquired = Boolean.TRUE.equals(
                    redis.opsForValue()
                            .setIfAbsent(KEY_PREFIX + taskName, InstanceIdentity.id(), lease)
                            .blockOptional(ACQUIRE_TIMEOUT)
                            .orElse(null));
            if (!acquired) {
                log.debug("定时任务 {} 由其他副本执行中，本副本跳过", taskName);
            }
            return acquired;
        } catch (Exception e) {
            // fail-open：宁可重复执行，也不要因 Redis 故障让清理/巡检长期停摆
            warnDegradedOnce("取锁异常（" + e.getClass().getSimpleName() + "）");
            return true;
        }
    }

    /**
     * 由触发周期推导租约：取周期的 90%（至少 1 秒）。
     *
     * <p>不能等于周期——各副本的触发时刻几乎相同，若租约恰好等于周期，下一个周期取锁时会
     * 与本次租约到期赛跑；也不能太短，否则同一周期内其他副本会重新取到锁。</p>
     */
    private static Duration leaseOf(final Duration period) {
        Duration lease = period.multipliedBy(9).dividedBy(10);
        return lease.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : lease;
    }

    private void warnDegradedOnce(final String reason) {
        if (degradedWarned.compareAndSet(false, true)) {
            log.warn("定时任务排他锁已降级为单副本语义：{}。多副本部署下，"
                    + "带外部副作用的任务（告警/日报/归档/轮换通知）可能被每个副本各执行一次。"
                    + "详见 docs 的「多副本」说明。", reason);
        } else {
            log.debug("定时任务排他锁降级（已告警过）：{}", reason);
        }
    }
}
