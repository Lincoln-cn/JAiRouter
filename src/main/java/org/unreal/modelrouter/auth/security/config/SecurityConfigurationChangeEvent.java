package org.unreal.modelrouter.auth.security.config;

import java.time.LocalDateTime;

/**
 * 安全配置变更事件
 *
 * <p>安全配置写入时的变更通知对象：变更 ID、配置类型、旧值/新值与时间戳。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was meant to be the security-side config-change notification object
 * (changeId, configType, old/new value, timestamp, with {@code source} kept for logging). Config-change
 * notification actually runs through {@code config.core.event.ConfigurationChangedEvent} (an
 * {@code ApplicationEvent} published by {@code ConfigPersistenceService} on every persist) and, for the
 * monitoring config, {@code MonitorConfigurationChangeEvent}; nothing in main publishes or listens to
 * this class.
 *
 * <p><b>Before deleting:</b> no publisher, no listener and no serialized shape; a full compile is enough.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Deprecated(forRemoval = true)
public class SecurityConfigurationChangeEvent {
    private final Object source;
    private final String changeId;
    private final String configType;
    private final Object oldValue;
    private final Object newValue;
    private final LocalDateTime timestamp;

    public SecurityConfigurationChangeEvent(final Object source, final String changeId,
            final String configType, final Object oldValue, final Object newValue) {
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
