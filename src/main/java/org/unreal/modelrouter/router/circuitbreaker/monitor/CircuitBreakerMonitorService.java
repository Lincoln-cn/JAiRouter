package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 熔断器监控服务
 * 统一协调事件记录和状态管理
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerMonitorService {

    private final CircuitBreakerEventRecorder eventRecorder;
    private final CircuitBreakerMonitorConfig config;

    /**
     * 记录状态变化事件
     */
    public void recordStateChange(
            String instanceId,
            String instanceName,
            String serviceType,
            String previousState,
            String currentState,
            int failureCount,
            int successCount,
            String triggerReason) {

        if (!config.isEnabled()) {
            return;
        }

        CircuitBreakerEvent event = CircuitBreakerEvent.stateChange(
            instanceId,
            instanceName,
            serviceType,
            previousState,
            currentState,
            failureCount,
            successCount,
            triggerReason
        );

        eventRecorder.record(event);
        log.info("Circuit breaker state change: {}", event.toCompactString());
    }

    /**
     * 记录成功事件
     */
    public void recordSuccess(
            String instanceId,
            String instanceName,
            String serviceType,
            int failureCount,
            int successCount) {

        if (!config.isEnabled()) {
            return;
        }

        CircuitBreakerEvent event = CircuitBreakerEvent.success(
            instanceId,
            instanceName,
            serviceType,
            failureCount,
            successCount
        );

        eventRecorder.record(event);
        log.debug("Circuit breaker success: {}", event.toCompactString());
    }

    /**
     * 记录失败事件
     */
    public void recordFailure(
            String instanceId,
            String instanceName,
            String serviceType,
            int failureCount,
            int successCount) {

        if (!config.isEnabled()) {
            return;
        }

        CircuitBreakerEvent event = CircuitBreakerEvent.failure(
            instanceId,
            instanceName,
            serviceType,
            failureCount,
            successCount
        );

        eventRecorder.record(event);
        log.debug("Circuit breaker failure: {}", event.toCompactString());
    }

    /**
     * 获取事件记录器
     */
    public CircuitBreakerEventRecorder getEventRecorder() {
        return eventRecorder;
    }

    /**
     * 获取配置
     */
    public CircuitBreakerMonitorConfig getConfig() {
        return config;
    }

    /**
     * 更新采样率
     */
    public void updateSampleRate(double sampleRate) {
        config.setSampleRate(sampleRate);
        log.info("Sample rate updated to: {}%", sampleRate * 100);
    }

    /**
     * 更新历史记录大小
     */
    public void updateHistorySize(int historySize) {
        config.setHistorySize(historySize);
        eventRecorder.updateHistorySize(historySize);
        log.info("History size updated to: {}", historySize);
    }

    /**
     * 暂停监控
     */
    public void pause() {
        eventRecorder.pause();
    }

    /**
     * 恢复监控
     */
    public void resume() {
        eventRecorder.resume();
    }

    /**
     * 获取监控状态摘要
     */
    public MonitorStatusSummary getStatusSummary() {
        return new MonitorStatusSummary(
            config.isEnabled(),
            eventRecorder.isPaused(),
            config.getSampleRate(),
            config.getHistorySize(),
            eventRecorder.getTotalSampledCount()
        );
    }

    /**
     * 监控状态摘要
     */
    public record MonitorStatusSummary(
        boolean enabled,
        boolean paused,
        double sampleRate,
        int historySize,
        long totalSampledCount
    ) {}
}
