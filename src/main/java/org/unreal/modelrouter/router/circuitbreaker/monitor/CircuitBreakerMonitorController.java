package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 熔断器监控 REST API 控制器
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/circuit-breaker-monitor")
@RequiredArgsConstructor
public class CircuitBreakerMonitorController {

    private final CircuitBreakerMonitorService monitorService;
    private final CircuitBreakerEventRecorder eventRecorder;
    private final CircuitBreakerAdminService adminService;

    /**
     * 熔断器实例视图：运行时状态 + 实例名/服务类型/上游地址（供监控页表格与统计卡使用）。
     *
     * @return 当前内存中的所有熔断器
     */
    @GetMapping("/instances")
    public ResponseEntity<List<CircuitBreakerInstanceView>> listInstances() {
        return ResponseEntity.ok(adminService.listInstances());
    }

    /**
     * 手动重置单个实例：内存状态置 CLOSED、清零计数、删除持久化状态、写 MANUAL_RESET 历史。
     *
     * @param instanceId 实例 UUID
     * @param request 可选的操作者备注
     * @return 重置结果；实例当前没有熔断器时返回 404
     */
    @PostMapping("/instances/{instanceId}/reset")
    public Mono<ResponseEntity<RouterResponse<CircuitBreakerResetResult>>> resetInstance(
            @PathVariable final String instanceId,
            @RequestBody(required = false) final ResetRequest request) {
        String reason = request != null ? request.reason() : null;
        return adminService.reset(instanceId, reason)
                .map(result -> ResponseEntity.ok(RouterResponse.success(result, "熔断器已重置")))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.<CircuitBreakerResetResult>error("该实例当前没有熔断器记录", "CIRCUIT_BREAKER_NOT_FOUND")));
    }

    /**
     * 批量手动重置：显式实例列表优先；未给实例时按服务类型与状态过滤，避免“一键清空”误伤。
     *
     * @param request 过滤条件与操作者备注
     * @return 命中数与逐个结果
     */
    @PostMapping("/instances/reset")
    public Mono<ResponseEntity<RouterResponse<CircuitBreakerBatchResetResult>>> resetInstances(
            @RequestBody final BatchResetRequest request) {
        boolean noTarget = (request.instanceIds() == null || request.instanceIds().isEmpty())
                && (request.serviceType() == null || request.serviceType().isBlank())
                && (request.states() == null || request.states().isEmpty());
        if (noTarget) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("请指定实例列表，或至少一个过滤条件（serviceType / states）", "INVALID_REQUEST")));
        }
        return adminService.resetBatch(request.instanceIds(), request.serviceType(), request.states(), request.reason())
                .map(result -> ResponseEntity.ok(RouterResponse.success(result,
                        "已重置 " + result.reset().size() + " 个熔断器")));
    }

    @GetMapping("/status")
    public ResponseEntity<CircuitBreakerMonitorService.MonitorStatusSummary> getStatus() {
        return ResponseEntity.ok(monitorService.getStatusSummary());
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, List<CircuitBreakerEvent>>> getHistory(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(eventRecorder.getAllHistory(limit));
    }

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

    @PutMapping("/config/history-size")
    public ResponseEntity<ConfigUpdateResponse> updateHistorySize(
            @RequestBody HistorySizeRequest request) {
        if (request.historySize() < 50 || request.historySize() > 5000) {
            return ResponseEntity.badRequest()
                    .body(new ConfigUpdateResponse(false, "History size must be between 50 and 5000"));
        }
        monitorService.updateHistorySize(request.historySize());
        return ResponseEntity.ok(new ConfigUpdateResponse(true,
                "History size updated to " + request.historySize()));
    }

    @PostMapping("/pause")
    public ResponseEntity<ActionResponse> pause() {
        monitorService.pause();
        return ResponseEntity.ok(new ActionResponse(true, "Monitoring paused"));
    }

    @PostMapping("/resume")
    public ResponseEntity<ActionResponse> resume() {
        monitorService.resume();
        return ResponseEntity.ok(new ActionResponse(true, "Monitoring resumed"));
    }

    @DeleteMapping("/history")
    public ResponseEntity<ActionResponse> clearAllHistory() {
        eventRecorder.clearAllHistory();
        return ResponseEntity.ok(new ActionResponse(true, "All history cleared"));
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportJson(
            @RequestParam(defaultValue = "500") int limit) {
        String json = eventRecorder.exportAsJson(limit);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=circuit-breaker-history.json")
                .body(json);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(defaultValue = "500") int limit) {
        String csv = eventRecorder.exportAsCsv(limit);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=circuit-breaker-history.csv")
                .body(csv);
    }

    public record SampleRateRequest(double sampleRate) {}
    public record HistorySizeRequest(int historySize) {}
    public record ConfigUpdateResponse(boolean success, String message) {}
    public record ActionResponse(boolean success, String message) {}
    public record ResetRequest(String reason) {}
    public record BatchResetRequest(List<String> instanceIds, String serviceType, List<String> states, String reason) {}
}
