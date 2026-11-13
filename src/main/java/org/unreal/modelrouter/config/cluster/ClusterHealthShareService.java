package org.unreal.modelrouter.config.cluster;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 上游实例健康状态的跨副本共享（issue #1137）。
 *
 * <p>每个副本每轮健康检查后把本地结果写入 Redis（key 按上报者隔离并带 TTL），
 * 同时定时把其它副本的上报拉进内存快照，供 {@code ServiceStateManager} 同步读取。
 * 热路径（负载均衡挑实例）只读内存，不做 Redis 往返。</p>
 *
 * <p>默认关闭；只有 {@code jairouter.cluster.health-sharing.enabled=true}
 * 且处于集群模式、且 Redis 可用时才会真正参与判定。</p>
 */
@Component
public class ClusterHealthShareService {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthShareService.class);

    /** 共享健康位的 Redis 前缀，键格式：前缀 + reporterId + ':' + instanceKey。 */
    public static final String KEY_PREFIX = "jairouter:cluster:health:";

    /** 单条上报的 TTL 下限（秒）：即使 grace-seconds 调小，也要留出一次心跳的余量。 */
    private static final long MIN_TTL_SECONDS = 30L;

    private final HealthSharingProperties properties;
    private final ClusterProperties clusterProperties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final String reporterId;

    private final Map<String, Integer> healthyReplicas = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReportedAt = new ConcurrentHashMap<>();
    private final AtomicLong lastRefreshAt = new AtomicLong();

    public ClusterHealthShareService(final HealthSharingProperties properties,
                                     final ClusterProperties clusterProperties,
                                     final ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.clusterProperties = clusterProperties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.reporterId = resolveReporterId();
    }

    /**
     * 是否真正启用共享：开关打开 + 集群模式 + Redis 模板可用。
     */
    public boolean isActive() {
        return properties.isEnabled() && isClusterMode() && redisTemplateProvider.getIfAvailable() != null;
    }

    /**
     * 把本副本的健康位上报到共享存储。由健康检查批次结束后调用。
     */
    public void publish(final Map<String, Boolean> localHealth) {
        if (localHealth == null || localHealth.isEmpty() || !isActive()) {
            return;
        }
        final StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        final long ttlSeconds = Math.max(MIN_TTL_SECONDS, properties.getGraceSeconds() + MIN_TTL_SECONDS);
        try {
            for (final Map.Entry<String, Boolean> entry : localHealth.entrySet()) {
                redis.opsForValue().set(
                        KEY_PREFIX + reporterId + ':' + entry.getKey(),
                        Boolean.TRUE.equals(entry.getValue()) ? "1" : "0",
                        Duration.ofSeconds(ttlSeconds));
            }
        } catch (DataAccessException | IllegalStateException e) {
            // Redis 不可用时静默降级：共享视图停留在上一轮，判定退回本地结果
            log.debug("[cluster] 上报本副本健康状态失败: {}", e.getMessage());
        }
    }

    /**
     * 定时把共享视图拉进内存快照。间隔固定为 2 秒，与 grace-seconds 相比足够小。
     */
    @Scheduled(fixedDelay = 2000L, initialDelay = 15000L)
    public void refresh() {
        if (!isActive()) {
            healthyReplicas.clear();
            lastReportedAt.clear();
            return;
        }
        final StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, Long> seen = new HashMap<>();
        final long now = System.currentTimeMillis();
        try (Cursor<String> cursor = redis.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + '*').count(500).build())) {
            while (cursor.hasNext()) {
                final String key = cursor.next();
                final String rest = key.substring(KEY_PREFIX.length());
                final int separator = rest.indexOf(':');
                if (separator <= 0 || separator == rest.length() - 1) {
                    continue;
                }
                // 键格式 reporterId + ':' + instanceKey；instanceKey 自身含冒号（如 chat:xxx）
                final String instanceKey = rest.substring(separator + 1);
                final String value = redis.opsForValue().get(key);
                if ("1".equals(value)) {
                    counts.merge(instanceKey, 1, Integer::sum);
                }
                seen.merge(instanceKey, now, Math::max);
            }
        } catch (DataAccessException | IllegalStateException e) {
            log.debug("[cluster] 读取共享健康视图失败: {}", e.getMessage());
            return;
        }
        healthyReplicas.clear();
        healthyReplicas.putAll(counts);
        lastReportedAt.clear();
        lastReportedAt.putAll(seen);
        lastRefreshAt.set(now);
    }

    /**
     * 合并本地判定与共享视图。热路径同步调用，只读内存。
     */
    public boolean resolve(final String instanceKey, final boolean localHealthy) {
        if (!isActive()) {
            return localHealthy;
        }
        final int replicas = healthyReplicas.getOrDefault(instanceKey, 0);
        final long last = lastReportedAt.getOrDefault(instanceKey, 0L);
        return HealthSharingResolver.resolve(
                properties.getMode(),
                properties.getToleranceCount(),
                properties.getGraceSeconds() * 1000L,
                localHealthy,
                replicas,
                last,
                System.currentTimeMillis());
    }

    /**
     * 供管理端展示的运行时快照。
     */
    public Map<String, Object> stats() {
        final Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("active", isActive());
        snapshot.put("mode", properties.getMode());
        snapshot.put("toleranceCount", properties.getToleranceCount());
        snapshot.put("graceSeconds", properties.getGraceSeconds());
        snapshot.put("trackedInstances", healthyReplicas.size());
        snapshot.put("lastRefreshAt", lastRefreshAt.get());
        snapshot.put("reporterId", reporterId);
        return snapshot;
    }

    private boolean isClusterMode() {
        final String mode = clusterProperties.getMode();
        if (ClusterProperties.MODE_CLUSTER.equalsIgnoreCase(mode)) {
            return true;
        }
        if (ClusterProperties.MODE_STANDALONE.equalsIgnoreCase(mode)) {
            return false;
        }
        return clusterProperties.getReplicas() > 1
                || System.getenv("KUBERNETES_SERVICE_HOST") != null
                || System.getenv("POD_NAME") != null;
    }

    private static String resolveReporterId() {
        final String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isBlank()) {
            return podName;
        }
        final String hostName = System.getenv("HOSTNAME");
        if (hostName != null && !hostName.isBlank()) {
            return hostName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "replica-" + UUID.randomUUID();
        }
    }
}
