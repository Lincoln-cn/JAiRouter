package org.unreal.modelrouter.auth.security.quota;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 配额计数后端抽象（v3.1 PR-3）。
 *
 * <p>把“计数存放在哪里”从账本语义中剥离出来，使同一套账本逻辑既能跑在单进程内存
 * （{@link LocalCounterBackend}），也能跑在 Redis 集群共享计数（{@link RedisCounterBackend}）。</p>
 *
 * <p><b>计数模型</b>：每个 {@link QuotaCounterKey} 对应一个“桶”，桶内两个字段：</p>
 * <ul>
 *   <li>{@code requests}：窗口内请求数（预留 +1、结算失败 -1）；</li>
 *   <li>{@code tokens}：窗口内 token 数（预留 +预估、结算按实际值冲正）。</li>
 * </ul>
 *
 * <p><b>返回值约定</b>：{@link #increment} / {@link #incrementIfPresent} 返回累加后的
 * {@code [requests, tokens]} 二元数组；{@link #read} 返回 {@link Optional}，桶不存在时为
 * {@link Optional#empty()}（“无计数”与“计数为 0”需要区分）；{@link #incrementIfPresent}
 * 在桶不存在时返回空 {@link Mono}（对应 PR-1 “结算不为新槽位建档”的既有语义）。</p>
 *
 * <p><b>异常语义</b>：后端实现自身不吞异常——Redis 不可用 / 超时 / 脚本错误时以
 * {@link Mono#error} 形式传播，由 {@link QuotaLedgerService} 统一按
 * {@code jairouter.quota.distributed.degrade-to-local} 降级并遵循 {@code fail-open} 策略，
 * 因此调用方（请求链路）不会看到 5xx。</p>
 *
 * <p>所有方法均为非阻塞 Reactive 签名；{@link QuotaLedgerService} 因历史原因（PR-2 已钉死
 * reserve/settle 同步签名）在调用处带超时阻塞等待，见该类的设计说明。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
public interface QuotaCounterBackend {

    /**
     * 后端名称（用于日志与指标标记后端类型）。
     *
     * @return 后端名称，如 {@code local} / {@code redis}
     */
    String name();

    /**
     * 原子累加一个桶的计数（桶不存在时创建）。
     *
     * @param key      计数键
     * @param requests 请求数增量，可为负
     * @param tokens   token 增量，可为负
     * @return 累加后的 {@code [requests, tokens]}，实现需保证非负钳制
     */
    Mono<long[]> increment(QuotaCounterKey key, long requests, long tokens);

    /**
     * 仅当桶已存在时累加计数（结算语义：不为结算新建桶）。
     *
     * @param key      计数键
     * @param requests 请求数增量，可为负
     * @param tokens   token 增量，可为负
     * @return 累加后的 {@code [requests, tokens]}；桶不存在时返回空 {@link Mono}
     */
    Mono<long[]> incrementIfPresent(QuotaCounterKey key, long requests, long tokens);

    /**
     * 读取一个桶的当前计数。
     *
     * @param key 计数键
     * @return 计数值；桶不存在时为 {@link Optional#empty()}
     */
    Mono<Optional<long[]>> read(QuotaCounterKey key);

    /**
     * 删除某个 API Key 的全部计数（配额重置）。
     *
     * @param apiKeyId API Key ID
     * @return 删除的桶数量
     */
    Mono<Long> reset(String apiKeyId);

    /**
     * 删除指定窗口内窗口起点早于 {@code cutoff} 的计数（保留期清理）。
     *
     * @param window 窗口类型
     * @param cutoff 截止时间
     * @return 删除的桶数量
     */
    Mono<Long> deleteExpired(QuotaWindow window, LocalDateTime cutoff);

    /**
     * {@link #increment(QuotaCounterKey, long, long)} 的三元组展开写法（等价便捷方法）。
     *
     * @param dimension   维度
     * @param window      窗口类型
     * @param windowStart 窗口起点
     * @param requests    请求数增量
     * @param tokens      token 增量
     * @return 累加后的 {@code [requests, tokens]}
     */
    default Mono<long[]> increment(final QuotaDimension dimension,
                                   final QuotaWindow window,
                                   final LocalDateTime windowStart,
                                   final long requests,
                                   final long tokens) {
        return increment(new QuotaCounterKey(dimension, window, windowStart), requests, tokens);
    }

    /**
     * {@link #incrementIfPresent(QuotaCounterKey, long, long)} 的三元组展开写法。
     *
     * @param dimension   维度
     * @param window      窗口类型
     * @param windowStart 窗口起点
     * @param requests    请求数增量
     * @param tokens      token 增量
     * @return 累加后的 {@code [requests, tokens]}；桶不存在时返回空 {@link Mono}
     */
    default Mono<long[]> incrementIfPresent(final QuotaDimension dimension,
                                            final QuotaWindow window,
                                            final LocalDateTime windowStart,
                                            final long requests,
                                            final long tokens) {
        return incrementIfPresent(new QuotaCounterKey(dimension, window, windowStart), requests, tokens);
    }

    /**
     * {@link #read(QuotaCounterKey)} 的三元组展开写法。
     *
     * @param dimension   维度
     * @param window      窗口类型
     * @param windowStart 窗口起点
     * @return 计数值；桶不存在时为 {@link Optional#empty()}
     */
    default Mono<Optional<long[]>> read(final QuotaDimension dimension,
                                        final QuotaWindow window,
                                        final LocalDateTime windowStart) {
        return read(new QuotaCounterKey(dimension, window, windowStart));
    }
}
