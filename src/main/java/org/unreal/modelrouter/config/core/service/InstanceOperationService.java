package org.unreal.modelrouter.config.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.core.manager.ConfigComparisonService;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.common.util.InstanceIdUtils;

import java.util.List;
import java.util.Map;

/**
 * 实例操作服务
 *
 * 封装实例的增删改操作，从 ConfigurationService 提取。
 * 负责实例级别的操作逻辑，不涉及配置获取、版本控制等协调职责。
 *
 * @since v2.6.15
 */
@Service
public class InstanceOperationService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceOperationService.class);

    private final ConfigValidator configValidator;
    private final ConfigComparisonService configComparisonService;

    public InstanceOperationService(final ConfigValidator configValidator,
                                     final ConfigComparisonService configComparisonService) {
        this.configValidator = configValidator;
        this.configComparisonService = configComparisonService;
    }

    /**
     * 添加实例到实例列表
     *
     * @param instances      实例列表
     * @param instanceConfig 实例配置（已转换为 Map）
     * @return 操作详情字符串
     * @throws IllegalArgumentException 如果实例已存在
     */
    public String addInstance(final List<Map<String, Object>> instances,
                               final Map<String, Object> instanceConfig) {
        // 验证并规范化实例配置
        Map<String, Object> validatedInstance = configValidator.validateAndNormalizeInstanceConfig(instanceConfig);

        String name = (String) validatedInstance.get("name");
        String baseUrl = (String) validatedInstance.get("baseUrl");

        // 检查是否已存在
        if (instanceExists(instances, name, baseUrl)) {
            throw new IllegalArgumentException("实例已存在: " + name + "@" + baseUrl);
        }

        instances.add(validatedInstance);
        String detail = "添加 " + name + "@" + baseUrl;
        logger.debug("实例操作: {}", detail);
        return detail;
    }

    /**
     * 更新实例
     *
     * @param instances       实例列表
     * @param instanceId      实例ID
     * @param newInstanceConfig 新的实例配置（已转换为 Map）
     * @return 操作详情字符串
     * @throws IllegalArgumentException 如果实例不存在
     */
    public String updateInstance(final List<Map<String, Object>> instances,
                                  final String instanceId,
                                  final Map<String, Object> newInstanceConfig) {
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            if (instanceId.equals(InstanceIdUtils.getInstanceId(instance))) {
                // 合并配置
                Map<String, Object> updated = configComparisonService.mergeInstanceConfig(instance, newInstanceConfig);
                // 验证并更新
                Map<String, Object> validated = configValidator.validateAndNormalizeInstanceConfig(updated);
                instances.set(i, validated);
                String detail = "更新 " + instanceId;
                logger.debug("实例操作: {}", detail);
                return detail;
            }
        }

        throw new IllegalArgumentException("实例不存在: " + instanceId);
    }

    /**
     * 删除实例
     *
     * @param instances  实例列表
     * @param instanceId 实例ID
     * @return 操作详情字符串
     * @throws IllegalArgumentException 如果实例不存在
     */
    public String deleteInstance(final List<Map<String, Object>> instances,
                                  final String instanceId) {
        boolean removed = instances.removeIf(inst ->
                instanceId.equals(InstanceIdUtils.getInstanceId(inst)));

        if (!removed) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        String detail = "删除 " + instanceId;
        logger.debug("实例操作: {}", detail);
        return detail;
    }

    /**
     * 检查实例是否存在
     *
     * @param instances 实例列表
     * @param name      实例名称
     * @param baseUrl   实例URL
     * @return true 如果存在
     */
    public boolean instanceExists(final List<Map<String, Object>> instances,
                                   final String name, final String baseUrl) {
        return instances.stream().anyMatch(inst ->
                name.equals(inst.get("name")) && baseUrl.equals(inst.get("baseUrl")));
    }

    /**
     * 根据实例ID查找实例
     *
     * @param instances  实例列表
     * @param instanceId 实例ID
     * @return 实例配置，如果不存在返回 null
     */
    public Map<String, Object> findInstanceById(final List<Map<String, Object>> instances,
                                                 final String instanceId) {
        for (Map<String, Object> instance : instances) {
            if (instanceId.equals(InstanceIdUtils.getInstanceId(instance))) {
                return instance;
            }
        }
        return null;
    }

    /**
     * 验证实例配置
     *
     * @param instanceConfig 实例配置
     * @return 验证后的配置
     */
    public Map<String, Object> validateInstanceConfig(final Map<String, Object> instanceConfig) {
        return configValidator.validateAndNormalizeInstanceConfig(instanceConfig);
    }
}
