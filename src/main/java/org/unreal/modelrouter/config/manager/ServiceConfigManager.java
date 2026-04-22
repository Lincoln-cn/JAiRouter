package org.unreal.modelrouter.config.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;
import org.unreal.modelrouter.constants.ServiceTypeConstants;
import org.unreal.modelrouter.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;

/**
 * 服务配置管理器 - v2.1.0 重构版
 * 
 * 负责服务配置的 CRUD 操作，使用强类型 DTO 替代 Map。
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */
@Component("configServiceConfigManager")
public class ServiceConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigManager.class);

    private final StoreManager storeManager;
    private final ConfigurationHelper configurationHelper;
    private final ConfigMergeService configMergeService;

    private static final String CURRENT_KEY = "model-router-config";

    public ServiceConfigManager(StoreManager storeManager,
                                ConfigurationHelper configurationHelper,
                                ConfigMergeService configMergeService) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
    }

    /**
     * 获取所有可用的服务类型
     *
     * @return 服务类型集合
     */
    public Set<String> getAvailableServiceTypes() {
        try {
            Map<String, Object> config = getCurrentConfig();
            if (config == null || !config.containsKey("services")) {
                return new HashSet<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            return services.keySet();
        } catch (Exception e) {
            logger.error("获取可用服务类型失败：error={}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    /**
     * 获取指定服务类型的配置（强类型）
     *
     * @param serviceType 服务类型
     * @return 服务配置，不存在返回 null
     */
    public ServiceConfiguration getServiceConfiguration(String serviceType) {
        try {
            Map<String, Object> config = getCurrentConfig();
            if (config == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            @SuppressWarnings("unchecked")
            Map<String, Object> serviceMap = (Map<String, Object>) services.get(serviceType);
            
            return serviceMap != null ? ServiceConfiguration.fromMap(serviceMap) : null;
        } catch (Exception e) {
            logger.error("获取服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取所有服务配置（强类型）
     *
     * @return 所有服务配置
     */
    public Map<String, ServiceConfiguration> getAllServiceConfigurations() {
        try {
            Map<String, Object> config = getCurrentConfig();
            if (config == null || !config.containsKey("services")) {
                return new LinkedHashMap<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            return ServiceConfiguration.fromServicesMap(services);
        } catch (Exception e) {
            logger.error("获取所有服务配置失败：error={}", e.getMessage(), e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 创建服务配置
     *
     * @param serviceType 服务类型
     * @param config 服务配置（强类型）
     * @throws IllegalArgumentException 服务类型已存在或配置无效
     */
    public void createService(String serviceType, ServiceConfiguration config) {
        logger.info("创建新服务：{}", serviceType);

        if (!ServiceTypeConstants.isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型：" + serviceType + 
                "，支持的服务类型：" + ServiceTypeConstants.getServiceTypeList());
        }

        if (config == null) {
            throw new IllegalArgumentException("服务配置不能为空");
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

        if (services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型已存在：" + serviceType);
        }

        // 使用强类型 DTO 转换为 Map 并保存
        services.put(serviceType, config.toMap());

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "createService");
        metadata.put("operationDetail", "创建新服务：" + serviceType);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        saveConfig(currentConfig);

        logger.info("服务 {} 创建成功", serviceType);
    }

    /**
     * 更新服务配置（使用强类型 DTO）
     *
     * @param serviceType 服务类型
     * @param config 新的服务配置
     * @return 更新后的服务配置
     * @throws IllegalArgumentException 服务不存在或配置无效
     */
    public ServiceConfiguration updateServiceConfig(String serviceType, ServiceConfiguration config) {
        logger.info("更新服务配置：{}", serviceType);

        if (config == null) {
            throw new IllegalArgumentException("服务配置不能为空");
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");
        @SuppressWarnings("unchecked")
        Map<String, Object> existingServiceMap = (Map<String, Object>) services.get(serviceType);

        if (existingServiceMap != null) {
            // 转换为强类型对象，保留 instances，合并其他字段
            ServiceConfiguration existingConfig = ServiceConfiguration.fromMap(existingServiceMap);
            
            // 创建合并后的配置：保留现有 instances，更新其他字段
            ServiceConfiguration mergedConfig = new ServiceConfiguration(
                config.adapter() != null ? config.adapter() : existingConfig.adapter(),
                existingConfig.instances(), // 保留现有 instances
                config.loadBalance() != null ? config.loadBalance() : existingConfig.loadBalance(),
                config.rateLimit() != null ? config.rateLimit() : existingConfig.rateLimit(),
                config.circuitBreaker() != null ? config.circuitBreaker() : existingConfig.circuitBreaker(),
                config.fallback() != null ? config.fallback() : existingConfig.fallback()
            );

            services.put(serviceType, mergedConfig.toMap());
            saveConfig(currentConfig);

            logger.info("服务 {} 配置更新成功", serviceType);
            return mergedConfig;
        } else {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }
    }

    /**
     * 使用强类型 DTO 更新服务配置
     *
     * @param serviceType 服务类型
     * @param request 更新请求
     */
    public void updateServiceConfigDto(String serviceType, UpdateServiceConfigRequest request) {
        logger.info("更新服务配置 (DTO)：serviceType={}", serviceType);

        if (request == null) {
            throw new IllegalArgumentException("更新请求不能为空");
        }

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");
        @SuppressWarnings("unchecked")
        Map<String, Object> existingServiceMap = (Map<String, Object>) services.get(serviceType);

        if (existingServiceMap != null) {
            // 转换为强类型对象
            ServiceConfiguration existingConfig = ServiceConfiguration.fromMap(existingServiceMap);
            
            // 创建合并后的配置：保留现有 instances，更新传入的字段
            ServiceConfiguration mergedConfig = new ServiceConfiguration(
                request.getAdapter() != null ? request.getAdapter() : existingConfig.adapter(),
                existingConfig.instances(), // 保留现有 instances
                request.getLoadBalance() != null ? 
                    toLoadBalanceConfig(request.getLoadBalance()) : existingConfig.loadBalance(),
                request.getRateLimit() != null ? 
                    toRateLimitConfig(request.getRateLimit()) : existingConfig.rateLimit(),
                request.getCircuitBreaker() != null ? 
                    toCircuitBreakerConfig(request.getCircuitBreaker()) : existingConfig.circuitBreaker(),
                request.getFallback() != null ? 
                    toFallbackConfig(request.getFallback()) : existingConfig.fallback()
            );

            services.put(serviceType, mergedConfig.toMap());
            saveConfig(currentConfig);

            logger.info("服务 {} 配置更新成功 (DTO)", serviceType);
        } else {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }
    }

    /**
     * 删除服务配置
     *
     * @param serviceType 服务类型
     * @throws IllegalArgumentException 服务不存在
     */
    public void deleteService(String serviceType) {
        logger.info("删除服务配置：{}", serviceType);

        Map<String, Object> currentConfig = getCurrentConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) currentConfig.get("services");

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在：" + serviceType);
        }

        services.remove(serviceType);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "deleteService");
        metadata.put("operationDetail", "删除服务：" + serviceType);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        saveConfig(currentConfig);

        logger.info("服务 {} 删除成功", serviceType);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前配置
     */
    private Map<String, Object> getCurrentConfig() {
        return storeManager.getConfig(CURRENT_KEY);
    }

    /**
     * 保存配置
     */
    private void saveConfig(Map<String, Object> config) {
        storeManager.saveConfig(CURRENT_KEY, config);
    }

    /**
     * 转换 LoadBalanceConfig 到 LoadBalanceConfiguration
     */
    private org.unreal.modelrouter.config.dto.LoadBalanceConfiguration toLoadBalanceConfig(
            org.unreal.modelrouter.dto.LoadBalanceConfig config) {
        if (config == null) {
            return null;
        }
        return new org.unreal.modelrouter.config.dto.LoadBalanceConfiguration(
            config.getType(),
            config.getHashAlgorithm()
        );
    }

    /**
     * 转换 RateLimitConfig 到 RateLimitConfiguration
     */
    private org.unreal.modelrouter.config.dto.RateLimitConfiguration toRateLimitConfig(
            org.unreal.modelrouter.dto.RateLimitConfig config) {
        if (config == null) {
            return null;
        }
        // 根据实际字段转换，使用 capacity 和 rate 估算各时间段的限制
        Integer rate = config.getRate();
        Integer capacity = config.getCapacity();
        return new org.unreal.modelrouter.config.dto.RateLimitConfiguration(
            rate, // requestsPerSecond
            rate != null ? rate * 60 : null, // requestsPerMinute
            rate != null ? rate * 3600 : null, // requestsPerHour
            rate != null ? rate * 86400 : null, // requestsPerDay
            capacity, // burstSize
            config.getEnabled()
        );
    }

    /**
     * 转换 CircuitBreakerConfig 到 CircuitBreakerConfiguration
     */
    private org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration toCircuitBreakerConfig(
            org.unreal.modelrouter.dto.CircuitBreakerConfig config) {
        if (config == null) {
            return null;
        }
        return new org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration(
            config.getFailureThreshold(),
            config.getTimeout() != null ? config.getTimeout().longValue() : null,
            config.getSuccessThreshold(),
            config.getEnabled()
        );
    }

    /**
     * 转换 Fallback 对象到 FallbackConfiguration
     */
    private org.unreal.modelrouter.config.dto.FallbackConfiguration toFallbackConfig(Object fallback) {
        if (fallback == null) {
            return null;
        }
        // 如果 fallback 已经是 FallbackConfiguration，直接返回
        if (fallback instanceof org.unreal.modelrouter.config.dto.FallbackConfiguration fc) {
            return fc;
        }
        // 否则尝试从 Map 转换
        if (fallback instanceof Map<?, ?> map) {
            return org.unreal.modelrouter.config.dto.FallbackConfiguration.fromMap(
                (Map<String, Object>) map);
        }
        // 默认配置
        return org.unreal.modelrouter.config.dto.FallbackConfiguration.defaultConfig();
    }
}
