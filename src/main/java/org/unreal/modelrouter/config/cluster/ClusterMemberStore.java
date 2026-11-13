package org.unreal.modelrouter.config.cluster;

import java.time.Duration;
import java.util.List;

/**
 * 集群成员存储抽象（#1136）。
 *
 * <p>落点默认 Redis：成员信息是<b>带过期的运行时状态</b>，租约语义与 Redis TTL 天然匹配，
 * 且不需要额外的清理任务。将来若客户的部署环境只提供关系库，可实现一个基于
 * {@code NOW()} + UPSERT 的表实现并切换 {@code jairouter.cluster.member.store}。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
public interface ClusterMemberStore {

    /**
     * 写入/续期一个成员。
     *
     * @param member 成员快照
     * @param ttl    过期时间
     */
    void put(ClusterMember member, Duration ttl);

    /**
     * 列出当前所有未过期成员。
     *
     * @return 成员列表（可能包含已过期但尚未被 Redis 删除的条目，由调用方按 {@code lastSeen} 过滤）
     */
    List<ClusterMember> list();

    /**
     * 移除一个成员（本副本优雅停机时调用，避免其它副本在 TTL 内看到幽灵成员）。
     *
     * @param instanceId 副本标识
     */
    void remove(String instanceId);
}
