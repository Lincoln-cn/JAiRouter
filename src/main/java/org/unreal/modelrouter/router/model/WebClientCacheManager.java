package org.unreal.modelrouter.router.model;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.monitor.tracing.client.RequestIdPropagationFilter;
import org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.router.http.WebClientPool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebClient 缓存管理器
 * 负责 WebClient 实例的创建和缓存
 *
 * @since v2.7.20
 */
@Component
public class WebClientCacheManager {

    private final Map<String, WebClient> cache = new ConcurrentHashMap<>();

    /**
     * 获取或创建 WebClient
     *
     * @param baseUrl 基础URL
     * @return WebClient 实例
     */
    public WebClient getOrCreate(final String baseUrl) {
        return cache.computeIfAbsent(baseUrl, this::createWebClient);
    }

    /**
     * 创建 WebClient
     * 优先使用追踪工厂创建，如果不可用则创建普通 WebClient
     *
     * @param baseUrl 基础URL
     * @return WebClient 实例
     */
    private WebClient createWebClient(final String baseUrl) {
        // R3-P0：出站前校验，覆盖历史脏数据（云元数据等）
        final String safeBaseUrl =
                org.unreal.modelrouter.config.core.helper.SsrfGuard.validateOutboundBaseUrl(baseUrl);
        try {
            TracingWebClientFactory tracingFactory =
                    ApplicationContextProvider.getBean(TracingWebClientFactory.class);
            return tracingFactory.createTracingWebClient(safeBaseUrl);
        } catch (Exception e) {
            // 追踪不可用（如 jairouter.tracing.enabled=false）：仍走 WebClientPool —— 出站解析守卫
            // （#266）只挂在它创建的客户端上，自建 WebClient 会绕过校验结果的绑定
            WebClientPool webClientPool = ApplicationContextProvider.getBean(WebClientPool.class);
            // 追踪关闭不代表请求 ID 不存在：RequestIdWebFilter 与 tracing 是两套机制，
            // 这里仍挂上透传过滤器，保证 X-Request-Id 能到上游（3.8.8）
            final RequestIdPropagationFilter requestIdFilter = resolveRequestIdFilter();
            if (requestIdFilter == null) {
                // 极简上下文（测试替身、自定义装配）里没有该 Bean：退回原有行为，
                // 不因为「追踪附属组件缺失」让整条出站路径失败
                return webClientPool.getOrCreate(safeBaseUrl);
            }
            return webClientPool.getOrCreate(safeBaseUrl, "request-id",
                    builder -> builder.filter(requestIdFilter));
        }
    }

    /**
     * 取请求标识透传过滤器。
     *
     * <p>用 {@code getBeanProvider(...).getIfAvailable()} 而不是 {@code getBean(...)}：这个方法运行在
     * 「追踪不可用」的降级分支里，若在这里再抛 {@code NoSuchBeanDefinitionException}，会把一次本可以
     * 正常出站的调用变成失败。真实运行装配下该 Bean 一定存在（{@code @Component}），返回 null 只出现
     * 在极简上下文与测试替身里。</p>
     */
    private static RequestIdPropagationFilter resolveRequestIdFilter() {
        final ApplicationContext context = ApplicationContextProvider.getApplicationContext();
        if (context == null) {
            return null;
        }
        return context.getBeanProvider(RequestIdPropagationFilter.class).getIfAvailable();
    }

    /**
     * 清除缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }
}
