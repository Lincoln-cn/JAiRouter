package org.unreal.modelrouter.config.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务配置更新服务
 *
 * <p>处理服务配置的 DTO 转换和更新逻辑。
 * 从 ConfigurationService 提取，实现单一职责原则。
 *
 * <h2>更新语义：增量合并，不是全量覆盖（issue #1088）</h2>
 *
 * <p>本类曾经从空 Map 开始构建新配置、只保留 {@code instances}，然后放请求里非 null 的字段。
 * 结果是 {@code PUT /api/config/type/services/{type}} 只要不同时提交 adapter / loadBalance /
 * rateLimit / circuitBreaker 的全部内容，未提交的字段就被静默清空——实测把 chat 的
 * {@code adapter: gpustack} 清成了 {@code null}，同时把 {@code rateLimit} 整块重置，
 * 而接口仍返回「更新配置成功」。管理台任何「只改负载均衡」的表单都会踩这条路径。
 *
 * <p>现在的规则：
 * <ol>
 *   <li>以现有配置为基线（{@link LinkedHashMap} 副本），<b>保留所有未提及的字段</b>，
 *       包括未知键（未来版本新增的配置项不会被旧代码清掉）；</li>
 *   <li>请求里为 {@code null} 的字段表示「不修改」；</li>
 *   <li>子配置块（loadBalance / rateLimit / circuitBreaker）内部同样按字段合并，
 *       所以「读出来 → 只改一项 → 写回去」不会丢掉块里的其它字段。</li>
 * </ol>
 *
 * <p>代价：无法通过本接口把某个字段显式置为 {@code null}（例如清空 fallback）。
 * 需要清空时请使用版本回滚或删除后重建，这是为避免「少传字段就丢配置」而做的取舍。
 *
 * @since v2.6.15
 */
@Service
public class ServiceConfigUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigUpdateService.class);

    /**
     * 构建服务配置更新
     *
     * @param existingServiceConfig 现有服务配置，可为 null（新建服务时）
     * @param request               更新请求，字段为 null 表示不修改
     * @return 更新后的服务配置（现有配置的副本 + 请求中出现的字段）
     */
    public Map<String, Object> buildServiceConfigUpdate(final Map<String, Object> existingServiceConfig,
                                                         final UpdateServiceConfigRequest request) {
        Map<String, Object> newConfig = existingServiceConfig == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existingServiceConfig);

        if (request.getAdapter() != null) {
            newConfig.put("adapter", request.getAdapter());
        }

        final LoadBalanceConfig lb = request.getLoadBalance();
        if (lb != null) {
            final Map<String, Object> patch = buildLoadBalanceMap(lb);
            if (!patch.isEmpty()) {
                newConfig.put("loadBalance", mergeBlock(newConfig.get("loadBalance"), patch));
            }
        }

        final RateLimitConfig rl = request.getRateLimit();
        if (rl != null) {
            final Map<String, Object> patch = buildRateLimitMap(rl);
            if (!patch.isEmpty()) {
                newConfig.put("rateLimit", mergeBlock(newConfig.get("rateLimit"), patch));
            }
        }

        final CircuitBreakerConfig cb = request.getCircuitBreaker();
        if (cb != null) {
            final Map<String, Object> patch = buildCircuitBreakerMap(cb);
            if (!patch.isEmpty()) {
                newConfig.put("circuitBreaker", mergeBlock(newConfig.get("circuitBreaker"), patch));
            }
        }

        // issue #1088：fallback 原本有 DTO 字段却从未被写回，这里补齐（null 仍表示不修改）
        if (request.getFallback() != null) {
            newConfig.put("fallback", request.getFallback());
        }

        logger.debug("构建服务配置更新完成，配置项: {}", newConfig.keySet());
        return newConfig;
    }

    /**
     * 合并子配置块：以现有块为基线，用补丁里出现的字段覆盖。
     *
     * <p>现有块不是 Map（例如历史数据里是字符串）时直接采用补丁，避免 ClassCastException。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeBlock(final Object existingBlock, final Map<String, Object> patch) {
        final Map<String, Object> merged = new LinkedHashMap<>();
        if (existingBlock instanceof Map) {
            merged.putAll((Map<String, Object>) existingBlock);
        }
        merged.putAll(patch);
        return merged;
    }

    /**
     * 构建负载均衡配置补丁（只包含请求里出现的字段，null 不写入）
     */
    private Map<String, Object> buildLoadBalanceMap(final LoadBalanceConfig lb) {
        final Map<String, Object> lbMap = new LinkedHashMap<>();
        putIfPresent(lbMap, "type", lb.getType());
        putIfPresent(lbMap, "hashAlgorithm", lb.getHashAlgorithm());
        return lbMap;
    }

    /**
     * 构建限流配置补丁（只包含请求里出现的字段，null 不写入）
     */
    private Map<String, Object> buildRateLimitMap(final RateLimitConfig rl) {
        final Map<String, Object> rlMap = new LinkedHashMap<>();
        putIfPresent(rlMap, "enabled", rl.getEnabled());
        putIfPresent(rlMap, "algorithm", rl.getAlgorithm());
        putIfPresent(rlMap, "capacity", rl.getCapacity());
        putIfPresent(rlMap, "rate", rl.getRate());
        putIfPresent(rlMap, "scope", rl.getScope());
        putIfPresent(rlMap, "key", rl.getKey());
        putIfPresent(rlMap, "clientIpEnable", rl.getClientIpEnable());
        return rlMap;
    }

    /**
     * 构建熔断配置补丁（只包含请求里出现的字段，null 不写入）
     */
    private Map<String, Object> buildCircuitBreakerMap(final CircuitBreakerConfig cb) {
        final Map<String, Object> cbMap = new LinkedHashMap<>();
        putIfPresent(cbMap, "enabled", cb.getEnabled());
        putIfPresent(cbMap, "failureThreshold", cb.getFailureThreshold());
        putIfPresent(cbMap, "timeout", cb.getTimeout());
        putIfPresent(cbMap, "successThreshold", cb.getSuccessThreshold());
        return cbMap;
    }

    private static void putIfPresent(final Map<String, Object> target, final String key, final Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
