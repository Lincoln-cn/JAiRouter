package org.unreal.modelrouter.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.dto.ModelCallStats;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型调用统计仓库
 * 
 * v2.0.0 新增功能：
 * - 内存存储模型调用统计
 * - 支持按模型名称、服务类型查询
 * - 支持统计数据的持久化和恢复
 * - 支持 QPS 计算
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@Repository
public class ModelCallStatsRepository {

    private static final Logger logger = LoggerFactory.getLogger(ModelCallStatsRepository.class);

    private static final String STORE_KEY_PREFIX = "model.callstats.";
    private static final long QPS_WINDOW_MS = 60000; // 1 分钟窗口

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    // 主缓存：key = "serviceType:modelName" -> stats
    private final Map<String, ModelCallStats> statsCache = new ConcurrentHashMap<>();

    // QPS 计算：key = "serviceType:modelName" -> 时间戳列表
    private final Map<String, Queue<Long>> qpsWindowCache = new ConcurrentHashMap<>();

    // 总计计数器
    private final AtomicLong totalCallsCounter = new AtomicLong(0);
    private final AtomicLong totalSuccessCounter = new AtomicLong(0);
    private final AtomicLong totalFailureCounter = new AtomicLong(0);

    @Autowired
    public ModelCallStatsRepository(StoreManager storeManager, ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取或创建统计对象
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 统计对象
     */
    public ModelCallStats getOrCreate(String serviceType, String modelName) {
        String key = buildKey(serviceType, modelName);
        return statsCache.computeIfAbsent(key, k -> {
            ModelCallStats stats = ModelCallStats.builder()
                    .modelName(modelName)
                    .serviceType(serviceType)
                    .totalCalls(0)
                    .successCount(0)
                    .failureCount(0)
                    .circuitBreakerCount(0)
                    .rateLimitCount(0)
                    .avgResponseTime(0)
                    .minResponseTime(Long.MAX_VALUE)
                    .maxResponseTime(0)
                    .statsStartTime(System.currentTimeMillis())
                    .instanceCount(0)
                    .active(false)
                    .healthStatus("HEALTHY")
                    .errorCodeDistribution(new HashMap<>())
                    .build();
            logger.debug("创建新的模型统计：serviceType={}, modelName={}", serviceType, modelName);
            return stats;
        });
    }

    /**
     * 获取统计对象
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 统计对象，不存在返回 null
     */
    public ModelCallStats get(String serviceType, String modelName) {
        String key = buildKey(serviceType, modelName);
        return statsCache.get(key);
    }

    /**
     * 更新统计信息
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param success 是否成功
     * @param responseTime 响应时间
     */
    public void updateStats(String serviceType, String modelName, boolean success, long responseTime) {
        ModelCallStats stats = getOrCreate(serviceType, modelName);
        synchronized (stats) {
            stats.updateStats(success, responseTime);
        }

        // 更新总计数器
        totalCallsCounter.incrementAndGet();
        if (success) {
            totalSuccessCounter.incrementAndGet();
        } else {
            totalFailureCounter.incrementAndGet();
        }

        // 更新 QPS 窗口
        updateQpsWindow(serviceType, modelName);

        // 标记为活跃
        stats.setActive(true);
    }

    /**
     * 记录熔断事件
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     */
    public void recordCircuitBreaker(String serviceType, String modelName) {
        ModelCallStats stats = getOrCreate(serviceType, modelName);
        synchronized (stats) {
            stats.recordCircuitBreaker();
        }
        totalCallsCounter.incrementAndGet();
        updateQpsWindow(serviceType, modelName);
    }

    /**
     * 记录限流事件
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     */
    public void recordRateLimit(String serviceType, String modelName) {
        ModelCallStats stats = getOrCreate(serviceType, modelName);
        synchronized (stats) {
            stats.recordRateLimit();
        }
        totalCallsCounter.incrementAndGet();
        updateQpsWindow(serviceType, modelName);
    }

    /**
     * 记录错误码
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param errorCode 错误码
     */
    public void recordErrorCode(String serviceType, String modelName, String errorCode) {
        ModelCallStats stats = getOrCreate(serviceType, modelName);
        synchronized (stats) {
            stats.recordErrorCode(errorCode);
        }
    }

    /**
     * 更新 QPS 窗口
     */
    private void updateQpsWindow(String serviceType, String modelName) {
        String key = buildKey(serviceType, modelName);
        long currentTime = System.currentTimeMillis();

        Queue<Long> window = qpsWindowCache.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (window) {
            window.add(currentTime);
            // 清理过期数据
            while (!window.isEmpty() && currentTime - window.peek() > QPS_WINDOW_MS) {
                window.poll();
            }
        }
    }

    /**
     * 获取当前 QPS
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return QPS
     */
    public double getCurrentQps(String serviceType, String modelName) {
        String key = buildKey(serviceType, modelName);
        Queue<Long> window = qpsWindowCache.get(key);
        if (window == null || window.isEmpty()) {
            return 0.0;
        }
        synchronized (window) {
            long currentTime = System.currentTimeMillis();
            // 清理过期数据
            while (!window.isEmpty() && currentTime - window.peek() > QPS_WINDOW_MS) {
                window.poll();
            }
            // 计算 QPS (calls per second)
            return window.size() * 1000.0 / QPS_WINDOW_MS;
        }
    }

    /**
     * 获取所有统计
     *
     * @return 所有模型统计
     */
    public Collection<ModelCallStats> getAllStats() {
        return statsCache.values();
    }

    /**
     * 按服务类型获取统计
     *
     * @param serviceType 服务类型
     * @return 统计列表
     */
    public List<ModelCallStats> getStatsByServiceType(String serviceType) {
        List<ModelCallStats> result = new ArrayList<>();
        for (ModelCallStats stats : statsCache.values()) {
            if (stats.getServiceType().equals(serviceType)) {
                result.add(stats);
            }
        }
        return result;
    }

    /**
     * 获取所有模型名称
     *
     * @return 模型名称集合
     */
    public Set<String> getAllModelNames() {
        Set<String> names = new HashSet<>();
        for (ModelCallStats stats : statsCache.values()) {
            names.add(stats.getModelName());
        }
        return names;
    }

    /**
     * 获取统计数量
     *
     * @return 统计对象数量
     */
    public int getCount() {
        return statsCache.size();
    }

    /**
     * 获取总调用次数
     *
     * @return 总调用次数
     */
    public long getTotalCalls() {
        return totalCallsCounter.get();
    }

    /**
     * 获取总成功次数
     *
     * @return 总成功次数
     */
    public long getTotalSuccess() {
        return totalSuccessCounter.get();
    }

    /**
     * 获取总失败次数
     *
     * @return 总失败次数
     */
    public long getTotalFailure() {
        return totalFailureCounter.get();
    }

    /**
     * 获取总体成功率
     *
     * @return 成功率
     */
    public double getOverallSuccessRate() {
        long total = totalCallsCounter.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) totalSuccessCounter.get() / total;
    }

    /**
     * 删除统计
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     */
    public void remove(String serviceType, String modelName) {
        String key = buildKey(serviceType, modelName);
        statsCache.remove(key);
        qpsWindowCache.remove(key);
        logger.info("删除模型统计：serviceType={}, modelName={}", serviceType, modelName);
    }

    /**
     * 清空所有统计
     */
    public void clear() {
        statsCache.clear();
        qpsWindowCache.clear();
        totalCallsCounter.set(0);
        totalSuccessCounter.set(0);
        totalFailureCounter.set(0);
        logger.info("清空所有模型统计");
    }

    /**
     * 持久化统计到存储
     *
     * @return 是否成功
     */
    public boolean persist() {
        try {
            Map<String, Object> allData = new HashMap<>();
            for (Map.Entry<String, ModelCallStats> entry : statsCache.entrySet()) {
                allData.put(entry.getKey(), entry.getValue());
            }
            Map<String, Object> jsonData = new java.util.HashMap<>();
            jsonData.put("data", allData);
            jsonData.put("timestamp", System.currentTimeMillis());
            storeManager.saveConfig(STORE_KEY_PREFIX + "all", jsonData);
            logger.debug("持久化模型统计成功，共 {} 条记录", statsCache.size());
            return true;
        } catch (Exception e) {
            logger.error("持久化模型统计失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 从存储恢复统计
     *
     * @return 是否成功
     */
    @SuppressWarnings("unchecked")
    public boolean restore() {
        try {
            Map<String, Object> configData = storeManager.getConfig(STORE_KEY_PREFIX + "all");
            if (configData == null) {
                return false;
            }
            Object dataObj = configData.get("data");
            if (!(dataObj instanceof Map)) {
                return false;
            }
            Map<String, Map<String, Object>> data = (Map<String, Map<String, Object>>) dataObj;
            if (data == null || data.isEmpty()) {
                logger.debug("未找到持久化的模型统计");
                return false;
            }
            for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    String serviceType = parts[0];
                    String modelName = parts[1];
                    Map<String, Object> statsData = entry.getValue();

                    ModelCallStats stats = getOrCreate(serviceType, modelName);
                    // 恢复字段
                    if (statsData.containsKey("totalCalls")) {
                        stats.setTotalCalls(((Number) statsData.get("totalCalls")).longValue());
                    }
                    if (statsData.containsKey("successCount")) {
                        stats.setSuccessCount(((Number) statsData.get("successCount")).longValue());
                    }
                    if (statsData.containsKey("failureCount")) {
                        stats.setFailureCount(((Number) statsData.get("failureCount")).longValue());
                    }
                    if (statsData.containsKey("avgResponseTime")) {
                        stats.setAvgResponseTime(((Number) statsData.get("avgResponseTime")).doubleValue());
                    }
                    if (statsData.containsKey("minResponseTime")) {
                        stats.setMinResponseTime(((Number) statsData.get("minResponseTime")).longValue());
                    }
                    if (statsData.containsKey("maxResponseTime")) {
                        stats.setMaxResponseTime(((Number) statsData.get("maxResponseTime")).longValue());
                    }
                    stats.setSuccessRate(stats.calculateSuccessRate());
                    stats.setHealthStatus(stats.determineHealthStatus());
                }
            }
            logger.info("恢复模型统计成功，共 {} 条记录", data.size());
            return true;
        } catch (Exception e) {
            logger.error("恢复模型统计失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建缓存键
     */
    private String buildKey(String serviceType, String modelName) {
        return serviceType + ":" + modelName;
    }

    /**
     * 获取 Top N 活跃模型
     *
     * @param n 数量
     * @return Top N 统计列表
     */
    public List<ModelCallStats> getTopActiveModels(int n) {
        return statsCache.values().stream()
                .filter(ModelCallStats::isActive)
                .sorted((a, b) -> Long.compare(b.getTotalCalls(), a.getTotalCalls()))
                .limit(n)
                .toList();
    }

    /**
     * 获取健康状态异常的模型
     *
     * @return 统计列表
     */
    public List<ModelCallStats> getUnhealthyModels() {
        List<ModelCallStats> result = new ArrayList<>();
        for (ModelCallStats stats : statsCache.values()) {
            if ("UNHEALTHY".equals(stats.getHealthStatus()) || "DEGRADED".equals(stats.getHealthStatus())) {
                result.add(stats);
            }
        }
        return result;
    }
}
