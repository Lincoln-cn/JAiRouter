package org.unreal.modelrouter.config.core.helper;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.constants.ServiceTypeConstants;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

/**
 * 服务类型解析器
 *
 * 负责解析和验证服务类型，支持多种格式（连字符、下划线、驼峰等）。
 *
 * @author JAiRouter Team
 * @since v2.13.1
 */
@Component
public class ServiceTypeResolver {

    /**
     * 解析服务类型（支持多种格式）
     *
     * @param serviceKey 服务键
     * @return 服务类型枚举，如果无法识别则返回null
     */
    public ModelServiceRegistry.ServiceType parseServiceType(final String serviceKey) {
        if (serviceKey == null || serviceKey.trim().isEmpty()) {
            return null;
        }

        try {
            // 标准化处理：转小写，统一格式
            String normalizedKey = serviceKey.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[\\s_-]+", ""); // 移除空格、下划线和连字符

            // 尝试直接匹配枚举值
            return ModelServiceRegistry.ServiceType.valueOf(normalizedKey);
        } catch (IllegalArgumentException e) {
            // 处理常见的别名映射
            return mapServiceTypeAlias(serviceKey);
        }
    }

    /**
     * 获取服务配置键
     *
     * @param serviceType 服务类型
     * @return 服务配置键（连字符格式）
     */
    public String getServiceConfigKey(final ModelServiceRegistry.ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }

        // 使用连字符格式作为标准键名
        return serviceType.name().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * 验证服务类型有效性
     *
     * @param serviceType 服务类型字符串
     * @return 是否有效
     */
    public boolean isValidServiceType(final String serviceType) {
        if (serviceType == null) {
            return false;
        }

        try {
            return parseServiceType(serviceType) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 映射服务类型别名到枚举
     *
     * @param serviceKey 服务键
     * @return 服务类型枚举，如果无法识别则返回null
     */
    private ModelServiceRegistry.ServiceType mapServiceTypeAlias(final String serviceKey) {
        String lower = serviceKey.toLowerCase(java.util.Locale.ROOT);

        if (lower.equals(ServiceTypeConstants.CHAT)
            || lower.equals("chat-completion")
            || lower.equals("chat-completions")) {
            return ModelServiceRegistry.ServiceType.chat;
        }

        if (lower.equals(ServiceTypeConstants.EMBEDDING)
            || lower.equals("embeddings")) {
            return ModelServiceRegistry.ServiceType.embedding;
        }

        if (lower.equals(ServiceTypeConstants.RERANK)
            || lower.equals("re-rank")) {
            return ModelServiceRegistry.ServiceType.rerank;
        }

        if (lower.equals(ServiceTypeConstants.TTS)
            || lower.equals("text-to-speech")) {
            return ModelServiceRegistry.ServiceType.tts;
        }

        if (lower.equals(ServiceTypeConstants.STT)
            || lower.equals("speech-to-text")) {
            return ModelServiceRegistry.ServiceType.stt;
        }

        if (lower.equals(ServiceTypeConstants.IMG_GEN)
            || lower.equals("imggen")
            || lower.equals("image-generation")
            || lower.equals("image-generate")) {
            return ModelServiceRegistry.ServiceType.imgGen;
        }

        if (lower.equals(ServiceTypeConstants.IMG_EDIT)
            || lower.equals("image-edit")
            || lower.equals("image-editing")) {
            return ModelServiceRegistry.ServiceType.imgEdit;
        }

        return null;
    }
}