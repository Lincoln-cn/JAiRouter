package org.unreal.modelrouter.monitor.tracing.encryption;

import java.time.Duration;
import java.time.Instant;

/**
 * 追踪数据保留策略
 */
public class TraceRetentionPolicy {
    private final String policyName;
    private final Duration retentionDuration;

    public TraceRetentionPolicy(final String policyName, final Duration retentionDuration) {
        this.policyName = policyName;
        this.retentionDuration = retentionDuration;
    }

    public boolean shouldDelete(final Instant createdAt) {
        return Duration.between(createdAt, Instant.now()).compareTo(retentionDuration) > 0;
    }

    public String getPolicyName() {
        return policyName;
    }
    public Duration getRetentionDuration() {
        return retentionDuration;
    }
}
