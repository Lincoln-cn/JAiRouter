package org.unreal.modelrouter.config.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集群配置同步配置（{@code jairouter.cluster.config-sync.*}，#1132）。
 *
 * <p><b>为什么需要它</b>：配置写入落在共享库（{@code config_data}）里，各副本启动时读取一次，
 * 之后运行时状态（路由表、实例列表、上游健康位）只在本副本内刷新。于是副本 A 改了配置，
 * 副本 B 仍然按旧配置路由——管理面看到的是新配置，流量实际走的是旧路径，现象是
 * 「改完不生效，重启才生效」。本层给其它副本一个定期的「版本有没有变」检查，并在检测到
 * 变化后刷新本副本的运行时状态。</p>
 *
 * <p><b>轮询是正确性底线，事件是加速</b>：{@code config_data.version} 是唯一权威来源，轮询
 * 它不需要任何额外设施，Redis 不可用时行为不变；{@code eventBusEnabled} 让写入方在提交后
 * 广播一条事件，订阅方立即触发一次检查，把「最长一个轮询周期」压到「一次网络往返」。
 * 事件丢失只退化为轮询，不会漏更新。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.7
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.cluster.config-sync")
public class ClusterConfigSyncProperties {

    /** 轮询间隔下界（毫秒）：再小对数据库是纯噪音。 */
    private static final long MIN_POLL_INTERVAL_MS = 1_000L;

    /** 轮询间隔上界（毫秒）：10 分钟，超过这个值运维会先怀疑功能坏了。 */
    private static final long MAX_POLL_INTERVAL_MS = 600_000L;

    /** 默认轮询间隔（毫秒），与共享态检查的 5 秒量级一致。 */
    public static final long DEFAULT_POLL_INTERVAL_MS = 5_000L;

    /**
     * 是否启用跨副本配置同步（默认 true）。
     *
     * <p>单副本部署下轮询只是每 5 秒查一次版本号，开销可以忽略；关掉它之后
     * 多副本会退化为「重启才生效」的旧行为。</p>
     */
    private boolean enabled = true;

    /** 轮询间隔（毫秒），钳制在 1,000 ~ 600,000。 */
    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    /**
     * 是否使用集群事件总线做即时通知（默认 true）。
     *
     * <p>事件总线未启用（{@code jairouter.cluster.events.enabled=false}，单副本的默认值）时
     * 订阅得到空流，本开关不产生任何效果——不会因为打开它而在单副本上引入 Redis 依赖。</p>
     */
    private boolean eventBusEnabled = true;

    /**
     * 生效的轮询间隔。
     *
     * @return 钳制在 1,000 ~ 600,000 毫秒的取值
     */
    public long effectivePollIntervalMs() {
        return Math.max(MIN_POLL_INTERVAL_MS, Math.min(pollIntervalMs, MAX_POLL_INTERVAL_MS));
    }
}
