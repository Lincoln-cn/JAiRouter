package org.unreal.modelrouter.router.http;

import io.netty.channel.ChannelOption;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.config.core.helper.SsrfGuard;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebClient 连接池：所有出站 {@link WebClient} 的唯一创建入口（含 SSRF 门闩）。
 *
 * <p>缓存 WebClient 实例以复用底层 TCP 连接，避免每次请求重建。</p>
 *
 * <h2>连接参数外部化（#182）</h2>
 * <p>此前下列参数写死在代码里，多副本/容器部署无法按实例资源规格调整：</p>
 * <ul>
 *   <li>{@code connect-timeout-ms} —— <b>注意</b>：外部化之前代码里虽然声明了
 *       {@code CONNECT_TIMEOUT_MS = 10000}，但<b>从未被任何地方使用</b>（没有
 *       {@code ChannelOption.CONNECT_TIMEOUT_MILLIS}），连接超时实际未生效；本次一并接上。</li>
 *   <li>{@code response-timeout-seconds} —— 原先硬编码 60s。</li>
 *   <li>连接池上限 —— 原先未显式设置 {@link ConnectionProvider}，使用 Reactor Netty 默认；
 *       现在可配置最大连接数、等待获取连接的排队超时、空闲回收时间。</li>
 * </ul>
 *
 * <p><b>默认值与外部化之前保持一致</b>（10s / 60s / 500 / 60s / 60s），因此不改配置时行为不变。</p>
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Component
public class WebClientPool {

    private static final Logger logger = LoggerFactory.getLogger(WebClientPool.class);

    /** 默认值：与外部化之前的硬编码行为一致 */
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    static final int DEFAULT_RESPONSE_TIMEOUT_SECONDS = 60;
    static final int DEFAULT_MAX_CONNECTIONS = 500;
    static final long DEFAULT_PENDING_ACQUIRE_TIMEOUT_MS = 60_000L;
    static final int DEFAULT_MAX_IDLE_TIME_SECONDS = 60;

    private final int connectTimeoutMs;
    private final int responseTimeoutSeconds;
    private final ConnectionProvider connectionProvider;

    private final Map<String, WebClient> clientCache = new ConcurrentHashMap<>();

    /** 统计：命中次数 */
    private final AtomicLong hitCount = new AtomicLong(0);

    /** 统计：未命中次数 */
    private final AtomicLong missCount = new AtomicLong(0);

    /**
     * 生产构造器：参数来自配置，默认值与外部化前一致。
     *
     * @param connectTimeoutMs          连接超时（毫秒）
     * @param responseTimeoutSeconds    响应超时（秒）
     * @param maxConnections            连接池最大连接数
     * @param pendingAcquireTimeoutMs   等待获取连接的排队超时（毫秒）
     * @param maxIdleTimeSeconds        连接空闲回收时间（秒）
     */
    @Autowired
    public WebClientPool(
            @Value("${jairouter.webclient.connect-timeout-ms:10000}") final int connectTimeoutMs,
            @Value("${jairouter.webclient.response-timeout-seconds:60}") final int responseTimeoutSeconds,
            @Value("${jairouter.webclient.max-connections:500}") final int maxConnections,
            @Value("${jairouter.webclient.pending-acquire-timeout-ms:60000}") final long pendingAcquireTimeoutMs,
            @Value("${jairouter.webclient.max-idle-time-seconds:60}") final int maxIdleTimeSeconds) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
        this.connectionProvider = ConnectionProvider.builder("jairouter-outbound")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofSeconds(maxIdleTimeSeconds))
                .build();
        logger.info("WebClientPool 初始化完成: connectTimeoutMs={}, responseTimeoutSeconds={}, "
                        + "maxConnections={}, pendingAcquireTimeoutMs={}, maxIdleTimeSeconds={}",
                connectTimeoutMs, responseTimeoutSeconds, maxConnections,
                pendingAcquireTimeoutMs, maxIdleTimeSeconds);
    }

    /**
     * 按默认值构造（与外部化前行为一致）。
     *
     * <p>保留无参构造器：既有测试与调用方以 {@code new WebClientPool()} 构造，
     * 不因引入配置而破坏它们。</p>
     */
    public WebClientPool() {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_RESPONSE_TIMEOUT_SECONDS, DEFAULT_MAX_CONNECTIONS,
                DEFAULT_PENDING_ACQUIRE_TIMEOUT_MS, DEFAULT_MAX_IDLE_TIME_SECONDS);
    }

    /**
     * 释放连接池资源。
     *
     * <p>多副本滚动升级时每个副本都会停机；不释放会让 Netty 连接池资源随进程退出前的
     * 优雅停机窗口滞留。幂等，重复调用安全。</p>
     */
    @PreDestroy
    public void dispose() {
        try {
            connectionProvider.disposeLater().block(Duration.ofSeconds(5));
        } catch (Exception e) {
            logger.warn("释放 WebClient 连接池时异常: {}", e.getMessage());
        }
        clientCache.clear();
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
        // SSRF 门闩收敛在此：所有出站 WebClient（含 tracing 热路径）都经此创建
        final String normalizedBaseUrl = SsrfGuard.validateOutboundBaseUrl(baseUrl);
        // 使用规范化 baseUrl + configurator hashCode 作为缓存 key，等价 URL 不重复建池
        String cacheKey = configurator != null 
                ? normalizedBaseUrl + "#" + configurator.hashCode() 
                : normalizedBaseUrl;
        
        WebClient cached = clientCache.get(cacheKey);
        if (cached != null) {
            hitCount.incrementAndGet();
            return cached;
        }

        missCount.incrementAndGet();
        return clientCache.computeIfAbsent(cacheKey, key -> createWebClient(normalizedBaseUrl, configurator));
    }

    /**
     * 创建带连接池的 WebClient
     */
    private WebClient createWebClient(String baseUrl, Consumer<WebClient.Builder> configurator) {
        logger.debug("创建 WebClient: baseUrl={}", baseUrl);

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .compress(true)
                // #182：接上此前声明却未生效的连接超时
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

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
        // 与 getOrCreate 的规范化 key 对齐；原始前缀一并清理，兼容历史/脏 key
        String normalized = baseUrl;
        try {
            normalized = SsrfGuard.validateOutboundBaseUrl(baseUrl);
        } catch (SecurityException e) {
            // 清理保持宽容：非法 URL 仍按原始前缀移除
        }
        final String normalizedPrefix = normalized;
        final String rawPrefix = baseUrl;
        clientCache.keySet().removeIf(key -> key.startsWith(normalizedPrefix) || key.startsWith(rawPrefix));
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
