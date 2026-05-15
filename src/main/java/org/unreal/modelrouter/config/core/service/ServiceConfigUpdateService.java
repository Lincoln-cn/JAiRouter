package org.unreal.modelrouter.config.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务配置更新服务
 *
 * 处理服务配置的 DTO 转换和更新逻辑。
 * 从 ConfigurationService 提取，实现单一职责原则。
 *
 * @since v2.6.15
 */
@Service
public class ServiceConfigUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigUpdateService.class);

    /**
     * 构建服务配置更新
     *
     * @param existingServiceConfig 现有服务配置
     * @param request               更新请求
     * @return 更新后的服务配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildServiceConfigUpdate(final Map<String, Object> existingServiceConfig,
                                                         final UpdateServiceConfigRequest request) {
        Map<String, Object> newConfig = new HashMap<>();

        // 保留现有的 instances
        if (existingServiceConfig != null && existingServiceConfig.containsKey("instances")) {
            newConfig.put("instances", existingServiceConfig.get("instances"));
        }

        // 更新传入的配置
        if (request.getAdapter() != null) {
            newConfig.put("adapter", request.getAdapter());
        }

        // 负载均衡配置
        LoadBalanceConfig lb = request.getLoadBalance();
        if (lb != null) {
            newConfig.put("loadBalance", buildLoadBalanceMap(lb));
        }

        // 限流配置
        RateLimitConfig rl = request.getRateLimit();
        if (rl != null) {
            newConfig.put("rateLimit", buildRateLimitMap(rl));
        }

        // 熔断配置
        CircuitBreakerConfig cb = request.getCircuitBreaker();
        if (cb != null) {
            newConfig.put("circuitBreaker", buildCircuitBreakerMap(cb));
        }

        logger.debug("构建服务配置更新完成，配置项: {}", newConfig.keySet());
        return newConfig;
    }

    /**
     * 构建负载均衡配置 Map
     */
    private Map<String, Object> buildLoadBalanceMap(final LoadBalanceConfig lb) {
        Map<String, Object> lbMap = new HashMap<>();
        lbMap.put("type", lb.getType());
        if (lb.getHashAlgorithm() != null) {
            lbMap.put("hashAlgorithm", lb.getHashAlgorithm());
        }
        return lbMap;
    }

    /**
     * 构建限流配置 Map
     */
    private Map<String, Object> buildRateLimitMap(final RateLimitConfig rl) {
        Map<String, Object> rlMap = new HashMap<>();
        rlMap.put("enabled", rl.getEnabled());
        rlMap.put("algorithm", rl.getAlgorithm());
        rlMap.put("capacity", rl.getCapacity());
        rlMap.put("rate", rl.getRate());
        rlMap.put("scope", rl.getScope());
        rlMap.put("key", rl.getKey());
        rlMap.put("clientIpEnable", rl.getClientIpEnable());
        return rlMap;
    }

    /**
     * 构建熔断配置 Map
     */
    private Map<String, Object> buildCircuitBreakerMap(final CircuitBreakerConfig cb) {
        Map<String, Object> cbMap = new HashMap<>();
        cbMap.put("enabled", cb.getEnabled());
        cbMap.put("failureThreshold", cb.getFailureThreshold());
        cbMap.put("timeout", cb.getTimeout());
        cbMap.put("successThreshold", cb.getSuccessThreshold());
        return cbMap;
    }
}
