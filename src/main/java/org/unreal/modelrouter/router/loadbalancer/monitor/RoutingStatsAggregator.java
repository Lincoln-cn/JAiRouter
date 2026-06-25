package org.unreal.modelrouter.router.loadbalancer.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 路由统计聚合器
 * 实时统计各服务类型的实例选中分布
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
public class RoutingStatsAggregator {

    /**
     * 按服务类型分组的统计数据
     */
    private final Map<String, ServiceRoutingStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 用于更新统计的锁
     */
    private final ReentrantLock updateLock = new ReentrantLock();

    /**
     * 更新统计（收到路由事件时调用）
     */
    public void update(RoutingEvent event) {
        String serviceType = event.serviceType();

        ServiceRoutingStats stats = statsMap.computeIfAbsent(
            serviceType,
            k -> new ServiceRoutingStats(serviceType)
        );

        stats.recordSelection(
            event.selectedInstance(),
            event.clientId(),
            event.timestamp(),
            event.strategy(),
            event.modelName()
        );
    }

    /**
     * 获取指定服务类型的统计数据
     */
    public ServiceRoutingStats getStats(String serviceType) {
        return statsMap.get(serviceType);
    }

    /**
     * 获取所有服务类型的统计数据
     */
    public Map<String, ServiceRoutingStats> getAllStats() {
        return new HashMap<>(statsMap);
    }

    /**
     * 重置指定服务类型的统计数据
     */
    public void resetStats(String serviceType) {
        ServiceRoutingStats stats = statsMap.get(serviceType);
        if (stats != null) {
            stats.reset();
            log.info("Reset routing stats for service: {}", serviceType);
        }
    }

    /**
     * 重置所有统计数据
     */
    public void resetAllStats() {
        statsMap.values().forEach(ServiceRoutingStats::reset);
        log.info("Reset all routing stats");
    }

    /**
     * 获取轮转序列（round-robin策略验证用）
     */
    public List<String> getRotationSequence(String serviceType, int count) {
        ServiceRoutingStats stats = statsMap.get(serviceType);
        if (stats == null) {
            return Collections.emptyList();
        }
        return stats.getRecentSelectionSequence(count);
    }

    /**
     * 获取IP到实例的映射（ip-hash/consistent-hash验证用）
     */
    public Map<String, String> getIpToInstanceMapping(String serviceType, int limit) {
        ServiceRoutingStats stats = statsMap.get(serviceType);
        if (stats == null) {
            return Collections.emptyMap();
        }
        return stats.getClientToInstanceMapping(limit);
    }

    /**
     * 单个服务类型的路由统计
     */
    public static class ServiceRoutingStats {
        private final String serviceType;

        /**
         * 负载均衡策略
         */
        private volatile String strategy = "Unknown";

        /**
         * 实例选中次数统计
         */
        private final Map<String, AtomicLong> instanceSelectionCounts = new ConcurrentHashMap<>();

        /**
         * 按模型分组的实例选中统计
         * 结构: Map<模型名, Map<实例ID, 计数>>
         */
        private final Map<String, Map<String, AtomicLong>> modelInstanceCounts = new ConcurrentHashMap<>();

        /**
         * 最近选中的实例序列（用于验证 round-robin）
         */
        private final List<SelectionRecord> recentSelections = Collections.synchronizedList(new ArrayList<>());

        /**
         * 客户端到实例的映射（用于验证 ip-hash）
         */
        private final Map<String, String> clientToInstanceMap = new ConcurrentHashMap<>();

        /**
         * 客户端请求计数
         */
        private final Map<String, AtomicLong> clientRequestCounts = new ConcurrentHashMap<>();

        /**
         * 统计开始时间
         */
        private volatile Instant startTime = Instant.now();

        /**
         * 保留最近选中的最大数量
         */
        private static final int MAX_RECENT_SELECTIONS = 100;

        public ServiceRoutingStats(String serviceType) {
            this.serviceType = serviceType;
        }

        /**
         * 记录一次实例选择
         */
        public void recordSelection(String instance, String clientId, Instant timestamp, String strategy, String modelName) {
            // 更新策略
            if (strategy != null && !strategy.isEmpty()) {
                this.strategy = strategy;
            }

            // 更新实例选中计数
            instanceSelectionCounts.computeIfAbsent(instance, k -> new AtomicLong(0))
                .incrementAndGet();

            // 更新模型-实例统计
            if (modelName != null && !modelName.isEmpty()) {
                Map<String, AtomicLong> instanceCounts = modelInstanceCounts.computeIfAbsent(
                    modelName, k -> new ConcurrentHashMap<>());
                instanceCounts.computeIfAbsent(instance, k -> new AtomicLong(0))
                    .incrementAndGet();
            }

            // 记录最近选择序列
            SelectionRecord record = new SelectionRecord(instance, clientId, timestamp);
            synchronized (recentSelections) {
                recentSelections.add(record);
                while (recentSelections.size() > MAX_RECENT_SELECTIONS) {
                    recentSelections.remove(0);
                }
            }

            // 更新客户端到实例的映射
            if (clientId != null && !clientId.isEmpty()) {
                clientToInstanceMap.put(clientId, instance);
                clientRequestCounts.computeIfAbsent(clientId, k -> new AtomicLong(0))
                    .incrementAndGet();
            }
        }

        /**
         * 获取总选择次数
         */
        public long getTotalSelections() {
            return instanceSelectionCounts.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        }

        /**
         * 获取负载均衡策略
         */
        public String getStrategy() {
            return strategy;
        }

        /**
         * 获取实例选中分布（前端使用 instanceCounts）
         */
        @JsonProperty("instanceCounts")
        public Map<String, Long> getInstanceDistribution() {
            Map<String, Long> distribution = new HashMap<>();
            instanceSelectionCounts.forEach((instance, count) -> {
                distribution.put(instance, count.get());
            });
            return distribution;
        }

        /**
         * 获取按模型分组的实例分布（前端使用 modelInstanceCounts）
         * 结构: Map<模型名, Map<实例ID, 计数>>
         */
        @JsonProperty("modelInstanceCounts")
        public Map<String, Map<String, Long>> getModelInstanceDistribution() {
            Map<String, Map<String, Long>> result = new HashMap<>();
            modelInstanceCounts.forEach((modelName, instanceMap) -> {
                Map<String, Long> instanceCounts = new HashMap<>();
                instanceMap.forEach((instance, count) -> {
                    instanceCounts.put(instance, count.get());
                });
                result.put(modelName, instanceCounts);
            });
            return result;
        }

        /**
         * 获取实例选中百分比
         */
        public Map<String, Double> getInstanceDistributionPercent() {
            long total = getTotalSelections();
            Map<String, Double> percent = new HashMap<>();
            if (total > 0) {
                instanceSelectionCounts.forEach((instance, count) -> {
                    percent.put(instance, (count.get() * 100.0) / total);
                });
            }
            return percent;
        }

        /**
         * 获取最近选中的实例序列（用于 JSON 序列化）
         */
        @JsonProperty("recentSelections")
        public List<String> getRecentSelectionsForJson() {
            return getRecentSelectionSequence(MAX_RECENT_SELECTIONS);
        }

        /**
         * 获取最近选中的实例序列
         */
        public List<String> getRecentSelectionSequence(int count) {
            List<String> sequence = new ArrayList<>();
            synchronized (recentSelections) {
                int start = Math.max(0, recentSelections.size() - count);
                for (int i = start; i < recentSelections.size(); i++) {
                    sequence.add(recentSelections.get(i).instance());
                }
            }
            return sequence;
        }

        /**
         * 获取客户端到实例的映射（用于 JSON 序列化）
         */
        @JsonProperty("clientInstanceMap")
        public Map<String, String> getClientInstanceMapForJson() {
            return getClientToInstanceMapping(100);
        }

        /**
         * 获取客户端到实例的映射
         */
        public Map<String, String> getClientToInstanceMapping(int limit) {
            Map<String, String> result = new HashMap<>();
            clientToInstanceMap.entrySet().stream()
                .limit(limit)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        }

        /**
         * 获取统计时长（秒）
         */
        public long getDurationSeconds() {
            return Duration.between(startTime, Instant.now()).getSeconds();
        }

        /**
         * 获取每秒请求数
         */
        public double getRequestsPerSecond() {
            long seconds = getDurationSeconds();
            if (seconds > 0) {
                return (double) getTotalSelections() / seconds;
            }
            return 0.0;
        }

        /**
         * 重置统计
         */
        public void reset() {
            instanceSelectionCounts.clear();
            modelInstanceCounts.clear();
            synchronized (recentSelections) {
                recentSelections.clear();
            }
            clientToInstanceMap.clear();
            clientRequestCounts.clear();
            startTime = Instant.now();
        }

        /**
         * 选择记录
         */
        private record SelectionRecord(String instance, String clientId, Instant timestamp) {}
    }
}
