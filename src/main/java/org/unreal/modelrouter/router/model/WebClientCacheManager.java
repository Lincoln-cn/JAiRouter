package org.unreal.modelrouter.router.model;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory;

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
        try {
            TracingWebClientFactory tracingFactory =
                    ApplicationContextProvider.getBean(TracingWebClientFactory.class);
            return tracingFactory.createTracingWebClient(baseUrl);
        } catch (Exception e) {
            // 追踪功能不可用，创建普通WebClient
            return WebClient.builder().baseUrl(baseUrl).build();
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
