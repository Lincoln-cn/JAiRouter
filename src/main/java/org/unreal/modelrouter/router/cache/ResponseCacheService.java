package org.unreal.modelrouter.router.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v2.9.9: 响应缓存门面服务.
 *
 * <p>统一封装缓存读/写/键构建与 hit/miss 指标：
 * <ul>
 *   <li>enabled=false 时全部短路：lookup 恒 empty、store no-op、buildKey 返回 null</li>
 *   <li>buildKey 内做确定性判定（only-deterministic / skip-streaming 属性），
 *       不可缓存请求返回 null（处理器据此不生成键 → 读写天然双关闭）</li>
 *   <li>lookup 命中记 {@code recordResponseCacheHit}，未命中记
 *       {@code recordResponseCacheMiss}</li>
 *   <li>v2.9.10: invalidate/invalidateAll 供管理 API 失效缓存</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
@Service
public class ResponseCacheService {

    /** TTL 合理下界（秒） */
    private static final long TTL_MIN_SECONDS = 1;
    /** TTL 合理上界（秒，7 天） */
    private static final long TTL_MAX_SECONDS = 604_800;

    private final CacheStore cacheStore;
    private final ResponseCacheProperties properties;
    private final MetricsCollector metricsCollector;

    /** 线程安全的启动累计命中计数 */
    private final AtomicLong hits = new AtomicLong(0);
    /** 线程安全的启动累计未命中计数 */
    private final AtomicLong misses = new AtomicLong(0);

    /**
     * 构造函数.
     *
     * @param cacheStore 缓存存储
     * @param properties 响应缓存配置
     * @param metricsCollector 指标收集器（可选，监控关闭时为 null）
     */
    public ResponseCacheService(
            final CacheStore cacheStore,
            final ResponseCacheProperties properties,
            @Autowired(required = false) final MetricsCollector metricsCollector) {
        this.cacheStore = cacheStore;
        this.properties = properties;
        this.metricsCollector = metricsCollector;
    }

    /**
     * 响应缓存是否启用.
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 构建缓存键（enabled=false / 租户缺失 / 请求不可缓存 / cacheSalt 绕过 时返回 null）.
     *
     * @param tenantKey 租户键（apiKeyId，null 时调用方已回退 clientIp）
     * @param serviceType 服务类型
     * @param requestDto 原始请求 DTO
     * @return 三段式缓存键（rc:{serviceType}:{model}:{sha256}）；不可缓存时返回 null
     */
    public String buildKey(final String tenantKey, final ServiceType serviceType, final Object requestDto) {
        if (!isEnabled() || tenantKey == null || tenantKey.isBlank()
                || serviceType == null || requestDto == null) {
            return null;
        }
        if (!isCacheEligible(serviceType, requestDto)) {
            return null;
        }
        return ResponseCacheKeyBuilder.build(tenantKey, serviceType, requestDto);
    }

    /**
     * 缓存读（命中/未命中均记录指标）.
     *
     * @param key 缓存键
     * @param serviceName 服务名（用于指标 tag）
     * @param modelName 模型名（用于指标 tag）
     * @return 命中返回缓存值，未命中或未启用返回 {@link Optional#empty()}
     */
    public Optional<Object> lookup(final String key, final String serviceName, final String modelName) {
        if (!isEnabled() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        Optional<Object> result = cacheStore.get(key);
        if (result.isPresent()) {
            recordHit(serviceName, modelName);
        } else {
            recordMiss(serviceName, modelName);
        }
        return result;
    }

    /**
     * 缓存写（enabled=false / 键空 / 数据 null 时 no-op）.
     *
     * @param key 缓存键
     * @param data 缓存数据（下游转换后的 data）
     */
    public void store(final String key, final Object data) {
        if (!isEnabled() || key == null || key.isBlank() || data == null) {
            return;
        }
        cacheStore.put(key, data, properties.getTtl());
    }

    /**
     * 请求是否可缓存（only-deterministic / skip-streaming 属性判定）.
     *
     * <p>chat 需满足：skipStreaming 关闭或非流式；onlyDeterministic 关闭或
     * temperature==null/0 且 n==null/1。embedding/rerank 天然确定性（无采样参数）。
     */
    private boolean isCacheEligible(final ServiceType serviceType, final Object requestDto) {
        switch (serviceType) {
            case chat:
                if (!(requestDto instanceof ChatDTO.Request request)) {
                    return false;
                }
                if (properties.isSkipStreaming() && Boolean.TRUE.equals(request.stream())) {
                    return false;
                }
                if (properties.isOnlyDeterministic() && !isDeterministicChat(request)) {
                    return false;
                }
                return true;
            case embedding:
                return requestDto instanceof EmbeddingDTO.Request;
            case rerank:
                return requestDto instanceof RerankDTO.Request;
            default:
                return false;
        }
    }

    /**
     * chat 确定性判定：temperature==null/0 且 n==null/1.
     */
    private boolean isDeterministicChat(final ChatDTO.Request request) {
        Double temperature = request.temperature();
        boolean temperatureZero = temperature == null || temperature == 0d;
        Integer n = request.n();
        boolean singleChoice = n == null || n == 1;
        return temperatureZero && singleChoice;
    }

    /**
     * 记录缓存命中指标（同时递增运行时计数器）.
     */
    private void recordHit(final String serviceName, final String modelName) {
        hits.incrementAndGet();
        if (metricsCollector != null) {
            metricsCollector.recordResponseCacheHit(serviceName, modelName);
        }
    }

    /**
     * 记录缓存未命中指标（同时递增运行时计数器）.
     */
    private void recordMiss(final String serviceName, final String modelName) {
        misses.incrementAndGet();
        if (metricsCollector != null) {
            metricsCollector.recordResponseCacheMiss(serviceName, modelName);
        }
    }

    /**
     * 当前缓存条目数（enabled=false 时返回 0）.
     *
     * @return 缓存条目数
     */
    public long size() {
        if (!isEnabled()) {
            return 0;
        }
        return cacheStore.size();
    }

    /**
     * 按服务类型与模型失效缓存（enabled=false 时 no-op）.
     *
     * <p>通过键前缀寻址批量删除：{@code rc:{serviceType}:} 或
     * {@code rc:{serviceType}:{model}:}。
     *
     * @param serviceType 服务类型（可选，null 时失效全部）
     * @param model 模型名称（可选，null 时按服务类型失效）
     * @return 失效操作是否执行（enabled=false 时返回 false）
     */
    public boolean invalidate(final ServiceType serviceType, final String model) {
        if (!isEnabled()) {
            return false;
        }
        if (serviceType == null) {
            cacheStore.clear();
            return true;
        }
        String prefix = ResponseCacheKeyBuilder.buildPrefix(serviceType, model);
        cacheStore.deleteByPrefix(prefix);
        return true;
    }

    /**
     * 清空全部响应缓存（enabled=false 时 no-op）.
     *
     * @return 清空操作是否执行
     */
    public boolean invalidateAll() {
        if (!isEnabled()) {
            return false;
        }
        cacheStore.clear();
        return true;
    }

    /**
     * 获取当前缓存运行时状态快照.
     *
     * <p>包含配置项（来自 properties）与运行时统计（hits/misses/hitRatio/size）。
     * hitRatio 在 hits+misses=0 时返回 null 表示"尚无请求数据"。
     *
     * @return 不可变快照 map
     */
    public Map<String, Object> snapshot() {
        long h = hits.get();
        long m = misses.get();
        Double hitRatio = (h + m) == 0 ? null : (double) h / (h + m);

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("enabled", properties.isEnabled());
        snap.put("ttlSeconds", properties.getTtl().getSeconds());
        snap.put("maxSize", properties.getMaxSize());
        snap.put("size", size());
        snap.put("skipStreaming", properties.isSkipStreaming());
        snap.put("onlyDeterministic", properties.isOnlyDeterministic());
        snap.put("hits", h);
        snap.put("misses", m);
        snap.put("hitRatio", hitRatio);
        return snap;
    }

    /**
     * 运行时更新缓存配置（部分更新：仅修改非 null 参数）.
     *
     * <p>语义：
     * <ul>
     *   <li>全 null（无任何配置参数）视为非法 → 抛出 {@link IllegalArgumentException}</li>
     *   <li>非 null 参数逐一校验后直接写入注入的 {@link ResponseCacheProperties} bean</li>
     *   <li>ttlSeconds 范围：[1, 604800]（7 天），越界抛出 {@link IllegalArgumentException}</li>
     *   <li>maxSize 不可热改（Caffeine 构造时锁定），此处不接受该参数</li>
     * </ul>
     *
     * @param enabled           是否启用（null 表示不修改）
     * @param skipStreaming      是否跳过流式（null 表示不修改）
     * @param onlyDeterministic  是否仅缓存确定性请求（null 表示不修改）
     * @param ttlSeconds         缓存 TTL 秒数（null 表示不修改）
     * @return 更新后的快照
     * @throws IllegalArgumentException 全 null 或校验失败
     */
    public Map<String, Object> updateRuntimeConfig(final Boolean enabled,
                                                   final Boolean skipStreaming,
                                                   final Boolean onlyDeterministic,
                                                   final Long ttlSeconds) {
        if (enabled == null && skipStreaming == null
                && onlyDeterministic == null && ttlSeconds == null) {
            throw new IllegalArgumentException("至少需要指定一个配置参数");
        }

        if (ttlSeconds != null) {
            if (ttlSeconds < TTL_MIN_SECONDS || ttlSeconds > TTL_MAX_SECONDS) {
                throw new IllegalArgumentException(
                        "ttlSeconds 超出范围 [" + TTL_MIN_SECONDS + ", " + TTL_MAX_SECONDS + "]: " + ttlSeconds);
            }
            properties.setTtl(Duration.ofSeconds(ttlSeconds));
        }
        if (enabled != null) {
            properties.setEnabled(enabled);
        }
        if (skipStreaming != null) {
            properties.setSkipStreaming(skipStreaming);
        }
        if (onlyDeterministic != null) {
            properties.setOnlyDeterministic(onlyDeterministic);
        }

        return snapshot();
    }
}
