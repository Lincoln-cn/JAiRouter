package org.unreal.modelrouter.router.loadbalancer.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingStatsAggregator.ServiceRoutingStats;

import java.util.List;
import java.util.Map;

/**
 * 路由监控 REST API 控制器
 * 提供监控数据查询、配置调整、暂停/恢复等功能
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/routing-monitor")
@RequiredArgsConstructor
public class RoutingMonitorController {

    private final RoutingMonitorService monitorService;
    private final RoutingEventRecorder eventRecorder;
    private final RoutingStatsAggregator statsAggregator;

    // ==================== 状态查询 ====================

    /**
     * 获取监控状态摘要
     */
    @GetMapping("/status")
    public ResponseEntity<RoutingMonitorService.MonitorStatusSummary> getStatus() {
        return ResponseEntity.ok(monitorService.getStatusSummary());
    }

    /**
     * 获取所有服务的路由统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, ServiceRoutingStats>> getAllStats() {
        return ResponseEntity.ok(statsAggregator.getAllStats());
    }

    /**
     * 获取指定服务的路由统计
     */
    @GetMapping("/stats/{serviceType}")
    public ResponseEntity<ServiceRoutingStats> getServiceStats(@PathVariable String serviceType) {
        ServiceRoutingStats stats = statsAggregator.getStats(serviceType);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取路由历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, List<RoutingEvent>>> getHistory(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(eventRecorder.getAllHistory(limit));
    }

    /**
     * 获取指定服务的路由历史记录
     */
    @GetMapping("/history/{serviceType}")
    public ResponseEntity<List<RoutingEvent>> getServiceHistory(
            @PathVariable String serviceType,
            @RequestParam(defaultValue = "100") int limit) {
        List<RoutingEvent> history = eventRecorder.getHistory(serviceType, limit);
        return ResponseEntity.ok(history);
    }

    // ==================== 配置调整 ====================

    /**
     * 更新采样率
     */
    @PutMapping("/config/sample-rate")
    public ResponseEntity<ConfigUpdateResponse> updateSampleRate(
            @RequestBody SampleRateRequest request) {
        if (request.sampleRate() < 0.0 || request.sampleRate() > 1.0) {
            return ResponseEntity.badRequest()
                    .body(new ConfigUpdateResponse(false, "Sample rate must be between 0.0 and 1.0"));
        }
        monitorService.updateSampleRate(request.sampleRate());
        return ResponseEntity.ok(new ConfigUpdateResponse(true,
                "Sample rate updated to " + (request.sampleRate() * 100) + "%"));
    }

    /**
     * 更新历史记录大小
     */
    @PutMapping("/config/history-size")
    public ResponseEntity<ConfigUpdateResponse> updateHistorySize(
            @RequestBody HistorySizeRequest request) {
        if (request.historySize() < 100 || request.historySize() > 10000) {
            return ResponseEntity.badRequest()
                    .body(new ConfigUpdateResponse(false, "History size must be between 100 and 10000"));
        }
        monitorService.updateHistorySize(request.historySize());
        return ResponseEntity.ok(new ConfigUpdateResponse(true,
                "History size updated to " + request.historySize()));
    }

    /**
     * 更新监控配置
     */
    @PutMapping("/config")
    public ResponseEntity<ConfigUpdateResponse> updateConfig(
            @RequestBody MonitorConfigRequest request) {
        StringBuilder message = new StringBuilder();

        if (request.sampleRate() != null) {
            if (request.sampleRate() >= 0.0 && request.sampleRate() <= 1.0) {
                monitorService.updateSampleRate(request.sampleRate());
                message.append("Sample rate: ").append(request.sampleRate() * 100).append("%. ");
            }
        }

        if (request.historySize() != null) {
            if (request.historySize() >= 100 && request.historySize() <= 10000) {
                monitorService.updateHistorySize(request.historySize());
                message.append("History size: ").append(request.historySize()).append(". ");
            }
        }

        if (message.length() == 0) {
            return ResponseEntity.badRequest()
                    .body(new ConfigUpdateResponse(false, "No valid configuration provided"));
        }

        return ResponseEntity.ok(new ConfigUpdateResponse(true, message.toString().trim()));
    }

    // ==================== 暂停/恢复 ====================

    /**
     * 暂停全局监控
     */
    @PostMapping("/pause")
    public ResponseEntity<ActionResponse> pause() {
        monitorService.pause();
        return ResponseEntity.ok(new ActionResponse(true, "Monitoring paused globally"));
    }

    /**
     * 恢复全局监控
     */
    @PostMapping("/resume")
    public ResponseEntity<ActionResponse> resume() {
        monitorService.resume();
        return ResponseEntity.ok(new ActionResponse(true, "Monitoring resumed globally"));
    }

    /**
     * 暂停指定服务的监控
     */
    @PostMapping("/pause/{serviceType}")
    public ResponseEntity<ActionResponse> pauseService(@PathVariable String serviceType) {
        monitorService.pauseService(serviceType);
        return ResponseEntity.ok(new ActionResponse(true,
                "Monitoring paused for service: " + serviceType));
    }

    /**
     * 恢复指定服务的监控
     */
    @PostMapping("/resume/{serviceType}")
    public ResponseEntity<ActionResponse> resumeService(@PathVariable String serviceType) {
        monitorService.resumeService(serviceType);
        return ResponseEntity.ok(new ActionResponse(true,
                "Monitoring resumed for service: " + serviceType));
    }

    // ==================== 历史记录管理 ====================

    /**
     * 清空指定服务的历史记录
     */
    @DeleteMapping("/history/{serviceType}")
    public ResponseEntity<ActionResponse> clearServiceHistory(@PathVariable String serviceType) {
        eventRecorder.clearHistory(serviceType);
        return ResponseEntity.ok(new ActionResponse(true,
                "History cleared for service: " + serviceType));
    }

    /**
     * 清空所有历史记录
     */
    @DeleteMapping("/history")
    public ResponseEntity<ActionResponse> clearAllHistory() {
        eventRecorder.clearAllHistory();
        return ResponseEntity.ok(new ActionResponse(true, "All history cleared"));
    }

    // ==================== 导出功能 ====================

    /**
     * 导出历史记录为 JSON
     */
    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportJson(
            @RequestParam(required = false) String serviceType,
            @RequestParam(defaultValue = "1000") int limit) {
        String json = eventRecorder.exportAsJson(serviceType, limit);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=routing-history.json")
                .body(json);
    }

    /**
     * 导出历史记录为 CSV
     */
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String serviceType,
            @RequestParam(defaultValue = "1000") int limit) {
        String csv = eventRecorder.exportAsCsv(serviceType, limit);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=routing-history.csv")
                .body(csv);
    }

    // ==================== 请求/响应 DTO ====================

    public record SampleRateRequest(double sampleRate) {}

    public record HistorySizeRequest(int historySize) {}

    public record MonitorConfigRequest(Double sampleRate, Integer historySize) {}

    public record ConfigUpdateResponse(boolean success, String message) {}

    public record ActionResponse(boolean success, String message) {}
}
