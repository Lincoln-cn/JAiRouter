package org.unreal.modelrouter.service.merger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置合并器
 *
 * 负责合并服务配置，处理配置继承和覆盖逻辑。
 * 支持深度合并 Map 结构，保留不冲突的字段。
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
@Component
public class ConfigMerger {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMerger.class);

    /**
     * 合并服务配置
     *
     * @param existing 现有配置
     * @param updates 更新配置
     * @return 合并后的配置
     */
    public ServiceConfiguration merge(final ServiceConfiguration existing,final ServiceConfiguration updates) {
        if (existing == null) {
            logger.debug("现有配置为空，使用更新配置");
            return updates;
        }

        if (updates == null) {
            logger.debug("更新配置为空，保留现有配置");
            return existing;
        }

        logger.debug("合并服务配置：existing={}, updates={}",
                existing.adapter(), updates.adapter());

        // 合并各字段：优先使用 updates 的非 null 值
        String adapter = updates.adapter() != null ? updates.adapter() : existing.adapter();
        List<ModelInstanceConfiguration> instances = mergeInstances(
                existing.instances(), updates.instances());
        LoadBalanceConfiguration loadBalance = mergeLoadBalance(
                existing.loadBalance(), updates.loadBalance());
        RateLimitConfiguration rateLimit = mergeRateLimit(
                existing.rateLimit(), updates.rateLimit());
        CircuitBreakerConfiguration circuitBreaker = mergeCircuitBreaker(
                existing.circuitBreaker(), updates.circuitBreaker());
        FallbackConfiguration fallback = mergeFallback(
                existing.fallback(), updates.fallback());

        ServiceConfiguration merged = new ServiceConfiguration(
                adapter, instances, loadBalance, rateLimit, circuitBreaker, fallback);

        logger.debug("服务配置合并完成：adapter={}", merged.adapter());
        return merged;
    }

    /**
     * 合并实例配置
     *
     * @param existing 现有实例列表
     * @param updates 更新实例列表
     * @return 合并后的实例列表
     */
    public List<ModelInstanceConfiguration> mergeInstances(
            final List<ModelInstanceConfiguration> existing,
            final List<ModelInstanceConfiguration> updates) {

        if (updates == null || updates.isEmpty()) {
            logger.debug("更新实例为空，保留现有实例：count={}",
                    existing != null ? existing.size() : 0);
            return existing != null ? existing : List.of();
        }

        if (existing == null || existing.isEmpty()) {
            logger.debug("现有实例为空，使用更新实例：count={}", updates.size());
            return updates;
        }

        // 创建现有实例的映射（按 baseUrl）
        Map<String, ModelInstanceConfiguration> existingMap = new HashMap<>();
        for (ModelInstanceConfiguration instance : existing) {
            if (instance.baseUrl() != null) {
                existingMap.put(instance.baseUrl(), instance);
            }
        }

        // 合并更新实例
        List<ModelInstanceConfiguration> merged = new ArrayList<>();
        for (ModelInstanceConfiguration update : updates) {
            if (update.baseUrl() != null) {
                ModelInstanceConfiguration existingInstance = existingMap.get(update.baseUrl());
                if (existingInstance != null) {
                    // 合并同 baseUrl 的实例
                    merged.add(mergeInstance(existingInstance, update));
                    existingMap.remove(update.baseUrl());
                } else {
                    merged.add(update);
                }
            } else {
                merged.add(update);
            }
        }

        // 添加未被更新的现有实例
        merged.addAll(existingMap.values());

        logger.debug("实例配置合并完成：count={}", merged.size());
        return merged;
    }

    /**
     * 合并单个实例配置
     */
    private ModelInstanceConfiguration mergeInstance(
            final ModelInstanceConfiguration existing,
            final ModelInstanceConfiguration update) {

        return new ModelInstanceConfiguration(
                update.name() != null ? update.name() : existing.name(),
                update.baseUrl() != null ? update.baseUrl() : existing.baseUrl(),
                update.path() != null ? update.path() : existing.path(),
                update.adapter() != null ? update.adapter() : existing.adapter(),
                update.weight() != null ? update.weight() : existing.weight(),
                update.status() != null ? update.status() : existing.status(),
                update.rateLimit() != null ? update.rateLimit() : existing.rateLimit(),
                update.circuitBreaker() != null ? update.circuitBreaker() : existing.circuitBreaker(),
                update.fallback() != null ? update.fallback() : existing.fallback(),
                update.headers() != null ? update.headers() : existing.headers(),
                update.instanceId() != null ? update.instanceId() : existing.instanceId()
        );
    }

    /**
     * 合并负载均衡配置
     */
    private LoadBalanceConfiguration mergeLoadBalance(
            final LoadBalanceConfiguration existing,
            final LoadBalanceConfiguration updates) {

        if (updates == null) {
            return existing;
        }

        if (existing == null) {
            return updates;
        }

        return new LoadBalanceConfiguration(
                updates.type() != null ? updates.type() : existing.type(),
                updates.hashAlgorithm() != null ? updates.hashAlgorithm() : existing.hashAlgorithm()
        );
    }

    /**
     * 合并限流配置
     */
    private RateLimitConfiguration mergeRateLimit(
            final RateLimitConfiguration existing,
            final RateLimitConfiguration updates) {

        if (updates == null) {
            return existing;
        }

        if (existing == null) {
            return updates;
        }

        return new RateLimitConfiguration(
                updates.requestsPerSecond() != null ? updates.requestsPerSecond() : existing.requestsPerSecond(),
                updates.requestsPerMinute() != null ? updates.requestsPerMinute() : existing.requestsPerMinute(),
                updates.requestsPerHour() != null ? updates.requestsPerHour() : existing.requestsPerHour(),
                updates.requestsPerDay() != null ? updates.requestsPerDay() : existing.requestsPerDay(),
                updates.burstSize() != null ? updates.burstSize() : existing.burstSize(),
                updates.enabled() != null ? updates.enabled() : existing.enabled()
        );
    }

    /**
     * 合并熔断器配置
     */
    private CircuitBreakerConfiguration mergeCircuitBreaker(
            final CircuitBreakerConfiguration existing,
            final CircuitBreakerConfiguration updates) {

        if (updates == null) {
            return existing;
        }

        if (existing == null) {
            return updates;
        }

        return new CircuitBreakerConfiguration(
                updates.failureThreshold() != null ? updates.failureThreshold() : existing.failureThreshold(),
                updates.timeout() != null ? updates.timeout() : existing.timeout(),
                updates.successThreshold() != null ? updates.successThreshold() : existing.successThreshold(),
                updates.enabled() != null ? updates.enabled() : existing.enabled()
        );
    }

    /**
     * 合并降级配置
     */
    private FallbackConfiguration mergeFallback(
            final FallbackConfiguration existing,
            final FallbackConfiguration updates) {

        if (updates == null) {
            return existing;
        }

        if (existing == null) {
            return updates;
        }

        return new FallbackConfiguration(
                updates.enabled() != null ? updates.enabled() : existing.enabled(),
                updates.fallbackUrl() != null ? updates.fallbackUrl() : existing.fallbackUrl(),
                updates.maxRetries() != null ? updates.maxRetries() : existing.maxRetries(),
                updates.retryInterval() != null ? updates.retryInterval() : existing.retryInterval(),
                updates.returnDefaultResponse() != null ? updates.returnDefaultResponse() : existing.returnDefaultResponse()
        );
    }

    /**
     * 递归合并 Map（用于底层配置合并）
     *
     * @param base 基础 Map
     * @param updates 更新 Map
     * @return 合并后的 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeMaps(final Map<String, Object> base,final Map<String, Object> updates) {
        if (base == null) {
            return updates != null ? new HashMap<>(updates) : new HashMap<>();
        }

        if (updates == null) {
            return new HashMap<>(base);
        }

        Map<String, Object> result = new HashMap<>(base);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object updateValue = entry.getValue();
            Object baseValue = base.get(key);

            if (updateValue instanceof Map && baseValue instanceof Map) {
                // 递归合并嵌套 Map
                result.put(key, mergeMaps(
                        (Map<String, Object>) baseValue,
                        (Map<String, Object>) updateValue));
            } else {
                // 直接覆盖
                result.put(key, updateValue);
            }
        }

        logger.debug("Map 合并完成：keys={}", result.keySet().size());
        return result;
    }
}
