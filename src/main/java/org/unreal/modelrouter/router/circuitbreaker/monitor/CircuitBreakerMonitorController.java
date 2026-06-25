package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
