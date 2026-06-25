package org.unreal.modelrouter.router.loadbalancer.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 负载均衡路由监控配置
 * 支持静态配置(application.yml)和运行时动态调整
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Component
@ConfigurationProperties(prefix = "jairouter.loadbalancer.routing-monitor")
public class RoutingMonitorConfig {

    /**
     * 是否启用路由监控
     */
    private boolean enabled = true;

    /**
     * 采样率 (0.0 - 1.0)
     * 0.1 表示 10% 的请求会被记录
     */
    private double sampleRate = 0.1;

    /**
     * 历史记录保留条数
     * 环形缓冲区大小，超过此数量会覆盖最旧的记录
     */
    private int historySize = 1000;

    /**
     * 是否启用 WebSocket 推送
     */
    private boolean websocketEnabled = true;

    /**
     * 统计数据刷新间隔(毫秒)
     * WebSocket 推送统计数据的频率
     */
    private long statsRefreshIntervalMs = 1000;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        if (sampleRate < 0.0) {
            this.sampleRate = 0.0;
        } else if (sampleRate > 1.0) {
            this.sampleRate = 1.0;
        } else {
            this.sampleRate = sampleRate;
        }
    }

    public int getHistorySize() {
        return historySize;
    }

    public void setHistorySize(int historySize) {
        if (historySize < 100) {
            this.historySize = 100;
        } else if (historySize > 10000) {
            this.historySize = 10000;
        } else {
            this.historySize = historySize;
        }
    }

    public boolean isWebsocketEnabled() {
        return websocketEnabled;
    }

    public void setWebsocketEnabled(boolean websocketEnabled) {
        this.websocketEnabled = websocketEnabled;
    }

    public long getStatsRefreshIntervalMs() {
        return statsRefreshIntervalMs;
    }

    public void setStatsRefreshIntervalMs(long statsRefreshIntervalMs) {
        this.statsRefreshIntervalMs = statsRefreshIntervalMs;
    }

    /**
     * 获取配置摘要
     */
    public ConfigSummary getSummary() {
        return new ConfigSummary(enabled, sampleRate, historySize, websocketEnabled);
    }

    /**
     * 配置摘要记录
     */
    public record ConfigSummary(
        boolean enabled,
        double sampleRate,
        int historySize,
        boolean websocketEnabled
    ) {}
}
