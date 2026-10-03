package org.unreal.modelrouter.common.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 单副本实现（默认）：不做任何跨副本广播（#164）。
 *
 * <p>发布是无操作、订阅是空流，因此开启前后的行为完全一致。单副本部署没有跨副本问题，
 * 也不应为它引入 Redis 依赖。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "jairouter.cluster.events.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class LocalClusterEventBus implements ClusterEventBus {

    public LocalClusterEventBus() {
        log.debug("集群事件广播未启用：实时事件仅在本副本内可见（单副本部署的正常状态）");
    }

    @Override
    public void publish(final String channel, final String payload) {
        // 本副本事件已由本地 Sink 分发，无需广播
    }

    @Override
    public Flux<ClusterEvent> subscribe(final String channel) {
        return Flux.empty();
    }

    @Override
    public boolean isClusterWide() {
        return false;
    }
}
