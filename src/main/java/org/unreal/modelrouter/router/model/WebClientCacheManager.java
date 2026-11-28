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
        return getOrCreate(baseUrl, false);
    }

    /**
     * 取该 baseUrl 对应的 WebClient（按是否流式区分缓存条目）。
     *
     * <p>流式与非流式用的是两套传输层超时（{@code WebClientPool} 的
     * {@code streaming} 变体 = 600s，默认变体 = 60s，#701），因此必须分开缓存：
     * 共用一个条目会让先创建的一方决定另一方的超时。</p>
     *
     * @param baseUrl   基础 URL
     * @param streaming true 表示这是 SSE 流式请求，需要更长的传输层超时
     * @return WebClient
     */
    public WebClient getOrCreate(final String baseUrl, final boolean streaming) {
        return cache.computeIfAbsent(streaming ? baseUrl + "|streaming" : baseUrl,
                key -> createWebClient(baseUrl, streaming));
    }

    /**
     * 创建 WebClient
     * 优先使用追踪工厂创建，如果不可用则创建普通 WebClient
     *
     * @param baseUrl 基础URL
     * @return WebClient 实例
     */
    private WebClient createWebClient(final String baseUrl, final boolean streaming) {
        // R3-P0：出站前校验，覆盖历史脏数据（云元数据等）
        final String safeBaseUrl =
                org.unreal.modelrouter.config.core.helper.SsrfGuard.validateOutboundBaseUrl(baseUrl);
        try {
            TracingWebClientFactory tracingFactory =
                    ApplicationContextProvider.getBean(TracingWebClientFactory.class);
            return streaming
                    ? tracingFactory.createTracingWebClient(safeBaseUrl, true)
                    : tracingFactory.createTracingWebClient(safeBaseUrl);
        } catch (Exception e) {
            // 追踪不可用（如 jairouter.tracing.enabled=false）：仍走 WebClientPool —— 出站解析守卫
            // （#266）只挂在它创建的客户端上，自建 WebClient 会绕过校验结果的绑定
            WebClientPool webClientPool = ApplicationContextProvider.getBean(WebClientPool.class);
            // 追踪关闭不代表请求 ID 不存在：RequestIdWebFilter 与 tracing 是两套机制，
            // 这里仍挂上透传过滤器，保证 X-Request-Id 能到上游（3.8.8）
            final RequestIdPropagationFilter requestIdFilter = resolveRequestIdFilter();
            // 追踪关闭不代表请求是流式的——变体必须跟着 streaming 走，
            // 否则流式请求会被降到默认 60s 传输超时，长回答在中途被掐断
            final String requestIdVariant = streaming ? "streaming-request-id" : "request-id";
            if (requestIdFilter == null) {
                // 极简上下文（测试替身、自定义装配）里没有该 Bean：退回原有行为，
                // 不因为「追踪附属组件缺失」让整条出站路径失败
                return streaming
                        ? webClientPool.getOrCreate(safeBaseUrl, "streaming", builder -> { })
                        : webClientPool.getOrCreate(safeBaseUrl);
            }
            return webClientPool.getOrCreate(safeBaseUrl, requestIdVariant,
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
