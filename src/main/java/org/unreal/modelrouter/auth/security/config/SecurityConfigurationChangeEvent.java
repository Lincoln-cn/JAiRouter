package org.unreal.modelrouter.auth.security.config;

import java.time.LocalDateTime;

/**
 * 安全配置变更事件
 */
public class SecurityConfigurationChangeEvent {
    private final Object source;
    private final String changeId;
    private final String configType;
    private final Object oldValue;
    private final Object newValue;
    private final LocalDateTime timestamp;

    public SecurityConfigurationChangeEvent(final Object source, final String changeId ,final String configType,final Object oldValue,final Object newValue) {
        this.source = source;
        this.changeId = changeId;
        this.configType = configType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = LocalDateTime.now();
    }

    public String getChangeId() {
        return changeId;
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


}
