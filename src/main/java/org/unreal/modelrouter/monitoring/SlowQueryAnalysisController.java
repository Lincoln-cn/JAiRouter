package org.unreal.modelrouter.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.monitoring.SlowQueryDetector.SlowQueryStats;
import org.unreal.modelrouter.monitoring.alert.SlowQueryAlertService;
import org.unreal.modelrouter.monitoring.alert.SlowQueryAlertStats;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 慢查询统计和趋势分析控制器
 * 提供慢查询统计信息的查询接口
 */
@RestController
@RequestMapping("/api/monitoring/slow-queries")
public class SlowQueryAnalysisController {
    
    private final SlowQueryDetector slowQueryDetector;
    private final PerformanceTracker performanceTracker;
    
    // 慢查询告警服务（可选）
    @Autowired(required = false)
    private SlowQueryAlertService slowQueryAlertService;
    
    public SlowQueryAnalysisController(SlowQueryDetector slowQueryDetector, 
                                     PerformanceTracker performanceTracker) {
        this.slowQueryDetector = slowQueryDetector;
        this.performanceTracker = performanceTracker;
    }
    
    /**
     * 获取慢查询统计信息
     * @return 慢查询统计信息
     */
    @GetMapping("/stats")
    public Map<String, SlowQueryStats> getSlowQueryStats() {
        return slowQueryDetector.getAllSlowQueryStats();
    }
    
    /**
     * 获取特定操作的慢查询统计信息
     * @param operationName 操作名称
     * @return 慢查询统计信息
     */
    @GetMapping("/stats/{operationName}")
    public SlowQueryStats getSlowQueryStatsByOperation(@PathVariable String operationName) {
        return slowQueryDetector.getSlowQueryStats(operationName);
    }
    
    /**
     * 获取慢查询总数
     * @return 慢查询总数
     */
    @GetMapping("/count")
    public long getTotalSlowQueryCount() {
        return slowQueryDetector.getTotalSlowQueryCount();
    }
    
    /**
     * 获取性能热点
     * @param limit 结果数量限制，默认为10
     * @return 性能热点列表
     */
    @GetMapping("/hotspots")
    public List<PerformanceTracker.PerformanceHotspot> getPerformanceHotspots(
            @RequestParam(defaultValue = "10") int limit) {
        return performanceTracker.getPerformanceHotspots(limit);
    }
    
    /**
     * 重置统计信息
     */
    @DeleteMapping("/stats")
    public void resetStats() {
        slowQueryDetector.resetStats();
        performanceTracker.clearStats();
    }
    
    /**
     * 获取慢查询趋势数据
     * @return 慢查询趋势数据
     */
    @GetMapping("/trends")
    public Map<String, Object> getSlowQueryTrends() {
        Map<String, Object> trends = new java.util.HashMap<>();
        
        // 获取所有操作统计信息
        Map<String, PerformanceTracker.OperationStats> allStats = performanceTracker.getAllOperationStats();
        
        // 过滤出有慢查询的操作
        Map<String, PerformanceTracker.OperationStats> slowOperations = allStats.entrySet().stream()
                .filter(entry -> slowQueryDetector.getSlowQueryStats(entry.getKey()).getCount() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        trends.put("slowOperations", slowOperations);
        trends.put("totalSlowQueries", slowQueryDetector.getTotalSlowQueryCount());
        
        return trends;
    }
    
    // ===========================================
    // 慢查询告警管理接口
    // ===========================================
    
    /**
     * 获取慢查询告警统计信息
     * @return 告警统计信息
     */
    @GetMapping("/alerts/stats")
    public SlowQueryAlertStats getAlertStats() {
        if (slowQueryAlertService == null) {
            return SlowQueryAlertStats.builder()
                    .totalAlertsTriggered(0L)
                    .totalAlertsSuppressed(0L)
                    .activeAlertKeys(0)
                    .activeOperations(java.util.Set.of())
                    .build();
        }
        return slowQueryAlertService.getAlertStats();
    }
    
    /**
     * 重置慢查询告警统计
     */
    @DeleteMapping("/alerts/stats")
    public void resetAlertStats() {
        if (slowQueryAlertService != null) {
            slowQueryAlertService.resetAlertStats();
        }
    }
    
    /**
     * 获取告警系统状态
     * @return 告警系统状态
     */
    @GetMapping("/alerts/status")
    public Map<String, Object> getAlertSystemStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("alertServiceEnabled", slowQueryAlertService != null);
        
        if (slowQueryAlertService != null) {
            SlowQueryAlertStats stats = slowQueryAlertService.getAlertStats();
            status.put("alertStats", stats);
            status.put("alertTriggerRate", stats.getAlertTriggerRate());
            status.put("alertSuppressionRate", stats.getAlertSuppressionRate());
            status.put("averageAlertsPerOperation", stats.getAverageAlertsPerOperation());
        } else {
            status.put("message", "慢查询告警服务未启用");
        }
        
        return status;
    }
}