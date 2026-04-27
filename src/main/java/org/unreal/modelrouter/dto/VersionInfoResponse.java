package org.unreal.modelrouter.dto;

import org.unreal.modelrouter.config.dto.RouterConfiguration;

import java.util.Map;

/**
 * 版本信息数据传输对象
 * 使用强类型 RouterConfiguration 替代 Map<String, Object>
 */
public class VersionInfoResponse {

    private Integer version;
    private RouterConfiguration config;
    private Boolean current;
    private String operation;
    private String operationDetail;
    private Long timestamp;

    // Getters and setters
    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public RouterConfiguration getConfig() {
        return config;
    }

    public void setConfig(final RouterConfiguration config) {
        this.config = config;
    }

    /**
     * 兼容旧代码：获取 Map 格式的配置
     */
    public Map<String, Object> getConfigAsMap() {
        return config != null ? config.toMap() : Map.of();
    }

    /**
     * 兼容旧代码：设置 Map 格式的配置
     */
    public void setConfigFromMap(final Map<String, Object> configMap) {
        this.config = RouterConfiguration.fromMap(configMap);
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(final Boolean current) {
        this.current = current;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(final String operation) {
        this.operation = operation;
    }

    public String getOperationDetail() {
        return operationDetail;
    }

    public void setOperationDetail(final String operationDetail) {
        this.operationDetail = operationDetail;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Long timestamp) {
        this.timestamp = timestamp;
    }
}
