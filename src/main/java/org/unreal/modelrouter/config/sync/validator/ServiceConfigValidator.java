package org.unreal.modelrouter.config.sync.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.core.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.core.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.core.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.core.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.common.constants.ServiceTypeConstants;

import java.util.List;

/**
 * 服务配置验证器
 *
 * 负责验证服务配置的合法性，包括服务类型、配置完整性、实例配置等。
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
@Component
public class ServiceConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigValidator.class);

    /**
     * 验证服务类型是否合法
     *
     * @param serviceType 服务类型
     * @throws IllegalArgumentException 服务类型无效
     */
    public void validateServiceType(final String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("服务类型不能为空");
        }

        if (!ServiceTypeConstants.isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型：" + serviceType +
                "，支持的服务类型：" + ServiceTypeConstants.getServiceTypeList());
        }

        logger.debug("服务类型验证通过：{}", serviceType);
    }

    /**
     * 验证配置完整性
     *
     * @param config 服务配置
     * @throws IllegalArgumentException 配置无效
     */
    public void validateConfiguration(final ServiceConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("服务配置不能为空");
        }

        // 验证适配器配置
        if (config.adapter() == null || config.adapter().isBlank()) {
            throw new IllegalArgumentException("适配器配置不能为空");
        }

        // 验证负载均衡配置
        if (config.loadBalance() != null) {
            validateLoadBalanceConfig(config.loadBalance());
        }

        // 验证限流配置
        if (config.rateLimit() != null) {
            validateRateLimitConfig(config.rateLimit());
        }

        // 验证熔断器配置
        if (config.circuitBreaker() != null) {
            validateCircuitBreakerConfig(config.circuitBreaker());
        }

        // 验证降级配置
        if (config.fallback() != null) {
            validateFallbackConfig(config.fallback());
        }

        logger.debug("服务配置验证通过：adapter={}", config.adapter());
    }

    /**
     * 验证实例配置
     *
     * @param instances 实例列表
     * @throws IllegalArgumentException 实例配置无效
     */
    public void validateInstances(final List<ModelInstanceConfiguration> instances) {
        if (instances == null || instances.isEmpty()) {
            logger.debug("实例列表为空，跳过验证");
            return;
        }

        for (ModelInstanceConfiguration instance : instances) {
            validateInstance(instance);
        }

        logger.debug("实例配置验证通过：count={}", instances.size());
    }

    /**
     * 验证单个实例配置
     *
     * @param instance 实例配置
     * @throws IllegalArgumentException 实例配置无效
     */
    private void validateInstance(final ModelInstanceConfiguration instance) {
        if (instance == null) {
            throw new IllegalArgumentException("实例配置不能为空");
        }

        // 验证 baseUrl
        if (instance.baseUrl() == null || instance.baseUrl().isBlank()) {
            throw new IllegalArgumentException("实例 baseUrl 不能为空");
        }

        // 验证权重（如果有）
        if (instance.weight() != null && instance.weight() < 0) {
            throw new IllegalArgumentException("实例权重不能为负数：" + instance.weight());
        }

        // 验证健康状态
        if (instance.status() != null && !"active".equals(instance.status()) && !"inactive".equals(instance.status())) {
            throw new IllegalArgumentException("实例状态必须是 active 或 inactive：" + instance.status());
        }
    }

    /**
     * 验证负载均衡配置
     *
     * @param config 负载均衡配置
     * @throws IllegalArgumentException 配置无效
     */
    public void validateLoadBalanceConfig(final LoadBalanceConfiguration config) {
        if (config == null) {
            return;
        }

        String type = config.type();
        if (type != null && !type.isBlank()) {
            // 使用 LoadBalanceConfiguration.Type 验证
            LoadBalanceConfiguration.Type.fromString(type);
        }

        // 验证 hashAlgorithm（如果有）
        if (config.hashAlgorithm() != null && config.hashAlgorithm().isBlank()) {
            throw new IllegalArgumentException("hashAlgorithm 不能为空字符串");
        }

        logger.debug("负载均衡配置验证通过：type={}", type);
    }

    /**
     * 验证限流配置
     *
     * @param config 限流配置
     * @throws IllegalArgumentException 配置无效
     */
    public void validateRateLimitConfig(final RateLimitConfiguration config) {
        if (config == null) {
            return;
        }

        // 验证速率限制值
        if (config.requestsPerSecond() != null && config.requestsPerSecond() < 0) {
            throw new IllegalArgumentException("每秒请求数不能为负数：" + config.requestsPerSecond());
        }

        if (config.requestsPerMinute() != null && config.requestsPerMinute() < 0) {
            throw new IllegalArgumentException("每分钟请求数不能为负数：" + config.requestsPerMinute());
        }

        if (config.requestsPerHour() != null && config.requestsPerHour() < 0) {
            throw new IllegalArgumentException("每小时请求数不能为负数：" + config.requestsPerHour());
        }

        if (config.requestsPerDay() != null && config.requestsPerDay() < 0) {
            throw new IllegalArgumentException("每天请求数不能为负数：" + config.requestsPerDay());
        }

        // 验证突发容量
        if (config.burstSize() != null && config.burstSize() < 0) {
            throw new IllegalArgumentException("突发容量不能为负数：" + config.burstSize());
        }

        logger.debug("限流配置验证通过：enabled={}", config.enabled());
    }

    /**
     * 验证熔断器配置
     *
     * @param config 熔断器配置
     * @throws IllegalArgumentException 配置无效
     */
    public void validateCircuitBreakerConfig(final CircuitBreakerConfiguration config) {
        if (config == null) {
            return;
        }

        // 验证失败阈值
        if (config.failureThreshold() != null && config.failureThreshold() < 0) {
            throw new IllegalArgumentException("失败阈值不能为负数：" + config.failureThreshold());
        }

        // 验证超时时间
        if (config.timeout() != null && config.timeout() < 0) {
            throw new IllegalArgumentException("超时时间不能为负数：" + config.timeout());
        }

        // 验证成功阈值
        if (config.successThreshold() != null && config.successThreshold() < 0) {
            throw new IllegalArgumentException("成功阈值不能为负数：" + config.successThreshold());
        }

        logger.debug("熔断器配置验证通过：enabled={}", config.enabled());
    }

    /**
     * 验证降级配置
     *
     * @param config 降级配置
     * @throws IllegalArgumentException 配置无效
     */
    public void validateFallbackConfig(final FallbackConfiguration config) {
        if (config == null) {
            return;
        }

        // 验证降级 URL
        if (config.fallbackUrl() != null && config.fallbackUrl().isBlank()) {
            throw new IllegalArgumentException("降级 URL 不能为空字符串");
        }

        // 验证最大重试次数
        if (config.maxRetries() != null && config.maxRetries() < 0) {
            throw new IllegalArgumentException("最大重试次数不能为负数：" + config.maxRetries());
        }

        // 验证重试间隔
        if (config.retryInterval() != null && config.retryInterval() < 0) {
            throw new IllegalArgumentException("重试间隔不能为负数：" + config.retryInterval());
        }

        logger.debug("降级配置验证通过：enabled={}", config.enabled());
    }
}
