package org.unreal.modelrouter.common.cluster;

import reactor.core.publisher.Flux;

/**
 * 跨副本事件广播（#164）。
 *
 * <p>实时推送类端点（路由监控、熔断监控 WebSocket）的事件源是<b>本进程运行时回调</b>，
 * 因此多副本下客户端连在哪个副本就只能看到哪个副本的事件——监控面板会静默漏事件，
 * 看起来正常但数据不全。本接口把这类事件广播到全集群。</p>
 *
 * <p>两种实现：Redis 未启用时退化为 {@link LocalClusterEventBus}（发布无操作、订阅为空），
 * 行为与引入本接口前<b>完全一致</b>——这是刻意保留的默认，避免在单副本部署上平白引入
 * Redis 依赖。</p>
 *
 * <p><b>不含健康状态 SSE</b>：它的数据来自共享数据库且每 5 秒定时生成快照，各副本看到的
 * 本就是同一份数据，广播只会带来重复推送。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
public interface ClusterEventBus {

    /**
     * 把本地产生的事件广播给全集群（含其它副本；本副本不需要，因为本地 Sink 已经分发过一次）。
     *
     * <p>实现必须<b>不抛出</b>：调用方在事件回调的热路径上，广播失败只应降级为「仅本副本可见」。</p>
     *
     * @param channel 逻辑通道名
     * @param payload 事件载荷（调用方自行序列化）
     */
    void publish(String channel, String payload);

    /**
     * 订阅其它副本广播来的事件。
     *
     * <p><b>刻意过滤掉本副本自己发出的事件</b>：本地产生的事件已由本进程的 Sink 分发，
     * 若不回环过滤会让每个客户端收到重复事件。</p>
     *
     * @param channel 逻辑通道名
     * @return 其它副本发出的事件载荷流；未启用集群广播时为空流
     */
    Flux<String> subscribe(String channel);

    /**
     * 当前是否真正跨副本。
     *
     * @return 是否已启用跨副本广播
     */
    boolean isClusterWide();
}
