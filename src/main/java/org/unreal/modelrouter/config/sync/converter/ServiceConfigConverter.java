package org.unreal.modelrouter.config.sync.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.core.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.core.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.core.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.core.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.LoadBalanceConfig;
import org.unreal.modelrouter.dto.RateLimitConfig;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.jpa.entity.ServiceConfigEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务配置转换器
 *
 * 负责 DTO 与领域对象之间的转换。
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
@Component
public class ServiceConfigConverter {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigConverter.class);

    /**
     * ServiceConfiguration → ServiceConfigDTO
     */
    public ServiceConfigDTO toDTO(final ServiceConfiguration config, final String serviceType) {
        if (config == null) {
            return null;
        }

        return ServiceConfigDTO.builder()
                .serviceType(serviceType)
                .adapter(config.adapter())
                .loadBalanceType(config.loadBalance() != null ? config.loadBalance().type() : null)
                .build();
    }

    /**
     * ServiceConfigEntity → ServiceConfigDTO
     */
    public ServiceConfigDTO toDTO(final ServiceConfigEntity entity) {
        if (entity == null) {
            return null;
        }

        return ServiceConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .serviceType(entity.getServiceType())
                .adapter(entity.getAdapter())
                .loadBalanceType(entity.getLoadBalanceType())
                .version(entity.getVersion())
                .isLatest(entity.getIsLatest())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * ServiceConfiguration → ServiceConfigDTO (带实体信息)
     */
    public ServiceConfigDTO toDTO(final ServiceConfiguration config, final ServiceConfigEntity entity) {
        if (config == null) {
            return null;
        }

        return ServiceConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .serviceType(entity.getServiceType())
                .adapter(config.adapter())
                .loadBalanceType(config.loadBalance() != null ? config.loadBalance().type() : null)
                .version(entity.getVersion())
                .isLatest(entity.getIsLatest())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * CreateServiceConfigRequest → ServiceConfiguration
     */
    public ServiceConfiguration fromCreateRequest(final CreateServiceConfigRequest request) {
        if (request == null) {
            return null;
        }

        List<ModelInstanceConfiguration> instances = null;
        if (request.getInstances() != null && !request.getInstances().isEmpty()) {
            instances = request.getInstances().stream()
                    .map(this::toModelInstance)
                    .collect(Collectors.toList());
        }

        return new ServiceConfiguration(
                request.getAdapter(),
                instances,
                toLoadBalance(request.getLoadBalanceType()),
                null, // rateLimit
                null, // circuitBreaker
                null  // fallback
        );
    }

    /**
     * UpdateServiceConfigRequest → ServiceConfiguration (部分更新)
     */
    public ServiceConfiguration fromUpdateRequest(
            final ServiceConfiguration existing,
            final UpdateServiceConfigRequest request) {

        if (request == null || existing == null) {
            return existing;
        }

        return new ServiceConfiguration(
                request.getAdapter() != null ? request.getAdapter() : existing.adapter(),
                existing.instances(), // 保留现有 instances
                request.getLoadBalance() != null ?
                        mergeLoadBalance(existing.loadBalance(), request.getLoadBalance()) : existing.loadBalance(),
                request.getRateLimit() != null ?
                        toRateLimit(request.getRateLimit()) : existing.rateLimit(),
                request.getCircuitBreaker() != null ?
                        toCircuitBreaker(request.getCircuitBreaker()) : existing.circuitBreaker(),
                request.getFallback() != null ?
                        toFallback(request.getFallback()) : existing.fallback()
        );
    }

    /**
     * 列表转换：ServiceConfiguration → ServiceConfigDTO
     */
    public List<ServiceConfigDTO> toDTOList(final Map<String, ServiceConfiguration> configurations) {
        if (configurations == null || configurations.isEmpty()) {
            return List.of();
        }

        return configurations.entrySet().stream()
                .map(entry -> toDTO(entry.getValue(), entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * CreateServiceInstanceRequest → ModelInstanceConfiguration
     */
    private ModelInstanceConfiguration toModelInstance(final CreateServiceInstanceRequest request) {
        return new ModelInstanceConfiguration(
                request.getName(),
                request.getBaseUrl(),
                request.getPath(),
                request.getAdapter(),
                request.getWeight(),
                request.getStatus(),
                null, // rateLimit
                null, // circuitBreaker
                null, // fallback
                request.getHeaders(),
                null  // instanceId
        );
    }

    /**
     * String → LoadBalanceConfiguration
     */
    private LoadBalanceConfiguration toLoadBalance(final String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return new LoadBalanceConfiguration(type, null);
    }

    /**
     * LoadBalanceConfig → LoadBalanceConfiguration
     */
    private LoadBalanceConfiguration mergeLoadBalance(
            final LoadBalanceConfiguration existing,
            final LoadBalanceConfig update) {

        if (update == null) {
            return existing;
        }

        return new LoadBalanceConfiguration(
                update.getType() != null ? update.getType() : (existing != null ? existing.type() : null),
                update.getHashAlgorithm() != null ? update.getHashAlgorithm() : (existing != null ? existing.hashAlgorithm() : null)
        );
    }

    /**
     * RateLimitConfig → RateLimitConfiguration
     */
    private RateLimitConfiguration toRateLimit(final RateLimitConfig config) {
        if (config == null) {
            return null;
        }

        return new RateLimitConfiguration(
                config.getRate(),
                config.getRate() != null ? config.getRate() * 60 : null,
                config.getRate() != null ? config.getRate() * 3600 : null,
                config.getRate() != null ? config.getRate() * 86400 : null,
                config.getCapacity(),
                config.getEnabled()
        );
    }

    /**
     * CircuitBreakerConfig → CircuitBreakerConfiguration
     */
    private CircuitBreakerConfiguration toCircuitBreaker(final CircuitBreakerConfig config) {
        if (config == null) {
            return null;
        }

        return new CircuitBreakerConfiguration(
                config.getFailureThreshold(),
                config.getTimeout() != null ? config.getTimeout().longValue() : null,
                config.getSuccessThreshold(),
                config.getEnabled()
        );
    }

    /**
     * Object → FallbackConfiguration
     */
    @SuppressWarnings("unchecked")
    private FallbackConfiguration toFallback(final Object fallback) {
        if (fallback == null) {
            return null;
        }

        if (fallback instanceof FallbackConfiguration fc) {
            return fc;
        }

        if (fallback instanceof Map<?, ?> map) {
            return FallbackConfiguration.fromMap((Map<String, Object>) map);
        }

        return FallbackConfiguration.defaultConfig();
    }
}
