package org.unreal.modelrouter.monitor.tracing.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.monitor.tracing.interceptor.BackendCallTracingInterceptor;
import org.unreal.modelrouter.router.http.WebClientPool;

/**
 * 追踪WebClient工厂
 *
 * 负责创建集成了追踪功能的WebClient实例，提供：
 * - 自动注入追踪拦截器
 * - 统一的WebClient配置
 * - 条件化的追踪功能启用
 *
 * 性能优化 (v2.7.0):
 * - 使用 WebClientPool 缓存 WebClient 实例
 * - 复用底层 TCP 连接，减少创建开销
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "jairouter.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingWebClientFactory {

    /** 缓存变体名：与其他出站客户端共享 baseUrl 时区分 tracing 拦截器配置（#699）。 */
    private static final String TRACING_VARIANT = "tracing";

    private final BackendCallTracingInterceptor tracingInterceptor;
    private final WebClientPool webClientPool;

    public TracingWebClientFactory(
            final BackendCallTracingInterceptor tracingInterceptor,
            final WebClientPool webClientPool) {
        this.tracingInterceptor = tracingInterceptor;
        this.webClientPool = webClientPool;
    }

    /**
     * 创建带有追踪功能的WebClient
     *
     * @param baseUrl 基础URL
     * @return 配置了追踪拦截器的WebClient
     */
    public WebClient createTracingWebClient(final String baseUrl) {
        // #699：给出稳定变体名，避免用 lambda 的 identity hash 当缓存键
        return webClientPool.getOrCreate(baseUrl, TRACING_VARIANT, builder -> {
            builder.filter(tracingInterceptor);
        });
    }

    /**
     * 为现有WebClient添加追踪功能
     *
     * @param existingClient 现有的WebClient
     * @return 添加了追踪功能的WebClient
     */
    public WebClient addTracingToWebClient(final WebClient existingClient) {
        return existingClient.mutate()
                .filter(tracingInterceptor)
                .build();
    }
}