package org.unreal.modelrouter.router.guard;

import java.util.List;

/**
 * 有序的护栏链：按登记顺序执行，命中阻断即短路。
 *
 * <p><b>当前覆盖边界（写在这里，免得后来者以为已经覆盖）</b>：链按 chunk 判定，
 * 每个算子无跨 chunk 状态。因此一段敏感串如果恰好被上游切在两个 chunk 之间
 * （例如手机号前 6 位在上一块、后 5 位在下一块），当前实现<b>检测不到</b>。
 * 补齐它需要「尾部 carry 缓冲 + 流结束时 flush」，属于下一步的迭代，不在本版射程内。</p>
 *
 * @since 3.6.0
 */
public final class GuardChain {

    /** 空链：恒定放行。 */
    public static final GuardChain EMPTY = new GuardChain(List.of());

    private final List<ChunkGuard> guards;

    /**
     * 构造链。
     *
     * @param guards 算子列表，按执行顺序；不得为 {@code null}
     */
    public GuardChain(final List<ChunkGuard> guards) {
        this.guards = List.copyOf(guards);
    }

    /**
     * 依次执行链上的算子。
     *
     * @param chunk 当前 chunk 文本
     * @return 最终处置结果
     */
    public GuardDecision evaluate(final String chunk) {
        if (chunk == null || guards.isEmpty()) {
            return GuardDecision.allow(chunk);
        }
        String current = chunk;
        String lastPolicy = null;
        String lastReason = null;
        boolean redacted = false;
        for (final ChunkGuard guard : guards) {
            final GuardDecision decision = guard.evaluate(current);
            if (decision == null) {
                continue;
            }
            if (decision.aborting()) {
                return decision;
            }
            if (decision.redacted()) {
                current = decision.output();
                lastPolicy = decision.policy();
                lastReason = decision.reason();
                redacted = true;
            }
        }
        return redacted ? GuardDecision.redact(lastPolicy, current, lastReason) : GuardDecision.allow(chunk);
    }

    /**
     * 链上是否有算子。
     *
     * @return 无算子时为 true
     */
    public boolean isEmpty() {
        return guards.isEmpty();
    }

    /**
     * 链上算子数量（仅用于日志与测试断言）。
     *
     * @return 算子个数
     */
    public int size() {
        return guards.size();
    }
}
