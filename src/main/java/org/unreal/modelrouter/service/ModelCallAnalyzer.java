package org.unreal.modelrouter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.ModelCallStats;
import org.unreal.modelrouter.repository.ModelCallStatsRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型调用分析服务
 * 
 * v2.0.0 新增功能：
 * - 模型调用统计分析
 * - 按模型名称聚合统计
 * - 健康状态监控
 * - Top N 模型排行
 * - 异常模型告警
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@Service
public class ModelCallAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ModelCallAnalyzer.class);

    private final ModelCallStatsRepository statsRepository;

    @Autowired
    public ModelCallAnalyzer(final ModelCallStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * 获取所有模型的统计摘要
     *
     * @return 统计摘要
     */
    public Map<String, Object> getStatsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalModels", statsRepository.getCount());
        summary.put("totalCalls", statsRepository.getTotalCalls());
        summary.put("totalSuccess", statsRepository.getTotalSuccess());
        summary.put("totalFailure", statsRepository.getTotalFailure());
        summary.put("overallSuccessRate", statsRepository.getOverallSuccessRate());
        summary.put("timestamp", System.currentTimeMillis());
        return summary;
    }

    /**
     * 按服务类型获取统计
     *
     * @param serviceType 服务类型
     * @return 统计列表
     */
    public List<ModelCallStats> getStatsByServiceType(final String serviceType) {
        return statsRepository.getStatsByServiceType(serviceType);
    }

    /**
     * 获取指定模型的统计
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 统计对象
     */
    public ModelCallStats getModelStats(final String serviceType,final String modelName) {
        ModelCallStats stats = statsRepository.get(serviceType, modelName);
        if (stats != null) {
            // 更新 QPS
            double qps = statsRepository.getCurrentQps(serviceType, modelName);
            stats.setCurrentQps(qps);
        }
        return stats;
    }

    /**
     * 获取 Top N 活跃模型
     *
     * @param n 数量
     * @return Top N 统计列表
     */
    public List<ModelCallStats> getTopActiveModels(final int n) {
        return statsRepository.getTopActiveModels(n);
    }

    /**
     * 获取 Top 10 活跃模型
     *
     * @return Top 10 统计列表
     */
    public List<ModelCallStats> getTop10ActiveModels() {
        return getTopActiveModels(10);
    }

    /**
     * 获取健康状态异常的模型
     *
     * @return 统计列表
     */
    public List<ModelCallStats> getUnhealthyModels() {
        return statsRepository.getUnhealthyModels();
    }

    /**
     * 获取所有模型统计（带分页和过滤）
     *
     * @param serviceType 服务类型过滤（可选）
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @param sortBy 排序字段 (totalCalls, successRate, avgResponseTime 等)
     * @param ascending 是否升序
     * @return 分页结果
     */
    public Map<String, Object> getAllModelStats(final String serviceType,final int page,final int size,final String sortBy,final boolean ascending) {
        List<ModelCallStats> allStats = new ArrayList<>(statsRepository.getAllStats());

        // 过滤
        if (serviceType != null && !serviceType.isEmpty()) {
            allStats = allStats.stream()
                    .filter(s -> s.getServiceType().equals(serviceType))
                    .collect(Collectors.toList());
        }

        // 更新 QPS
        for (ModelCallStats stats : allStats) {
            double qps = statsRepository.getCurrentQps(stats.getServiceType(), stats.getModelName());
            stats.setCurrentQps(qps);
        }

        // 排序
        Comparator<ModelCallStats> comparator = getComparator(sortBy);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        allStats.sort(comparator);

        // 分页
        int total = allStats.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<ModelCallStats> pagedStats = fromIndex < total ? allStats.subList(fromIndex, toIndex) : new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("content", pagedStats);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (total + size - 1) / size);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    /**
     * 获取比较器
     */
    private Comparator<ModelCallStats> getComparator(final String sortBy) {
        switch (sortBy != null ? sortBy : "totalCalls") {
            case "successRate":
                return Comparator.comparingDouble(ModelCallStats::getSuccessRate);
            case "avgResponseTime":
                return Comparator.comparingDouble(ModelCallStats::getAvgResponseTime);
            case "failureCount":
                return Comparator.comparingLong(ModelCallStats::getFailureCount);
            case "currentQps":
                return Comparator.comparingDouble(ModelCallStats::getCurrentQps);
            case "totalCalls":
            default:
                return Comparator.comparingLong(ModelCallStats::getTotalCalls);
        }
    }

    /**
     * 获取按服务类型分组的统计
     *
     * @return 分组统计
     */
    public Map<String, Object> getGroupedByServiceType() {
        Map<String, Object> result = new HashMap<>();

        for (ModelCallStats stats : statsRepository.getAllStats()) {
            String serviceType = stats.getServiceType();
            result.computeIfAbsent(serviceType, k -> {
                Map<String, Object> group = new HashMap<>();
                group.put("serviceType", serviceType);
                group.put("models", new ArrayList<ModelCallStats>());
                group.put("totalCalls", 0L);
                group.put("totalSuccess", 0L);
                group.put("totalFailure", 0L);
                group.put("avgSuccessRate", 0.0);
                return group;
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> group = (Map<String, Object>) result.get(serviceType);
            @SuppressWarnings("unchecked")
            List<ModelCallStats> models = (List<ModelCallStats>) group.get("models");
            models.add(stats);

            // 累加
            long totalCalls = (long) group.get("totalCalls") + stats.getTotalCalls();
            long totalSuccess = (long) group.get("totalSuccess") + stats.getSuccessCount();
            long totalFailure = (long) group.get("totalFailure") + stats.getFailureCount();
            group.put("totalCalls", totalCalls);
            group.put("totalSuccess", totalSuccess);
            group.put("totalFailure", totalFailure);

            // 计算平均成功率
            double avgSuccessRate = (double) totalSuccess / (totalCalls > 0 ? totalCalls : 1);
            group.put("avgSuccessRate", avgSuccessRate);
        }

        return result;
    }

    /**
     * 获取模型调用趋势（最近 N 分钟）
     * 注：简化实现，返回当前统计
     *
     * @param minutes 分钟数
     * @return 趋势数据
     */
    public List<Map<String, Object>> getCallTrend(final int minutes) {
        List<Map<String, Object>> trend = new ArrayList<>();
        
        // 当前实现返回当前时刻的统计
        // 未来可以实现时间序列存储
        Map<String, Object> currentPoint = new HashMap<>();
        currentPoint.put("timestamp", System.currentTimeMillis());
        currentPoint.put("totalCalls", statsRepository.getTotalCalls());
        currentPoint.put("totalSuccess", statsRepository.getTotalSuccess());
        currentPoint.put("totalFailure", statsRepository.getTotalFailure());
        trend.add(currentPoint);

        return trend;
    }

    /**
     * 刷新所有模型的 QPS
     */
    public void refreshQps() {
        for (ModelCallStats stats : statsRepository.getAllStats()) {
            double qps = statsRepository.getCurrentQps(stats.getServiceType(), stats.getModelName());
            stats.setCurrentQps(qps);
        }
    }

    /**
     * 定时任务：每分钟刷新 QPS 并持久化
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledRefresh() {
        refreshQps();
        statsRepository.persist();
        logger.debug("模型调用统计已刷新并持久化");
    }

    /**
     * 定时任务：每小时清理非活跃模型统计（超过 24 小时无调用）
     */
    @Scheduled(fixedRate = 3600000)
    public void scheduledCleanup() {
        long inactiveThreshold = 24 * 60 * 60 * 1000; // 24 小时
        long currentTime = System.currentTimeMillis();

        List<String> toRemove = new ArrayList<>();
        for (ModelCallStats stats : statsRepository.getAllStats()) {
            if (!stats.isActive() && currentTime - stats.getStatsStartTime() > inactiveThreshold) {
                toRemove.add(stats.getServiceType() + ":" + stats.getModelName());
            }
        }

        for (String key : toRemove) {
            String[] parts = key.split(":");
            if (parts.length == 2) {
                statsRepository.remove(parts[0], parts[1]);
            }
        }

        if (!toRemove.isEmpty()) {
            logger.info("清理了 {} 个非活跃模型统计", toRemove.size());
        }
    }
    
    /**
     * 清空所有模型统计
     */
    public void clearAllStats() {
        statsRepository.clearAll();
        logger.info("已清空所有模型统计");
    }
}
