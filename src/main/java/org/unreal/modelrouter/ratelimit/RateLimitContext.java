package org.unreal.modelrouter.ratelimit;

import org.unreal.modelrouter.model.ModelServiceRegistry;


public class RateLimitContext {
    private final ModelServiceRegistry.ServiceType serviceType;
    private final String modelName;
    private final String clientIp;
    private final int tokens;
    private final String instanceId;
    private final String instanceUrl;

    // 带实例信息的构造函数
    public RateLimitContext(final ModelServiceRegistry.ServiceType serviceType,
                            final String modelName,
                            final String clientIp,
                            final int tokens,
                            final String instanceId,
                            final String instanceUrl) {
        this.serviceType = serviceType;
        this.modelName = modelName;
        this.clientIp = clientIp;
        this.tokens = tokens;
        this.instanceId = instanceId;
        this.instanceUrl = instanceUrl;
    }

    // 兼容旧版本的构造函数
    public RateLimitContext(final ModelServiceRegistry.ServiceType serviceType,
                            final String modelName,
                            final String clientIp,
                            final int tokens) {
        this(serviceType, modelName, clientIp, tokens, null, null);
    }

    /**
     * 获取服务类型
     * @return 服务类型
     */
    public ModelServiceRegistry.ServiceType getServiceType() {
        return serviceType;
    }

    /**
     * 获取模型名称
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取客户端IP
     * @return 客户端IP
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * 获取令牌数
     * @return 令牌数
     */
    public int getTokens() {
        return tokens;
    }

    /**
     * 获取实例ID
     * @return 实例ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 获取实例URL
     * @return 实例URL
     */
    public String getInstanceUrl() {
        return instanceUrl;
    }

    /**
     * 检查是否包含实例信息
     * @return 是否包含实例信息
     */
    public boolean hasInstanceInfo() {
        return instanceId != null && instanceUrl != null;
    }
}