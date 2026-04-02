package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.DatabaseConfigService;
import org.unreal.modelrouter.store.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.store.repository.ServiceConfigRepository;
import org.unreal.modelrouter.store.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.vo.ServiceInstanceVO;
import org.unreal.modelrouter.dto.InstanceUpdateFlatRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实例配置服务 - 负责实例的增删改查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceConfigService {

    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final DatabaseConfigService databaseConfigService;

    /**
     * 获取服务实例列表
     */
    public Mono<List<ServiceInstanceVO>> getInstances(String serviceType) {
        log.info("获取服务实例列表：serviceType={}", serviceType);
        return Mono.<List<ServiceInstanceVO>>fromCallable(() -> {
                Map<String, Object> serviceConfig = databaseConfigService.getServiceConfig(serviceType);
                if (serviceConfig == null || serviceConfig.isEmpty()) {
                    return new java.util.ArrayList<ServiceInstanceVO>();
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
                if (instances == null) {
                    return new java.util.ArrayList<ServiceInstanceVO>();
                }
                List<ServiceInstanceVO> result = instances.stream()
                        .map(this::convertToVO)
                        .collect(Collectors.toList());
                return result;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取单个实例详情
     */
    public Mono<ServiceInstanceVO> getInstance(String serviceType, String instanceId) {
        log.info("获取实例详情：serviceType={}, instanceId={}", serviceType, instanceId);
        return getInstances(serviceType)
                .map(instances -> instances.stream()
                        .filter(inst -> instanceId.equals(inst.getInstanceId()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * 添加实例
     */
    public Mono<ServiceInstanceVO> addInstance(String serviceType, Map<String, Object> instanceConfig) {
        log.info("添加实例：serviceType={}, config={}", serviceType, instanceConfig);
        return Mono.fromCallable(() -> {
                // 调用 DatabaseConfigService 添加实例
                Map<String, Object> result = databaseConfigService.addServiceInstance(serviceType, instanceConfig);
                return convertToVO(result);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 更新实例
     */
    public Mono<ServiceInstanceVO> updateInstance(String serviceType, String instanceId, Map<String, Object> instanceConfig) {
        log.info("更新实例：serviceType={}, instanceId={}, config={}", serviceType, instanceId, instanceConfig);
        return Mono.fromCallable(() -> {
                // 确保 instanceId 在 instanceConfig 中
                instanceConfig.put("instanceId", instanceId);
                
                // 调用 DatabaseConfigService 更新实例
                Map<String, Object> result = databaseConfigService.updateServiceInstance(serviceType, instanceId, instanceConfig);
                return convertToVO(result);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 更新实例（简化版 - 直接使用扁平化 DTO）
     */
    public Mono<ServiceInstanceVO> updateInstanceFlat(String serviceType, String instanceId, InstanceUpdateFlatRequest request) {
        log.info("简化版更新实例：serviceType={}, instanceId={}, name={}", serviceType, instanceId, request.getName());
        return Mono.fromCallable(() -> {
                // 将 DTO 转换为 Map（扁平格式）
                Map<String, Object> instanceConfig = new HashMap<>();
                instanceConfig.put("instanceId", instanceId);
                instanceConfig.put("name", request.getName());
                instanceConfig.put("baseUrl", request.getBaseUrl());
                if (request.getPath() != null) instanceConfig.put("path", request.getPath());
                if (request.getWeight() != null) instanceConfig.put("weight", request.getWeight());
                if (request.getStatus() != null) instanceConfig.put("status", request.getStatus());
                if (request.getAdapter() != null) instanceConfig.put("adapter", request.getAdapter());
                if (request.getHeaders() != null) instanceConfig.put("headers", request.getHeaders());
                
                // 限流器配置 - 扁平字段
                if (request.getRateLimitEnabled() != null) instanceConfig.put("rateLimitEnabled", request.getRateLimitEnabled());
                if (request.getRateLimitAlgorithm() != null) instanceConfig.put("rateLimitAlgorithm", request.getRateLimitAlgorithm());
                if (request.getRateLimitCapacity() != null) instanceConfig.put("rateLimitCapacity", request.getRateLimitCapacity());
                if (request.getRateLimitRate() != null) instanceConfig.put("rateLimitRate", request.getRateLimitRate());
                if (request.getRateLimitScope() != null) instanceConfig.put("rateLimitScope", request.getRateLimitScope());
                if (request.getRateLimitKey() != null) instanceConfig.put("rateLimitKey", request.getRateLimitKey());
                if (request.getRateLimitClientIpEnable() != null) instanceConfig.put("rateLimitClientIpEnable", request.getRateLimitClientIpEnable());
                
                // 熔断器配置 - 扁平字段
                if (request.getCircuitBreakerEnabled() != null) instanceConfig.put("circuitBreakerEnabled", request.getCircuitBreakerEnabled());
                if (request.getCircuitBreakerFailureThreshold() != null) instanceConfig.put("circuitBreakerFailureThreshold", request.getCircuitBreakerFailureThreshold());
                if (request.getCircuitBreakerTimeout() != null) instanceConfig.put("circuitBreakerTimeout", request.getCircuitBreakerTimeout());
                if (request.getCircuitBreakerSuccessThreshold() != null) instanceConfig.put("circuitBreakerSuccessThreshold", request.getCircuitBreakerSuccessThreshold());
                
                // 调用 DatabaseConfigService 更新实例
                Map<String, Object> result = databaseConfigService.updateServiceInstance(serviceType, instanceId, instanceConfig);
                return convertToVO(result);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除实例
     */
    public Mono<Void> deleteInstance(String serviceType, String instanceId) {
        log.info("删除实例：serviceType={}, instanceId={}", serviceType, instanceId);
        return Mono.fromRunnable(() -> {
                databaseConfigService.deleteServiceInstance(serviceType, instanceId);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    /**
     * 将 Map 转换为 ServiceInstanceVO
     */
    private ServiceInstanceVO convertToVO(Map<String, Object> instance) {
        if (instance == null) {
            return null;
        }

        // 构建限流配置 VO - 支持嵌套和扁平两种格式
        ServiceInstanceVO.RateLimitVO rateLimit = null;
        if (instance.containsKey("rateLimit") && instance.get("rateLimit") instanceof Map) {
            // 嵌套格式
            Map<String, Object> rl = (Map<String, Object>) instance.get("rateLimit");
            rateLimit = ServiceInstanceVO.RateLimitVO.builder()
                    .enabled((Boolean) rl.get("enabled"))
                    .algorithm((String) rl.get("algorithm"))
                    .capacity(rl.get("capacity") != null ? ((Number) rl.get("capacity")).intValue() : null)
                    .rate(rl.get("rate") != null ? ((Number) rl.get("rate")).intValue() : null)
                    .scope((String) rl.get("scope"))
                    .key((String) rl.get("key"))
                    .clientIpEnable((Boolean) rl.get("clientIpEnable"))
                    .build();
        } else if (instance.containsKey("rateLimitEnabled")) {
            // 扁平格式
            Boolean enabled = (Boolean) instance.get("rateLimitEnabled");
            if (enabled != null && enabled) {
                rateLimit = ServiceInstanceVO.RateLimitVO.builder()
                        .enabled(enabled)
                        .algorithm((String) instance.get("rateLimitAlgorithm"))
                        .capacity((Integer) instance.get("rateLimitCapacity"))
                        .rate((Integer) instance.get("rateLimitRate"))
                        .scope((String) instance.get("rateLimitScope"))
                        .key((String) instance.get("rateLimitKey"))
                        .clientIpEnable((Boolean) instance.get("rateLimitClientIpEnable"))
                        .build();
            }
        }

        // 构建熔断器配置 VO - 支持嵌套和扁平两种格式
        ServiceInstanceVO.CircuitBreakerVO circuitBreaker = null;
        if (instance.containsKey("circuitBreaker") && instance.get("circuitBreaker") instanceof Map) {
            // 嵌套格式
            Map<String, Object> cb = (Map<String, Object>) instance.get("circuitBreaker");
            circuitBreaker = ServiceInstanceVO.CircuitBreakerVO.builder()
                    .enabled((Boolean) cb.get("enabled"))
                    .failureThreshold((Integer) cb.get("failureThreshold"))
                    .timeout((Integer) cb.get("timeout"))
                    .successThreshold((Integer) cb.get("successThreshold"))
                    .build();
        } else if (instance.containsKey("circuitBreakerEnabled")) {
            // 扁平格式
            Boolean cbEnabled = (Boolean) instance.get("circuitBreakerEnabled");
            if (cbEnabled != null && cbEnabled) {
                circuitBreaker = ServiceInstanceVO.CircuitBreakerVO.builder()
                        .enabled(cbEnabled)
                        .failureThreshold((Integer) instance.get("circuitBreakerFailureThreshold"))
                        .timeout((Integer) instance.get("circuitBreakerTimeout"))
                        .successThreshold((Integer) instance.get("circuitBreakerSuccessThreshold"))
                        .build();
            }
        }

        return ServiceInstanceVO.builder()
                .instanceId((String) instance.get("instanceId"))
                .name((String) instance.get("name"))
                .baseUrl((String) instance.get("baseUrl"))
                .path((String) instance.get("path"))
                .weight(instance.get("weight") != null ? ((Number) instance.get("weight")).intValue() : null)
                .status((String) instance.get("status"))
                .healthStatus((String) instance.get("healthStatus"))
                .headers(instance.get("headers"))
                .rateLimit(rateLimit)
                .circuitBreaker(circuitBreaker)
                .build();
    }
}
