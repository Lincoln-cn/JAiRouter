package org.unreal.modelrouter.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 错误追踪配置属性
 * 
 * 配置错误追踪功能的各种参数。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "jairouter.monitoring.error-tracking")
public class ErrorTrackerProperties {
    
    /**
     * 是否启用错误追踪
     */
    private boolean enabled = true;
    
    /**
     * 错误聚合窗口大小（分钟）
     */
    private int aggregationWindowMinutes = 5;
    
    /**
     * 最大错误聚合数量
     */
    private int maxAggregations = 1000;
    
    /**
     * 是否启用异常堆栈脱敏
     */
    private boolean sanitizationEnabled = true;
    
    /**
     * 堆栈脱敏配置
     */
    private SanitizationConfig sanitization = new SanitizationConfig();
    
    /**
     * 指标配置
     */
    private MetricsConfig metrics = new MetricsConfig();
    
    /**
     * 堆栈脱敏配置
     */
    @Data
    public static class SanitizationConfig {
        
        /**
         * 是否启用堆栈脱敏
         */
        private boolean enabled = true;
        
        /**
         * 最大堆栈深度
         */
        private int maxStackDepth = 20;
        
        /**
         * 需要脱敏的包前缀
         */
        private List<String> sensitivePackages = new ArrayList<>(List.of(
            "org.unreal.modelrouter.security",
            "org.unreal.modelrouter.auth",
            "java.security",
            "javax.crypto"
        ));
        
        /**
         * 需要完全过滤的包前缀
         */
        private List<String> excludedPackages = new ArrayList<>(List.of(
            "sun.",
            "java.lang.reflect",
            "org.springframework.cglib",
            "net.sf.cglib"
        ));
        
        /**
         * 需要脱敏的字段名
         */
        private List<String> sensitiveFields = new ArrayList<>(List.of(
            "password",
            "token",
            "secret",
            "key",
            "credential"
        ));
    }
    
    /**
     * 指标配置
     */
    @Data
    public static class MetricsConfig {
        
        /**
         * 是否启用错误指标
         */
        private boolean enabled = true;
        
        /**
         * 错误计数器前缀
         */
        private String counterPrefix = "jairouter.errors";
        
        /**
         * 是否按错误类型分组
         */
        private boolean groupByErrorType = true;
        
        /**
         * 是否按操作分组
         */
        private boolean groupByOperation = true;
        
        /**
         * 是否记录错误持续时间
         */
        private boolean recordDuration = true;
        
        /**
         * 错误直方图桶配置
         */
        private double[] histogramBuckets = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0};
    }
}