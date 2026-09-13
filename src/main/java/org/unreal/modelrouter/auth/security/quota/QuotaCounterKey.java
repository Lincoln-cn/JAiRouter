package org.unreal.modelrouter.auth.security.quota;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 计数后端键：{@code (维度, 窗口类型, 窗口起点)} 三元组（v3.1 PR-3）。
 *
 * <p>该三元组是账本计数的唯一标识，在本地实现中作为 {@code ConcurrentHashMap} 的键，
 * 在 Redis 实现中作为 {@code Lua} 脚本与 {@code HINCRBY} 的 key 组成部分
 * （见 {@link RedisCounterBackend}）。窗口起点必须稳定可复现，因此由
 * {@link QuotaWindow#windowStart(LocalDateTime)} 计算，不使用“当前时间”本身。</p>
 *
 * @param dimension   维度，{@code null} 时折叠为 {@link QuotaDimension#empty()}
 * @param window      窗口类型，不能为 {@code null}
 * @param windowStart 窗口起点，不能为 {@code null}
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaCounterKey(QuotaDimension dimension, QuotaWindow window, LocalDateTime windowStart) {

    /**
     * 规范化构造：维度 {@code null} 折叠为空维度；窗口与窗口起点为必填。
     */
    public QuotaCounterKey {
        dimension = dimension == null ? QuotaDimension.empty() : dimension;
        window = Objects.requireNonNull(window, "window 不能为 null");
        windowStart = Objects.requireNonNull(windowStart, "windowStart 不能为 null");
    }
}
