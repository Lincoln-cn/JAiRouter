package org.unreal.modelrouter.monitor.monitoring.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 活跃会话计数（#947）。
 *
 * <p>与 {@link SseConnectionMetrics} 同一动机：长连接堆了多少是容量饱和时最先要知道的数字，
 * 而 WebSocket 侧此前完全空白。两个监控端点（熔断监控 / 路由监控）各自持有一条长连接流，
 * 断连时的表现（心跳继续发、事件不再推）与「连接真的还在」很难从日志区分，因此需要计数。</p>
 *
 * <p>指标名 {@value #METRIC}，按 {@code endpoint} 打标签。计数语义与 SSE 侧对齐：
 * 每次订阅 +1（会话建立），终止（完成 / 错误 / 取消）+1 相反方向。</p>
 *
 * <p>为什么包装在 {@code handle(session)} 的返回值上、而不是放进 {@code WebSocketHandler} 的装饰器：
 * WebFlux 的 handler 直接由 {@code HandlerMapping} 注册，没有一个统一的装饰点；包装返回的
 * {@code Mono} 恰好就是「这次会话的完整生命周期」，且不改变事件内容与背压。</p>
 *
 * @since 3.6.0
 */
@Component
public class WebSocketSessionMetrics {

    /** 指标名 */
    public static final String METRIC = "jairouter.ws.active.sessions";

    private final MeterRegistry registry;

    /** 端点 → 计数器（首次使用时注册 gauge，重复注册由 Micrometer 幂等去重） */
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * @param registry 指标注册表；为 {@code null} 时退化为不计数（便于不引入 Micrometer 的测试装配）
     */
    @Autowired
    public WebSocketSessionMetrics(@Autowired(required = false) final MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 给一次会话的生命周期包一层连接计数。
     *
     * @param session  原始会话 {@code Mono}（handler 的返回值）
     * @param endpoint 端点标识（作为 gauge 的标签值）
     * @return 计数包装后的 {@code Mono}（不改变内容与背压）
     */
    public Mono<Void> track(final Mono<Void> session, final String endpoint) {
        if (registry == null) {
            return session;
        }
        final AtomicLong counter = counter(endpoint);
        return session
                .doOnSubscribe(subscription -> counter.incrementAndGet())
                .doFinally(signal -> counter.decrementAndGet());
    }

    /**
     * 当前端点的活跃会话数（供内省与测试断言使用）。
     *
     * @param endpoint 端点标识
     * @return 活跃会话数，未知端点返回 0
     */
    public long active(final String endpoint) {
        final AtomicLong counter = counters.get(endpoint);
        return counter == null ? 0L : counter.get();
    }

    private AtomicLong counter(final String endpoint) {
        return counters.computeIfAbsent(endpoint, key -> {
            final AtomicLong counter = new AtomicLong();
            Gauge.builder(METRIC, counter, AtomicLong::doubleValue)
                    .description("当前打开的 WebSocket 会话数")
                    .tag("endpoint", key)
                    .register(registry);
            return counter;
        });
    }
}
