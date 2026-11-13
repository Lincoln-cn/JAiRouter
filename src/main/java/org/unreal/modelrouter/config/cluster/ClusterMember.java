package org.unreal.modelrouter.config.cluster;

/**
 * 一个集群副本的上报快照（#1136）。
 *
 * <p>{@code advertiseUrl} 是其它副本与运维工具访问该副本的地址，因此必须是<b>本副本视角外可达</b>
 * 的地址：容器 bridge 网络下 {@code hostname:port} 通常不可达、Kubernetes pod IP 会随重建变化，
 * 两种情形都应通过 {@code jairouter.cluster.member.advertise-url} 显式配置。</p>
 *
 * @param instanceId      副本标识（{@link org.unreal.modelrouter.common.util.InstanceIdentity}）
 * @param displayName     展示名（K8s 取 POD_NAME，裸机取 hostname）
 * @param advertiseUrl    对外可访问地址
 * @param startedAt       进程启动时间（epoch 毫秒）
 * @param configVersion   本副本已应用的配置版本（-1 表示尚未建立基线）
 * @param instanceCount   本副本注册的下游实例总数
 * @param healthyInstances 其中被判定为健康的实例数
 * @param buildVersion    应用版本号
 * @param lastSeen        最近一次心跳时间（epoch 毫秒）
 * @author JAiRouter Team
 * @since 3.8.6
 */
public record ClusterMember(
        String instanceId,
        String displayName,
        String advertiseUrl,
        long startedAt,
        int configVersion,
        int instanceCount,
        int healthyInstances,
        String buildVersion,
        long lastSeen) {
}
