package org.unreal.modelrouter.router.circuitbreaker.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 熔断器监控配置
 * 支持静态配置(application.yml)和运行时动态调整
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Component
@ConfigurationProperties(prefix = "jairouter.circuit-breaker.monitor")
public class CircuitBreakerMonitorConfig {

    /**
     * 是否启用熔断器监控
     */
    private boolean enabled = true;

    /**
     * 采样率 (0.0 - 1.0)
     * 仅对 SUCCESS/FAILURE 事件生效，STATE_CHANGE 事件必须记录
     */
    private double sampleRate = 0.1;

    /**
     * 历史记录保留条数
     */
    private int historySize = 500;

    /**
     * 是否启用 WebSocket 推送
     */
    private boolean websocketEnabled = true;

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
        if (historySize < 50) {
            this.historySize = 50;
        } else if (historySize > 5000) {
            this.historySize = 5000;
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
}
