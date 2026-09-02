package org.unreal.modelrouter.router.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.Optional;

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
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
@Service
public class ResponseCacheService {

    private final CacheStore cacheStore;
    private final ResponseCacheProperties properties;
    private final MetricsCollector metricsCollector;

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
     * @return SHA-256 十六进制缓存键；不可缓存时返回 null
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
     * 记录缓存命中指标.
     */
    private void recordHit(final String serviceName, final String modelName) {
        if (metricsCollector != null) {
            metricsCollector.recordResponseCacheHit(serviceName, modelName);
        }
    }

    /**
     * 记录缓存未命中指标.
     */
    private void recordMiss(final String serviceName, final String modelName) {
        if (metricsCollector != null) {
            metricsCollector.recordResponseCacheMiss(serviceName, modelName);
        }
    }
}
