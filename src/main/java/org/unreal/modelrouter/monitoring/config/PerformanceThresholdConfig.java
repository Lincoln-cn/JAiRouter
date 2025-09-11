package org.unreal.modelrouter.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能阈值配置类
 * 用于配置不同类型操作的性能阈值
 */
@Component
@ConfigurationProperties(prefix = "monitoring.performance.thresholds")
public class PerformanceThresholdConfig {

    /**
     * 默认阈值(毫秒)
     */
    private long defaultThreshold = 1000;

    /**
     * 操作类型对应的阈值映射
     * key: 操作类型
     * value: 阈值(毫秒)
     */
    private Map<String, Long> operationThresholds = new HashMap<>();

    public long getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(long defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }

    public Map<String, Long> getOperationThresholds() {
        return operationThresholds;
    }

    public void setOperationThresholds(Map<String, Long> operationThresholds) {
        this.operationThresholds = operationThresholds;
    }
}