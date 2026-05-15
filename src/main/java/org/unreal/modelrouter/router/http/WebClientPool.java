package org.unreal.modelrouter.router.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebClient 连接池
 * 
 * 缓存 WebClient 实例，复用底层 TCP 连接，避免每次请求都创建新的 WebClient。
 * 
 * 性能优化 (v2.7.0):
 * - WebClient 实例复用，减少对象创建开销
 * - 底层 Netty 连接池自动管理
 * - 基于 ConcurrentHashMap 的线程安全缓存
 * - 支持自定义 WebClient.Builder 配置
 * 
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Component
public class WebClientPool {

    private static final Logger logger = LoggerFactory.getLogger(WebClientPool.class);

    private final Map<String, WebClient> clientCache = new ConcurrentHashMap<>();

    /** 连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 10000;

    /** 读写超时时间（秒） */
    private static final int IO_TIMEOUT_SECONDS = 60;

    /** 统计：命中次数 */
    private final AtomicLong hitCount = new AtomicLong(0);

    /** 统计：未命中次数 */
    private final AtomicLong missCount = new AtomicLong(0);

    public WebClientPool() {
        logger.info("WebClientPool 初始化完成");
    }

    /**
     * 获取或创建 WebClient 实例（无自定义配置）
     * 
     * @param baseUrl 基础 URL
     * @return 缓存或新创建的 WebClient 实例
     */
    public WebClient getOrCreate(String baseUrl) {
        return getOrCreate(baseUrl, null);
    }

    /**
     * 获取或创建 WebClient 实例（带自定义配置）
     * 
     * 注意：配置器只在首次创建时调用，后续命中缓存时不会调用
     * 
     * @param baseUrl 基础 URL
     * @param configurator WebClient.Builder 配置器（可为 null）
     * @return 缓存或新创建的 WebClient 实例
     */
    public WebClient getOrCreate(String baseUrl, Consumer<WebClient.Builder> configurator) {
        // 使用 baseUrl + configurator hashCode 作为缓存 key
        String cacheKey = configurator != null 
                ? baseUrl + "#" + configurator.hashCode() 
                : baseUrl;
        
        WebClient cached = clientCache.get(cacheKey);
        if (cached != null) {
            hitCount.incrementAndGet();
            return cached;
        }

        missCount.incrementAndGet();
        return clientCache.computeIfAbsent(cacheKey, key -> createWebClient(baseUrl, configurator));
    }

    /**
     * 创建带连接池的 WebClient
     */
    private WebClient createWebClient(String baseUrl, Consumer<WebClient.Builder> configurator) {
        logger.debug("创建 WebClient: baseUrl={}", baseUrl);

        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .responseTimeout(Duration.ofSeconds(IO_TIMEOUT_SECONDS));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        // 应用自定义配置
        if (configurator != null) {
            configurator.accept(builder);
        }

        return builder.build();
    }

    /**
     * 清理指定 baseUrl 的缓存
     */
    public void evict(String baseUrl) {
        // 移除所有以 baseUrl 开头的缓存项
        clientCache.keySet().removeIf(key -> key.startsWith(baseUrl));
        logger.debug("WebClient 缓存已清理: baseUrl={}", baseUrl);
    }

    /**
     * 清理所有缓存
     */
    public void evictAll() {
        clientCache.clear();
        logger.debug("WebClient 缓存已全部清理");
    }

    /**
     * 获取缓存统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(
                clientCache.size(),
                hitCount.get(),
                missCount.get()
        );
    }

    /**
     * 缓存统计信息
     */
    public record PoolStats(
            long size,
            long hitCount,
            long missCount
    ) {
        public double hitRate() {
            long total = hitCount + missCount;
            return total > 0 ? (double) hitCount / total : 0.0;
        }
    }
}
