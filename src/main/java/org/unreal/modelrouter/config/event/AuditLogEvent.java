package org.unreal.modelrouter.config.event;

import java.time.Instant;
import java.util.Map;

/**
 * 审计日志事件
 *
 * 用于异步记录配置操作的审计日志。
 *
 * @since v2.12.0
 */
public record AuditLogEvent(
    /** 操作类型: "CREATE", "UPDATE", "DELETE", "ROLLBACK", "VERSION_CREATE" */
    String operation,

    /** 实体类型: "SERVICE_CONFIG", "INSTANCE", "TRACE_CONFIG", "VERSION" */
    String entityType,

    /** 实体ID（服务类型或实例ID） */
    String entityId,

    /** 操作数据（脱敏后的配置数据） */
    Map<String, Object> data,

    /** 执行操作的用户ID */
    String userId,

    /** 事件发生时间 */
    Instant timestamp
) {

    /**
     * 创建服务配置审计事件
     */
    public static AuditLogEvent serviceConfig(
            String operation,
            String serviceType,
            Map<String, Object> data,
            String userId) {
        return new AuditLogEvent(
            operation, "SERVICE_CONFIG", serviceType, data, userId, Instant.now()
        );
    }

    /**
     * 创建实例审计事件
     */
    public static AuditLogEvent instance(
            String operation,
            String instanceId,
            Map<String, Object> data,
            String userId) {
        return new AuditLogEvent(
            operation, "INSTANCE", instanceId, data, userId, Instant.now()
        );
    }

    /**
     * 创建版本审计事件
     */
    public static AuditLogEvent version(
            String operation,
            int versionNumber,
            Map<String, Object> data,
            String userId) {
        return new AuditLogEvent(
            operation, "VERSION", String.valueOf(versionNumber), data, userId, Instant.now()
        );
    }

    /**
     * 创建追踪配置审计事件
     */
    public static AuditLogEvent traceConfig(
            String operation,
            Map<String, Object> data,
            String userId) {
        return new AuditLogEvent(
            operation, "TRACE_CONFIG", "default", data, userId, Instant.now()
        );
    }
}