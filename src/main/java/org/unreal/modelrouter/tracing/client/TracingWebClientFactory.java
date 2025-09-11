package org.unreal.modelrouter.tracing.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.tracing.interceptor.BackendCallTracingInterceptor;

/**
 * 追踪WebClient工厂
 * 
 * 负责创建集成了追踪功能的WebClient实例，提供：
 * - 自动注入追踪拦截器
 * - 统一的WebClient配置
 * - 条件化的追踪功能启用
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "jairouter.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingWebClientFactory {
    
    private final BackendCallTracingInterceptor tracingInterceptor;
    
    public TracingWebClientFactory(BackendCallTracingInterceptor tracingInterceptor) {
        this.tracingInterceptor = tracingInterceptor;
    }
    
    /**
     * 创建带有追踪功能的WebClient
     * 
     * @param baseUrl 基础URL
     * @return 配置了追踪拦截器的WebClient
     */
    public WebClient createTracingWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(tracingInterceptor)
                .build();
    }
    
    /**
     * 为现有WebClient添加追踪功能
     * 
     * @param existingClient 现有的WebClient
     * @return 添加了追踪功能的WebClient
     */
    public WebClient addTracingToWebClient(WebClient existingClient) {
        return existingClient.mutate()
                .filter(tracingInterceptor)
                .build();
    }
}