package org.unreal.modelrouter.config.event;

import java.time.Instant;
import java.util.Map;

/**
 * 配置变更事件 - 核心事件类型
 *
 * 用于通知配置的创建、更新、删除操作。
 * 事件处理器可异步处理审计日志、配置同步、变更通知等。
 *
 * @since v2.12.0
 */
public record ConfigurationChangeEvent(
    /** 变更类型: "CREATE", "UPDATE", "DELETE" */
    String changeType,

    /** 服务类型（如 "chat", "embedding"） */
    String serviceType,

    /** 变更前的配置（DELETE时为null） */
    Map<String, Object> oldConfig,

    /** 变更后的配置（DELETE时为null） */
    Map<String, Object> newConfig,

    /** 执行变更的用户ID */
    String userId,

    /** 事件发生时间 */
    Instant timestamp
) {

    /**
     * 创建配置变更事件（更新操作）
     */
    public static ConfigurationChangeEvent update(
            String serviceType,
            Map<String, Object> oldConfig,
            Map<String, Object> newConfig,
            String userId) {
        return new ConfigurationChangeEvent(
            "UPDATE", serviceType, oldConfig, newConfig, userId, Instant.now()
        );
    }

    /**
     * 创建配置变更事件（创建操作）
     */
    public static ConfigurationChangeEvent create(
            String serviceType,
            Map<String, Object> newConfig,
            String userId) {
        return new ConfigurationChangeEvent(
            "CREATE", serviceType, null, newConfig, userId, Instant.now()
        );
    }

    /**
     * 创建配置变更事件（删除操作）
     */
    public static ConfigurationChangeEvent delete(
            String serviceType,
            Map<String, Object> oldConfig,
            String userId) {
        return new ConfigurationChangeEvent(
            "DELETE", serviceType, oldConfig, null, userId, Instant.now()
        );
    }

    /**
     * 判断是否为更新操作
     */
    public boolean isUpdate() {
        return "UPDATE".equals(changeType);
    }

    /**
     * 判断是否为创建操作
     */
    public boolean isCreate() {
        return "CREATE".equals(changeType);
    }

    /**
     * 判断是否为删除操作
     */
    public boolean isDelete() {
        return "DELETE".equals(changeType);
    }
}