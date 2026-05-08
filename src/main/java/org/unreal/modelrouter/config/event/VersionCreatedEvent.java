package org.unreal.modelrouter.config.event;

import java.time.Instant;
import java.util.Map;

/**
 * 版本创建事件
 *
 * 用于通知新配置版本的创建，事件处理器可异步保存版本历史。
 *
 * @since v2.12.0
 */
public record VersionCreatedEvent(
    /** 版本号 */
    int versionNumber,

    /** 版本描述 */
    String description,

    /** 配置快照 */
    Map<String, Object> configSnapshot,

    /** 创建版本的用户ID */
    String userId,

    /** 事件发生时间 */
    Instant timestamp
) {

    /**
     * 创建版本创建事件
     */
    public static VersionCreatedEvent of(
            int versionNumber,
            String description,
            Map<String, Object> configSnapshot,
            String userId) {
        return new VersionCreatedEvent(
            versionNumber, description, configSnapshot, userId, Instant.now()
        );
    }

    /**
     * 创建版本创建事件（无描述）
     */
    public static VersionCreatedEvent of(
            int versionNumber,
            Map<String, Object> configSnapshot,
            String userId) {
        return new VersionCreatedEvent(
            versionNumber, "Auto saved", configSnapshot, userId, Instant.now()
        );
    }
}