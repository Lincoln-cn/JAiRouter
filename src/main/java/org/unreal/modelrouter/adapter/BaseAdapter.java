package org.unreal.modelrouter.adapter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.util.IpUtils;

public abstract class BaseAdapter implements ServiceCapability {

    protected final ModelServiceRegistry registry;

    public BaseAdapter(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取选中的实例
     */
    protected ModelRouterProperties.ModelInstance selectInstance(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        return registry.selectInstance(serviceType, modelName, clientIp);
    }

    /**
     * 记录调用完成
     */
    protected void recordCallComplete(
            ModelServiceRegistry.ServiceType serviceType,
            ModelRouterProperties.ModelInstance instance) {
        registry.recordCallComplete(serviceType, instance);
    }

    /**
     * 获取模型路径
     */
    protected String getModelPath(ModelServiceRegistry.ServiceType serviceType, String modelName) {
        return registry.getModelPath(serviceType, modelName);
    }

    /**
     * 转换请求体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformRequest(Object request, String adapterType) {
        // 默认不转换，直接返回原请求
        return request;
    }

    /**
     * 适配模型名称格式
     */
    protected String adaptModelName(String originalModelName) {
        // 默认不转换，直接返回原模型名称
        return originalModelName;
    }

    /**
     * 转换响应体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformResponse(Object response, String adapterType) {
        // 默认不转换，直接返回原响应
        return response;
    }

    /**
     * 获取授权头 - 子类可以重写此方法来处理不同的认证方式
     */
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        return authorization;
    }
}