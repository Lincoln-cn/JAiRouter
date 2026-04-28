package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.ConfigurationHelper;
import org.unreal.modelrouter.constants.ServiceTypeConstants;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.util.InstanceIdUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置验证器
 * 
 * 负责配置数据的验证和标准化
 * 提供服务配置和实例配置的校验功能
 * 
 * @author AI Assistant
 * @since v2.2.0
 */
@Component
public class ConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigValidator.class);

    private final ConfigurationHelper configurationHelper;

    public ConfigValidator(final ConfigurationHelper configurationHelper) {
        this.configurationHelper = configurationHelper;
    }

    /**
     * 验证和标准化服务配置
     *
     * @param serviceConfig 服务配置
     * @return 标准化后的服务配置
     */
    public Map<String, Object> validateAndNormalizeServiceConfig(final Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new HashMap<>(serviceConfig);

        // 确保 instances 字段存在
        if (!normalized.containsKey("instances")) {
            normalized.put("instances", new ArrayList<>());
        }

        // 验证 instances 是 List 类型
        if (!(normalized.get("instances") instanceof List)) {
            normalized.put("instances", new ArrayList<>());
        }

        logger.debug("验证和标准化服务配置 - 输入：{}", serviceConfig);
        logger.debug("验证和标准化服务配置 - 输出：{}", normalized);

        return normalized;
    }

    /**
     * 验证和标准化实例配置
     *
     * @param instanceConfig 实例配置
     * @return 标准化后的实例配置
     */
    public Map<String, Object> validateAndNormalizeInstanceConfig(final Map<String, Object> instanceConfig) {
        Map<String, Object> normalized = new HashMap<>(instanceConfig);

        // 必需字段验证
        if (!normalized.containsKey("name") || normalized.get("name") == null) {
            throw new IllegalArgumentException("实例名称不能为空");
        }

        if (!normalized.containsKey("baseUrl") || normalized.get("baseUrl") == null) {
            throw new IllegalArgumentException("实例 baseUrl 不能为空");
        }

        // 设置默认值
        if (!normalized.containsKey("weight")) {
            normalized.put("weight", 1);
        }

        // 添加 status 字段的默认值
        if (!normalized.containsKey("status")) {
            normalized.put("status", "active");
        }

        // 确保 instanceId 字段存在
        if (!normalized.containsKey("instanceId") || normalized.get("instanceId") == null) {
            String name = (String) normalized.get("name");
            String baseUrl = (String) normalized.get("baseUrl");
            if (name != null && baseUrl != null) {
                normalized.put("instanceId", InstanceIdUtils.getInstanceId(instanceConfig));
            }
        }

        logger.debug("验证和标准化实例配置 - 输入：{}", instanceConfig);
        logger.debug("验证和标准化实例配置 - 输出：{}", normalized);
        logger.debug("验证和标准化实例配置 - headers 字段：{}", normalized.get("headers"));

        return normalized;
    }

    /**
     * 验证服务类型是否有效
     *
     * @param serviceType 服务类型
     * @return true 如果服务类型有效
     */
    public boolean isValidServiceType(final String serviceType) {
        if (serviceType == null) {
            return false;
        }

        try {
            // 标准化处理：转小写，移除空格、下划线和连字符
            String normalizedKey = serviceType.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[\\s_-]+", "");
            // 直接匹配枚举值
            ModelServiceRegistry.ServiceType.valueOf(normalizedKey);
            return true;
        } catch (Exception e) {
            // 处理常见的别名映射
            return isValidServiceTypeAlias(serviceType);
        }
    }

    /**
     * 合并实例配置
     *
     * @param existing 现有配置
     * @param updates  更新内容
     * @return 合并后的配置
     */
    public Map<String, Object> mergeInstanceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(updates);

        // 确保 instanceId 字段存在
        if (!merged.containsKey("instanceId")) {
            String name = (String) merged.get("name");
            String baseUrl = (String) merged.get("baseUrl");
            if (name != null && baseUrl != null) {
                merged.put("instanceId", InstanceIdUtils.getInstanceId(existing));
            }
        }

        return merged;
    }

    /**
     * 合并服务配置
     *
     * @param existing 现有配置
     * @param updates  更新内容
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeServiceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // instances 字段不合并，直接替换
                merged.put(key, value);
            } else if (existing.containsKey(key)
                    && existing.get(key) instanceof Map
                    && value instanceof Map) {
                // 递归合并 Map 类型字段
                Map<String, Object> existingMap = (Map<String, Object>) existing.get(key);
                Map<String, Object> updateMap = (Map<String, Object>) value;
                merged.put(key, mergeServiceConfig(existingMap, updateMap));
            } else {
                merged.put(key, value);
            }
        }

        return merged;
    }

    /**
     * 创建默认服务配置
     *
     * @return 默认服务配置
     */
    public Map<String, Object> createDefaultServiceConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("instances", new ArrayList<>());

        // 添加默认负载均衡配置
        Map<String, Object> loadBalance = new HashMap<>();
        loadBalance.put("type", "random");
        loadBalance.put("hashAlgorithm", "md5");
        config.put("loadBalance", loadBalance);

        return config;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查是否是有效的服务类型别名
     */
    private boolean isValidServiceTypeAlias(final String serviceType) {
        String lowerServiceType = serviceType.toLowerCase(java.util.Locale.ROOT);

        // 使用常量类进行匹配
        if (lowerServiceType.equals(ServiceTypeConstants.CHAT)
                || lowerServiceType.equals("chat-completion")
                || lowerServiceType.equals("chat-completions")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.EMBEDDING)
                || lowerServiceType.equals("embeddings")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.RERANK)
                || lowerServiceType.equals("re-rank")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.TTS)
                || lowerServiceType.equals("text-to-speech")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.STT)
                || lowerServiceType.equals("speech-to-text")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.IMG_GEN)
                || lowerServiceType.equals("imggen")
                || lowerServiceType.equals("image-generation")
                || lowerServiceType.equals("image-generate")) {
            return true;
        }

        if (lowerServiceType.equals(ServiceTypeConstants.IMG_EDIT)
                || lowerServiceType.equals("image-edit")
                || lowerServiceType.equals("image-editing")) {
            return true;
        }

        return false;
    }
}
