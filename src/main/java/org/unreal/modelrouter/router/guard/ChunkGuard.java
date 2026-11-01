package org.unreal.modelrouter.router.guard;

/**
 * 流式护栏链上的一个算子。
 *
 * <p>实现必须**同步且无阻塞**：它在 Netty event loop 上随每个 chunk 执行一次，
 * 任何 IO 或阻塞调用都会直接拖垮整条流。需要外部依赖的判定（如分类器）不在本接口的射程内。</p>
 *
 * <p>实现也必须**无跨 chunk 状态**：同一实例服务所有并发流，状态只能挂在返回值上。
 * 因此「敏感串被切成两半」这类场景当前不在覆盖范围内（见 {@code GuardChain} 的说明）。</p>
 *
 * @since 3.6.0
 */
public interface ChunkGuard {

    /**
     * 判定一个 chunk。
     *
     * @param chunk 当前 chunk 的文本（SSE data 串，通常是 JSON）
     * @return 处置结果；不得返回 {@code null}
     */
    GuardDecision evaluate(String chunk);

    /**
     * 规则名，用于指标与审计。
     *
     * @return 稳定的短名
     */
    String name();
}
