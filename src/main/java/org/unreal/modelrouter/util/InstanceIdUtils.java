package org.unreal.modelrouter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class InstanceIdUtils {

    private static final Logger logger = LoggerFactory.getLogger(InstanceIdUtils.class);

    /**
     * 获取实例ID
     *
     * @param instanceConfig 实例配置
     * @return 实例ID
     */
    public static String getInstanceId(Map<String, Object> instanceConfig) {
        if (instanceConfig == null) {
            return null;
        }

        // 检查是否已存在instanceId字段
        Object instanceIdObj = instanceConfig.get("instanceId");
        if (instanceIdObj instanceof String existingInstanceId && !((String) instanceIdObj).isEmpty()) {
            // 验证已存在的instanceId是否为有效的UUID格式，如果不是则重新生成
            if (isValidUUID(existingInstanceId)) {
                return existingInstanceId;
            } else {
                logger.warn("实例ID {} 不是有效的UUID格式，将重新生成", existingInstanceId);
            }
        }

        Object nameObj = instanceConfig.get("name");
        // 同时支持baseUrl和base-url两种字段名
        Object baseUrlObj = instanceConfig.get("baseUrl");
        if (baseUrlObj == null) {
            baseUrlObj = instanceConfig.get("base-url");
        }

        String name = nameObj instanceof String ? (String) nameObj : null;
        String baseUrl = baseUrlObj instanceof String ? (String) baseUrlObj : null;

        if (name != null && !name.trim().isEmpty()
                && baseUrl != null && !baseUrl.trim().isEmpty()) {
            // 使用UUID生成唯一ID
            return SecurityUtils.generateId();
        }
        return null;
    }

    /**
     * 验证字符串是否为有效的UUID
     *
     * @param uuid UUID字符串
     * @return 是否有效
     */
    private static boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
