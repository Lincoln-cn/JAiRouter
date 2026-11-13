package org.unreal.modelrouter.config.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集群形态与成员发现配置（#1136）。
 *
 * <p><b>部署形态门控</b>：{@link #mode} 取 {@code auto | standalone | cluster}。
 * 只有判定为 {@code cluster} 时才会写入成员表、发心跳；{@code standalone} 下
 * {@link ClusterMemberRegistry} 直接跳过，不在 Redis 留下任何 key。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.cluster")
public class ClusterProperties {

    /** 显式声明的集群形态取值。 */
    public static final String MODE_CLUSTER = "cluster";

    /** 显式声明的单机形态取值。 */
    public static final String MODE_STANDALONE = "standalone";

    /** 按副本数/环境推断形态。 */
    public static final String MODE_AUTO = "auto";

    /** 部署形态：auto（缺省，按副本数/环境推断）/ standalone / cluster。 */
    private String mode = MODE_AUTO;

    /** 副本数；{@code 0} 表示未显式配置（交给环境推断）。 */
    private int replicas;

    /** 成员上报地址；为空时回落为 {@code http://<hostname>:<server.port>}。 */
    private String advertiseUrl;

    /** 心跳间隔（毫秒），默认 5 秒。 */
    private long heartbeatIntervalMs = 5000;

    /** 成员过期时间（秒），默认 15 秒（= 3 个心跳周期）。 */
    private long ttlSeconds = 15;
}
