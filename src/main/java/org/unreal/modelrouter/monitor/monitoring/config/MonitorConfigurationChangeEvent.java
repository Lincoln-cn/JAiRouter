package org.unreal.modelrouter.monitor.monitoring.config;

import java.time.LocalDateTime;

/**
 * 配置变更事件
 * 当监控配置发生变更时发布的事件
 */
public class MonitorConfigurationChangeEvent {

    private final Object source;
    private final String changeId;
    private final String configType;
    private final Object oldValue;
    private final Object newValue;
    private final LocalDateTime timestamp;

    public MonitorConfigurationChangeEvent(final Object source , final String changeId, final String configType,final Object oldValue,final Object newValue) {
        this.source = source;
        this.changeId = changeId;
        this.configType = configType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public Object getSource() {
        return source;
    }

    public String getConfigType() {
        return configType;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getChangeId() {
        return this.changeId;
    }
}