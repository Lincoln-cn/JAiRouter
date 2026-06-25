package org.unreal.modelrouter.router.loadbalancer.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.List;

/**
 * 路由监控服务
 * 统一协调事件记录和统计聚合
 * 在负载均衡选择实例后调用 recordSelection 进行埋点
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingMonitorService {

    private final RoutingEventRecorder eventRecorder;
    private final RoutingStatsAggregator statsAggregator;
    private final RoutingMonitorConfig config;

    /**
     * 记录实例选择事件（埋点入口）
     * 在 LoadBalancer.selectInstance() 返回后调用此方法
     *
     * @param serviceType 服务类型
     * @param strategy 负载均衡策略
     * @param selectedInstance 选中的实例
     * @param clientId 客户端标识 (IP地址或请求key)
     * @param candidateCount 候选实例数量
     * @param selectionTimeMs 选择耗时(毫秒)
     */
    public void recordSelection(
            String serviceType,
            String strategy,
            ModelRouterProperties.ModelInstance selectedInstance,
            String clientId,
            int candidateCount,
            long selectionTimeMs) {
        recordSelection(serviceType, null, strategy, selectedInstance, clientId, candidateCount, selectionTimeMs);
    }

    /**
     * 记录实例选择事件（包含模型名，简化版本不计时）
     */
    public void recordSelection(
            String serviceType,
            String modelName,
            String strategy,
            ModelRouterProperties.ModelInstance selectedInstance,
            String clientId,
            int candidateCount) {
        recordSelection(serviceType, modelName, strategy, selectedInstance, clientId, candidateCount, 0L);
    }

    /**
     * 记录实例选择事件（包含模型名）
     */
    public void recordSelection(
            String serviceType,
            String modelName,
            String strategy,
            ModelRouterProperties.ModelInstance selectedInstance,
            String clientId,
            int candidateCount,
            long selectionTimeMs) {

        if (!config.isEnabled()) {
            return;
        }

        // 创建路由事件
        RoutingEvent event = RoutingEvent.of(
            serviceType,
            modelName,
            strategy,
            selectedInstance.getInstanceId(),
            selectedInstance.getBaseUrl(),
            clientId,
            candidateCount,
            selectionTimeMs
        );

        // 记录事件（包含采样判断）
        boolean sampled = eventRecorder.record(event);

        // 更新统计（不管是否采样，都更新统计）
        statsAggregator.update(event);

        if (sampled) {
            log.debug("Routing selection recorded: {} -> {} ({})",
                serviceType, selectedInstance.getInstanceId(), strategy);
        }
    }

    /**
     * 记录实例选择事件（简化版本，不计时）
     */
    public void recordSelection(
            String serviceType,
            String strategy,
            ModelRouterProperties.ModelInstance selectedInstance,
            String clientId,
            int candidateCount) {
        recordSelection(serviceType, strategy, selectedInstance, clientId, candidateCount, 0L);
    }

    /**
     * 获取事件记录器
     */
    public RoutingEventRecorder getEventRecorder() {
        return eventRecorder;
    }

    /**
     * 获取统计聚合器
     */
    public RoutingStatsAggregator getStatsAggregator() {
        return statsAggregator;
    }

    /**
     * 获取配置
     */
    public RoutingMonitorConfig getConfig() {
        return config;
    }

    /**
     * 更新采样率（运行时调整）
     */
    public void updateSampleRate(double sampleRate) {
        config.setSampleRate(sampleRate);
        log.info("Sample rate updated to: {}%", sampleRate * 100);
    }

    /**
     * 更新历史记录大小（运行时调整）
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
     * 暂停指定服务的监控
     */
    public void pauseService(String serviceType) {
        eventRecorder.pauseService(serviceType);
    }

    /**
     * 恢复指定服务的监控
     */
    public void resumeService(String serviceType) {
        eventRecorder.resumeService(serviceType);
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
            eventRecorder.getTotalSampledCount(),
            eventRecorder.getPausedServices()
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
        long totalSampledCount,
        List<String> pausedServices
    ) {}
}
