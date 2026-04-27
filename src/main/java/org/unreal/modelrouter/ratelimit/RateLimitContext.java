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

    /**
     * 兼容旧版本的构造函数（不含实例信息）
     *
     * @deprecated 此构造函数不支持实例信息，功能不完整。
     *             请使用完整构造函数 {@link #RateLimitContext(ModelServiceRegistry.ServiceType, String, String, int, String, String)} 替代。
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 缺少实例信息
     *             RateLimitContext context = new RateLimitContext(serviceType, modelName, clientIp, tokens);
     *             
     *             // 新代码 - 包含完整实例信息
     *             RateLimitContext context = new RateLimitContext(
     *                 serviceType, modelName, clientIp, tokens, instanceId, instanceUrl
     *             );
     *             
     *             // 检查是否有实例信息
     *             if (context.hasInstanceInfo()) {
     *                 // 可以使用 instanceId 和 instanceUrl
     *             }
     *             }</pre>
     *             此构造函数将在 v3.0 版本中移除。
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param clientIp 客户端IP
     * @param tokens 令牌数
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
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