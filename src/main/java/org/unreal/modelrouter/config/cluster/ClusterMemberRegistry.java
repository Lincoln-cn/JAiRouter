package org.unreal.modelrouter.config.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.cluster.MultiReplicaSharedStateGate;
import org.unreal.modelrouter.common.util.InstanceIdentity;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import jakarta.annotation.PreDestroy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 集群成员注册与心跳（#1136）。
 *
 * <p>只在<b>集群形态</b>下工作：单机部署时 {@link #start()} 直接返回，不写 Redis、
 * 不起线程，状态页据此显示「单机部署」空态。</p>
 *
 * <p>心跳只做两件事：把本副本快照写进成员表并续期；从成员表读出其它副本。
 * 存活判定交给 TTL（副本被强杀后最迟一个 TTL 后消失），不做额外的失联协商。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@Component
public class ClusterMemberRegistry {

    private final ClusterMemberStore memberStore;

    private final ClusterProperties properties;

    private final ConfigVersionManager configVersionManager;

    private final ObjectProvider<ModelServiceRegistry> modelServiceRegistry;

    private final int serverPort;

    private final String instanceId = InstanceIdentity.id();

    private final long startedAt = System.currentTimeMillis();

    private final String buildVersion = Optional.ofNullable(
                    ClusterMemberRegistry.class.getPackage().getImplementationVersion())
            .orElse("unknown");

    private volatile ScheduledExecutorService scheduler;

    /**
     * 构造器。
     *
     * @param memberStore         成员存储
     * @param properties          集群配置
     * @param configVersionManager 配置版本来源
     * @param modelServiceRegistry 下游实例注册表（心跳时统计实例数与健康数）
     * @param serverPort          本副本监听端口
     */
    public ClusterMemberRegistry(final ClusterMemberStore memberStore,
                                 final ClusterProperties properties,
                                 final ConfigVersionManager configVersionManager,
                                 final ObjectProvider<ModelServiceRegistry> modelServiceRegistry,
                                 @Value("${server.port:8080}") final int serverPort) {
        this.memberStore = memberStore;
        this.properties = properties;
        this.configVersionManager = configVersionManager;
        this.modelServiceRegistry = modelServiceRegistry;
        this.serverPort = serverPort;
    }

    /**
     * 应用就绪后启动心跳（仅集群形态）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!isClusterMode()) {
            log.info("[cluster] 当前为单机部署（mode={}），跳过成员心跳", properties.getMode());
            return;
        }
        heartbeatOnce();
        long interval = Math.max(1000L, properties.getHeartbeatIntervalMs());
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cluster-member-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::heartbeatOnce, interval, interval, TimeUnit.MILLISECONDS);
        log.info("[cluster] 成员心跳已启动：instanceId={} advertiseUrl={} 间隔={}ms TTL={}s",
                instanceId, advertiseUrl(), interval, properties.getTtlSeconds());
    }

    /**
     * 优雅停机：停心跳并主动摘除自己，避免其它副本在 TTL 内看到幽灵成员。
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (isClusterMode()) {
            memberStore.remove(instanceId);
        }
    }

    /**
     * 是否运行在集群形态。
     *
     * @return true = 多副本
     */
    public boolean isClusterMode() {
        String mode = properties.getMode() == null ? "auto" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if ("cluster".equals(mode)) {
            return true;
        }
        if ("standalone".equals(mode)) {
            return false;
        }
        return MultiReplicaSharedStateGate.detectMultiReplica(properties.getReplicas(), System.getenv());
    }

    /**
     * 当前形态的判定来源，供状态页展示。
     *
     * @return 判定依据
     */
    public String modeSource() {
        String mode = properties.getMode() == null ? "auto" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if ("cluster".equals(mode) || "standalone".equals(mode)) {
            return "explicit:" + mode;
        }
        return "auto:replicas=" + properties.getReplicas();
    }

    /**
     * 本副本快照（未启动心跳时也返回，供状态页展示）。
     *
     * @return 本副本快照
     */
    public ClusterMember self() {
        return snapshot();
    }

    /**
     * 读取成员表，按最近心跳倒序（本副本最近）。
     *
     * @return 未过期成员列表
     */
    public List<ClusterMember> members() {
        long expiry = System.currentTimeMillis() - Math.max(1L, properties.getTtlSeconds()) * 1000L;
        return memberStore.list().stream()
                .filter(member -> member.lastSeen() >= expiry)
                .sorted(Comparator.comparingLong(ClusterMember::lastSeen).reversed())
                .toList();
    }

    private void heartbeatOnce() {
        try {
            ClusterMember member = snapshot();
            memberStore.put(member,
                    Duration.ofSeconds(Math.max(1L, properties.getTtlSeconds())));
        } catch (RuntimeException e) {
            log.warn("[cluster] 成员心跳失败: {}", e.getMessage());
        }
    }

    private ClusterMember snapshot() {
        int instanceCount = 0;
        int healthyInstances = 0;
        ModelServiceRegistry registry = modelServiceRegistry.getIfAvailable();
        if (registry != null) {
            for (var instances : registry.getAllInstances().values()) {
                instanceCount += instances.size();
                healthyInstances += (int) instances.stream()
                        .filter(instance -> Boolean.TRUE.equals(instance.isHealthy()))
                        .count();
            }
        }
        return new ClusterMember(
                instanceId,
                displayName(),
                advertiseUrl(),
                startedAt,
                configVersionManager.getCurrentVersion(),
                instanceCount,
                healthyInstances,
                buildVersion,
                System.currentTimeMillis());
    }

    private String advertiseUrl() {
        if (properties.getAdvertiseUrl() != null && !properties.getAdvertiseUrl().isBlank()) {
            return properties.getAdvertiseUrl().trim();
        }
        return "http://" + displayName() + ":" + serverPort;
    }

    private String displayName() {
        String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isBlank()) {
            return podName;
        }
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return instanceId;
        }
    }
}
