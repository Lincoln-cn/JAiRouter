package org.unreal.modelrouter.config.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群状态接口（#1136）。
 *
 * <p>单机部署不隐藏入口：返回 {@code mode=standalone} 与空成员表，前端据此显示判定依据，
 * 换形态后运维仍能在同一页面找到入口。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@RestController
@RequestMapping("/api/cluster")
public class ClusterStatusController {

    private final ClusterMemberRegistry registry;

    private final ClusterProperties properties;

    /**
     * 构造器。
     *
     * @param registry   成员注册表
     * @param properties 集群配置
     */
    public ClusterStatusController(final ClusterMemberRegistry registry,
                                   final ClusterProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * 读取集群状态。
     *
     * @return 形态、判定依据、本副本快照、成员表与汇总
     */
    @GetMapping("/status")
    public RouterResponse<Map<String, Object>> status() {
        boolean clusterMode = registry.isClusterMode();
        List<ClusterMember> members = clusterMode ? registry.members() : List.of();
        int clusterConfigVersion = members.stream()
                .mapToInt(ClusterMember::configVersion)
                .max()
                .orElse(-1);
        long suspectThreshold = System.currentTimeMillis()
                - Math.max(1L, properties.getHeartbeatIntervalMs()) * 2L;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", clusterMode ? "cluster" : "standalone");
        body.put("modeSource", registry.modeSource());
        body.put("self", registry.self());
        body.put("clusterConfigVersion", clusterConfigVersion);
        body.put("summary", Map.of(
                "total", members.size(),
                "online", members.size(),
                "suspect", members.stream().filter(m -> m.lastSeen() < suspectThreshold).count(),
                "staleReplicas", members.stream()
                        .filter(m -> m.configVersion() < clusterConfigVersion)
                        .count()));
        body.put("members", members);
        return RouterResponse.success(body);
    }
}
