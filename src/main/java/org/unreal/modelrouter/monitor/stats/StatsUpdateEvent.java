package org.unreal.modelrouter.monitor.stats;

/**
 * 统计更新事件
 *
 * 用于事件驱动的统计更新，避免同步阻塞。
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
public class StatsUpdateEvent {

    /**
     * 事件类型
     */
    public enum Type {
        /**
         * 调用成功
         */
        CALL_SUCCESS,

        /**
         * 调用失败
         */
        CALL_FAILURE,

        /**
         * 熔断触发
         */
        CIRCUIT_BREAKER,

        /**
         * 限流触发
         */
        RATE_LIMITED,

        /**
         * 错误码记录
         */
        ERROR_CODE
    }

    private final Type type;
    private final String serviceType;
    private final String modelName;
    private final long responseTime;
    private final String errorCode;
    private final long timestamp;

    /**
     * 创建统计更新事件
     */
    private StatsUpdateEvent(final Builder builder) {
        this.type = builder.type;
        this.serviceType = builder.serviceType;
        this.modelName = builder.modelName;
        this.responseTime = builder.responseTime;
        this.errorCode = builder.errorCode;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() { return type; }
    public String getServiceType() { return serviceType; }
    public String getModelName() { return modelName; }
    public long getResponseTime() { return responseTime; }
    public String getErrorCode() { return errorCode; }
    public long getTimestamp() { return timestamp; }

    /**
     * 创建成功事件
     */
    public static StatsUpdateEvent success(final String serviceType, final String modelName, final long responseTime) {
        return builder().type(Type.CALL_SUCCESS)
                .serviceType(serviceType)
                .modelName(modelName)
                .responseTime(responseTime)
                .build();
    }

    /**
     * 创建失败事件
     */
    public static StatsUpdateEvent failure(final String serviceType, final String modelName, final long responseTime) {
        return builder().type(Type.CALL_FAILURE)
                .serviceType(serviceType)
                .modelName(modelName)
                .responseTime(responseTime)
                .build();
    }

    /**
     * 创建熔断事件
     */
    public static StatsUpdateEvent circuitBreaker(final String serviceType, final String modelName) {
        return builder().type(Type.CIRCUIT_BREAKER)
                .serviceType(serviceType)
                .modelName(modelName)
                .build();
    }

    /**
     * 创建限流事件
     */
    public static StatsUpdateEvent rateLimited(final String serviceType, final String modelName) {
        return builder().type(Type.RATE_LIMITED)
                .serviceType(serviceType)
                .modelName(modelName)
                .build();
    }

    /**
     * 创建错误码事件
     */
    public static StatsUpdateEvent errorCode(final String serviceType, final String modelName, final String errorCode) {
        return builder().type(Type.ERROR_CODE)
                .serviceType(serviceType)
                .modelName(modelName)
                .errorCode(errorCode)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private Type type;
        private String serviceType;
        private String modelName;
        private long responseTime;
        private String errorCode;

        public Builder type(final Type type) {
            this.type = type;
            return this;
        }

        public Builder serviceType(final String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public Builder modelName(final String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder responseTime(final long responseTime) {
            this.responseTime = responseTime;
            return this;
        }

        public Builder errorCode(final String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public StatsUpdateEvent build() {
            return new StatsUpdateEvent(this);
        }
    }

    @Override
    public String toString() {
        return String.format("StatsUpdateEvent{type=%s, service=%s, model=%s, responseTime=%dms}",
                type, serviceType, modelName, responseTime);
    }
}
