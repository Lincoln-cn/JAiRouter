package org.unreal.modelrouter.monitor.tracing.encryption;

import java.time.Instant;

/**
 * 加密追踪数据实体
 */
public class EncryptedTraceData {
    private final String traceId;
    private final String dataType;
    private String encryptedData;
    private final Instant createdAt;
    private Instant updatedAt;

    public EncryptedTraceData(final String traceId, final String dataType, final String encryptedData,
                            final Instant createdAt, final Instant updatedAt) {
        this.traceId = traceId;
        this.dataType = dataType;
        this.encryptedData = encryptedData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getTraceId() {
        return traceId;
    }
    public String getDataType() {
        return dataType;
    }
    public String getEncryptedData() {
        return encryptedData;
    }
    public void setEncryptedData(final String encryptedData) {
        this.encryptedData = encryptedData;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
