package org.unreal.modelrouter.ratelimit;

import org.unreal.modelrouter.config.ModelServiceRegistry;

/**
 * 限流上下文，包含限流所需的各种信息
 */
public class RateLimitContext {
    private final ModelServiceRegistry.ServiceType serviceType;
    private final String modelName;
    private final String clientIp;
    private final int tokens;

    public RateLimitContext(ModelServiceRegistry.ServiceType serviceType,
                            String modelName,
                            String clientIp,
                            int tokens) {
        this.serviceType = serviceType;
        this.modelName = modelName;
        this.clientIp = clientIp;
        this.tokens = tokens;
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
}
