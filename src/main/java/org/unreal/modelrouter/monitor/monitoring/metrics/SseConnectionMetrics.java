package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 活跃连接计数（#947）。
 *
 * <p>容量饱和时「长连接堆了多少」是最先要知道的数字之一。此前仓库里的自定义指标集中在
 * JWT 清理与存储侧（{@code jwt.cleanup.*} / {@code jwt.storage.*}），连接维度完全空白：
 * {@code performance.md} 给了「活跃连接数 &lt; 1000」的阈值，但程序从未导出这个数字。</p>
 *
 * <p>指标名 {@value #METRIC}，按 {@code endpoint} 打标签（端点数量有限且稳定，不会造成高基数）。</p>
 *
 * <p>计数语义：每次订阅 +1，终止（完成 / 错误 / 取消）+1 相反方向 —— 与「SSE 连接生命周期」对齐。
 * 注意 {@code doOnSubscribe} 在每次订阅时都会触发：若上游 Flux 用 {@code share()} 共享，
 * 每个下游订阅者各算一条连接，这正是「打开几个标签页就有几条连接」的口径。</p>
 *
 * @since 3.5.10
 */
@Component
public class SseConnectionMetrics {

    /** 指标名 */
    public static final String METRIC = "jairouter.sse.active.connections";

    private final MeterRegistry registry;

    /** 端点 → 计数器（首次使用时注册 gauge，重复注册由 Micrometer 幂等去重） */
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * @param registry 指标注册表（Spring 上下文必然存在，Micrometer 在 classpath 上）
     */
    public SseConnectionMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 给 SSE 流包一层连接计数。
     *
     * @param source   原始事件流
     * @param endpoint 端点标识（作为 gauge 的标签值）
     * @param <T>      事件类型
     * @return 计数包装后的流（不改变事件内容与背压）
     */
    public <T> Flux<T> track(final Flux<T> source, final String endpoint) {
        final AtomicLong counter = counter(endpoint);
        return source
                .doOnSubscribe(subscription -> counter.incrementAndGet())
                .doFinally(signal -> counter.decrementAndGet());
    }

    /**
     * 当前端点的活跃连接数（供内省与测试断言使用）。
     *
     * @param endpoint 端点标识
     * @return 活跃连接数，未知端点返回 0
     */
    public long active(final String endpoint) {
        final AtomicLong counter = counters.get(endpoint);
        return counter == null ? 0L : counter.get();
    }

    private AtomicLong counter(final String endpoint) {
        return counters.computeIfAbsent(endpoint, key -> {
            final AtomicLong counter = new AtomicLong();
            Gauge.builder(METRIC, counter, AtomicLong::doubleValue)
                    .description("当前打开的 SSE 连接数")
                    .tag("endpoint", key)
                    .register(registry);
            return counter;
        });
    }
}
