package org.unreal.modelrouter.monitor.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Token 用量记录的保留策略。
 *
 * <p>此前 {@link TokenUsageService#deleteOldUsageRecords(java.time.LocalDateTime)}
 * 没有调用方，这张表只增不减（见 #984）。这个配置项给清理任务提供保留天数与开关。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.monitoring.token-usage")
public class TokenUsageRetentionProperties {

    /**
     * 是否启用过期记录清理，默认启用。
     */
    private boolean cleanupEnabled = true;

    /**
     * 保留天数，默认 90 天，与审计日志的保留口径保持一致。
     */
    private int retentionDays = 90;
}
