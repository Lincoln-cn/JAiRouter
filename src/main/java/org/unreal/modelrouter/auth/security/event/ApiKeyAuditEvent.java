package org.unreal.modelrouter.auth.security.event;

import java.time.Instant;

/**
 * API Key 审计事件
 *
 * 用于异步记录 API Key 相关操作的审计日志，
 * 解耦审计逻辑与业务逻辑，提升性能。
 *
 * @since v2.14.3
 */
public record ApiKeyAuditEvent(
    /** 事件类型 */
    EventType eventType,

    /** API Key ID */
    String keyId,

    /** 事件消息 */
    String message,

    /** 操作者 */
    String operator,

    /** 客户端 IP */
    String ipAddress,

    /** 端点（仅用于使用事件） */
    String endpoint,

    /** 操作是否成功 */
    Boolean success,

    /** 事件发生时间 */
    Instant timestamp
) {

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /** API Key 创建 */
        CREATED,
        /** API Key 使用 */
        USED,
        /** API Key 撤销/删除 */
        REVOKED,
        /** API Key 轮换 */
        ROTATED,
        /** API Key 过期 */
        EXPIRED,
        /** 安全事件（如无效密钥、IP限制等） */
        SECURITY_EVENT,
        /** 批量导入 */
        BATCH_IMPORT
    }

    /**
     * 创建 API Key 创建事件
     */
    public static ApiKeyAuditEvent created(final String keyId,
                                            final String createdBy,
                                            final String ipAddress) {
        return new ApiKeyAuditEvent(
            EventType.CREATED, keyId, "API Key 已创建",
            createdBy, ipAddress, null, true, Instant.now()
        );
    }

    /**
     * 创建 API Key 使用事件
     */
    public static ApiKeyAuditEvent used(final String keyId,
                                         final String endpoint,
                                         final String ipAddress,
                                         final boolean success) {
        return new ApiKeyAuditEvent(
            EventType.USED, keyId,
            success ? "API Key 验证成功" : "API Key 验证失败",
            null, ipAddress, endpoint, success, Instant.now()
        );
    }

    /**
     * 创建 API Key 撤销事件
     */
    public static ApiKeyAuditEvent revoked(final String keyId,
                                            final String reason,
                                            final String revokedBy) {
        return new ApiKeyAuditEvent(
            EventType.REVOKED, keyId, reason,
            revokedBy, null, null, true, Instant.now()
        );
    }

    /**
     * 创建 API Key 轮换事件
     */
    public static ApiKeyAuditEvent rotated(final String keyId,
                                            final String rotatedBy) {
        return new ApiKeyAuditEvent(
            EventType.ROTATED, keyId, "API Key 已轮换",
            rotatedBy, null, null, true, Instant.now()
        );
    }

    /**
     * 创建 API Key 过期事件
     */
    public static ApiKeyAuditEvent expired(final String keyId) {
        return new ApiKeyAuditEvent(
            EventType.EXPIRED, keyId, "API Key 已过期，自动禁用",
            null, null, null, false, Instant.now()
        );
    }

    /**
     * 创建安全事件
     */
    public static ApiKeyAuditEvent securityEvent(final String eventType,
                                                   final String message,
                                                   final String keyId,
                                                   final String ipAddress) {
        return new ApiKeyAuditEvent(
            EventType.SECURITY_EVENT, keyId,
            "[" + eventType + "] " + message,
            null, ipAddress, null, false, Instant.now()
        );
    }

    /**
     * 创建批量导入事件
     */
    public static ApiKeyAuditEvent batchImport(final String ipAddress,
                                                final int successCount,
                                                final int failureCount) {
        return new ApiKeyAuditEvent(
            EventType.BATCH_IMPORT, null,
            "批量导入完成: 成功 " + successCount + ", 失败 " + failureCount,
            null, ipAddress, null, failureCount == 0, Instant.now()
        );
    }
}