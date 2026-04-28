package org.unreal.modelrouter.monitor.monitoring.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 监控功能启用条件
 * 当monitoring.metrics.enabled=true时启用监控功能
 */
public class MonitoringEnabledCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("monitoring.metrics.enabled", "true");
        return Boolean.parseBoolean(enabled);
    }
}