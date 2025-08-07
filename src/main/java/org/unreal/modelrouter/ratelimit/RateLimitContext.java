package org.unreal.modelrouter.ratelimit;

import org.unreal.modelrouter.config.ModelServiceRegistry;


public class RateLimitContext {
    private final ModelServiceRegistry.ServiceType serviceType;
    private final String modelName;
    private final String clientIp;
    private final int tokens;
    private final String instanceId;
    private final String instanceUrl;

    // 带实例信息的构造函数
    public RateLimitContext(ModelServiceRegistry.ServiceType serviceType,
                            String modelName,
                            String clientIp,
                            int tokens,
                            String instanceId,
                            String instanceUrl) {
        this.serviceType = serviceType;
        this.modelName = modelName;
        this.clientIp = clientIp;
        this.tokens = tokens;
        this.instanceId = instanceId;
        this.instanceUrl = instanceUrl;
    }

    // 兼容旧版本的构造函数
    public RateLimitContext(ModelServiceRegistry.ServiceType serviceType,
                            String modelName,
                            String clientIp,
                            int tokens) {
        this(serviceType, modelName, clientIp, tokens, null, null);
    }

    // Getters
    public ModelServiceRegistry.ServiceType getServiceType() {
        return serviceType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getTokens() {
        return tokens;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public boolean hasInstanceInfo() {
        return instanceId != null && instanceUrl != null;
    }
}