package org.unreal.modelrouter.router.guard;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式护栏的装配与出口。
 *
 * <p><b>失败语义 = 降级不阻断</b>：护栏内部任何异常都只记一条 warn，然后按「放行」处理。
 * 理由与配额/缓存一致 —— 判定器坏掉不该让用户的流断掉；出事的时候宁可放过，也不能把网关变成故障源。</p>
 *
 * <p><b>默认关闭</b>：{@code jairouter.guard.enabled=false} 时 {@link #evaluate(String)} 直接返回放行，
 * 连链都不碰，热路径上只有一次布尔判断。</p>
 *
 * <p>配置变更后需要重启（或重放 {@link #rebuild()}）：本服务在启动时把规则编译成不可变链。</p>
 *
 * @since 3.6.0
 */
@Service
public class StreamGuardService {

    private static final Logger log = LoggerFactory.getLogger(StreamGuardService.class);

    private final GuardProperties properties;

    private final MeterRegistry meterRegistry;

    private volatile GuardChain chain = GuardChain.EMPTY;

    /**
     * 构造。
     *
     * @param properties    护栏配置
     * @param meterRegistry 指标注册表，可为 {@code null}（此时只计数不导出）
     */
    @Autowired
    public StreamGuardService(final GuardProperties properties,
                              @Autowired(required = false) final MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 启动时编译规则链。
     */
    @PostConstruct
    public void rebuild() {
        final List<ChunkGuard> guards = new ArrayList<>();
        if (properties.getRedact().isEnabled() && !properties.getRedact().getPatterns().isEmpty()) {
            final RegexRedactGuard guard = new RegexRedactGuard(properties.getRedact().getPatterns());
            guards.add(guard);
        }
        if (properties.getBlock().isEnabled() && !properties.getBlock().getPatterns().isEmpty()) {
            final RegexAbortGuard guard = new RegexAbortGuard(properties.getBlock().getPatterns());
            guards.add(guard);
        }
        this.chain = guards.isEmpty() ? GuardChain.EMPTY : new GuardChain(guards);
        if (properties.isEnabled()) {
            log.info("流式护栏已启用：{} 个算子（脱敏 {} 条规则 / 阻断 {} 条规则）",
                    chain.size(),
                    properties.getRedact().getPatterns().size(),
                    properties.getBlock().getPatterns().size());
        }
    }

    /**
     * 护栏是否生效。
     *
     * @return 启用且链非空时为 true
     */
    public boolean enabled() {
        return properties.isEnabled() && !chain.isEmpty();
    }

    /**
     * 判定一个 chunk。
     *
     * @param chunk 当前 chunk 文本
     * @return 处置结果；任何内部异常都降级为放行
     */
    public GuardDecision evaluate(final String chunk) {
        if (!enabled()) {
            return GuardDecision.allow(chunk);
        }
        try {
            final GuardDecision decision = chain.evaluate(chunk);
            record(decision);
            return decision;
        } catch (final RuntimeException e) {
            log.warn("流式护栏判定失败，本块按放行处理：{}", e.getMessage());
            return GuardDecision.allow(chunk);
        }
    }

    /**
     * 记录一次判定（仅命中的动作）。
     *
     * @param decision 处置结果
     */
    private void record(final GuardDecision decision) {
        if (meterRegistry == null || decision.action() == GuardAction.ALLOW) {
            return;
        }
        final String policy = decision.policy() == null ? "unknown" : decision.policy();
        Counter.builder("jairouter.guard.decisions")
                .description("流式护栏命中的处置次数")
                .tag("action", decision.action().name())
                .tag("policy", policy)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 供测试与配置刷新使用的链快照。
     *
     * @return 当前链
     */
    GuardChain chain() {
        return chain;
    }
}
