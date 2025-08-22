package org.unreal.modelrouter.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 跟踪配置类
 * 用于管理分布式追踪相关的配置参数
 */
public class TraceConfig {
    
    /** 是否启用追踪 */
    private boolean enabled;
    
    /** 采样率 (0.0到1.0之间) */
    private double samplingRate;
    
    /** 服务名称 */
    private String serviceName;
    
    /** 追踪导出器类型 */
    private String exporterType;
    
    /** 追踪导出器配置 */
    private Map<String, String> exporterConfig;
    
    /** 配置版本 */
    private int version;
    
    public TraceConfig() {
        this.enabled = false;
        this.samplingRate = 1.0;
        this.serviceName = "model-router";
        this.exporterType = "logging";
        this.exporterConfig = new HashMap<>();
        this.version = 1;
    }
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public double getSamplingRate() {
        return samplingRate;
    }
    
    public void setSamplingRate(double samplingRate) {
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException("采样率必须在0.0到1.0之间");
        }
        this.samplingRate = samplingRate;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getExporterType() {
        return exporterType;
    }
    
    public void setExporterType(String exporterType) {
        this.exporterType = exporterType;
    }
    
    public Map<String, String> getExporterConfig() {
        return exporterConfig;
    }
    
    public void setExporterConfig(Map<String, String> exporterConfig) {
        this.exporterConfig = exporterConfig != null ? new HashMap<>(exporterConfig) : new HashMap<>();
    }
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
    
    /**
     * 将配置转换为Map表示
     */
    public Map<String, Object> toMap() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("enabled", enabled);
        configMap.put("samplingRate", samplingRate);
        configMap.put("serviceName", serviceName);
        configMap.put("exporterType", exporterType);
        configMap.put("exporterConfig", exporterConfig);
        configMap.put("version", version);
        return configMap;
    }
    
    /**
     * 从Map创建配置
     */
    @SuppressWarnings("unchecked")
    public static TraceConfig fromMap(Map<String, Object> configMap) {
        TraceConfig config = new TraceConfig();
        
        if (configMap.containsKey("enabled")) {
            config.setEnabled((Boolean) configMap.get("enabled"));
        }
        
        if (configMap.containsKey("samplingRate")) {
            config.setSamplingRate(((Number) configMap.get("samplingRate")).doubleValue());
        }
        
        if (configMap.containsKey("serviceName")) {
            config.setServiceName((String) configMap.get("serviceName"));
        }
        
        if (configMap.containsKey("exporterType")) {
            config.setExporterType((String) configMap.get("exporterType"));
        }
        
        if (configMap.containsKey("exporterConfig") && configMap.get("exporterConfig") instanceof Map) {
            Map<String, String> exporterConfig = (Map<String, String>) configMap.get("exporterConfig");
            config.setExporterConfig(exporterConfig);
        }
        
        if (configMap.containsKey("version")) {
            config.setVersion((Integer) configMap.get("version"));
        }
        
        return config;
    }
}