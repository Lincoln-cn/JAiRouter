package org.unreal.modelrouter.router.model;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
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
            return webClientPool.getOrCreate(safeBaseUrl);
        }
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
