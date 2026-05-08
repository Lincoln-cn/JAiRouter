package org.unreal.modelrouter.auth.security.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import reactor.core.publisher.Mono;

/**
 * API Key 审计事件监听器
 *
 * 异步处理 API Key 审计事件，解耦审计逻辑与业务逻辑。
 * 使用 Spring Event + @Async 实现非阻塞事件处理。
 *
 * 性能优势：
 * 1. 审计事件发布后立即返回，不等待处理
 * 2. 异步批量处理审计日志
 * 3. 解耦审计逻辑和业务逻辑
 * 4. 更好的可扩展性
 *
 * @since v2.14.3
 */
@Slf4j
@Component
public class ApiKeyAuditEventListener {

    @Autowired(required = false)
    private ExtendedSecurityAuditService extendedAuditService;

    /**
     * 处理 API Key 创建事件
     */
    @Async
    @EventListener
    public void onApiKeyCreated(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.CREATED) {
            return;
        }

        log.debug("异步处理 API Key 创建事件: {}", event.keyId());

        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyCreated(
                    event.keyId(),
                    event.operator(),
                    event.ipAddress()
            ).onErrorResume(ex -> {
                log.warn("记录 API Key 创建审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理 API Key 使用事件
     */
    @Async
    @EventListener
    public void onApiKeyUsed(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.USED) {
            return;
        }

        log.debug("异步处理 API Key 使用事件: {} (success: {})",
                event.keyId(), event.success());

        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyUsed(
                    event.keyId(),
                    event.endpoint(),
                    event.ipAddress(),
                    event.success()
            ).onErrorResume(ex -> {
                log.warn("记录 API Key 使用审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理 API Key 撤销事件
     */
    @Async
    @EventListener
    public void onApiKeyRevoked(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.REVOKED) {
            return;
        }

        log.debug("异步处理 API Key 撤销事件: {}", event.keyId());

        if (extendedAuditService != null) {
            extendedAuditService.auditApiKeyRevoked(
                    event.keyId(),
                    event.message(),
                    event.operator()
            ).onErrorResume(ex -> {
                log.warn("记录 API Key 撤销审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理 API Key 轮换事件
     */
    @Async
    @EventListener
    public void onApiKeyRotated(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.ROTATED) {
            return;
        }

        log.debug("异步处理 API Key 轮换事件: {}", event.keyId());

        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent(
                    "API_KEY_ROTATED",
                    "密钥已轮换",
                    event.keyId(),
                    null
            ).onErrorResume(ex -> {
                log.warn("记录密钥轮换审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理 API Key 过期事件
     */
    @Async
    @EventListener
    public void onApiKeyExpired(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.EXPIRED) {
            return;
        }

        log.debug("异步处理 API Key 过期事件: {}", event.keyId());

        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent(
                    "API_KEY_EXPIRED",
                    "密钥已过期，自动禁用",
                    event.keyId(),
                    null
            ).onErrorResume(ex -> {
                log.warn("记录密钥过期审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理安全事件
     */
    @Async
    @EventListener
    public void onSecurityEvent(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.SECURITY_EVENT) {
            return;
        }

        log.debug("异步处理安全事件: {} - {}", event.keyId(), event.message());

        if (extendedAuditService != null) {
            // 解析事件类型
            String eventType = extractEventType(event.message());
            extendedAuditService.auditSecurityEvent(
                    eventType,
                    event.message(),
                    event.keyId(),
                    event.ipAddress()
            ).onErrorResume(ex -> {
                log.warn("记录安全审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 处理批量导入事件
     */
    @Async
    @EventListener
    public void onBatchImport(final ApiKeyAuditEvent event) {
        if (event.eventType() != ApiKeyAuditEvent.EventType.BATCH_IMPORT) {
            return;
        }

        log.debug("异步处理批量导入事件: {}", event.message());

        if (extendedAuditService != null) {
            extendedAuditService.auditSecurityEvent(
                    "API_KEY_BATCH_IMPORT",
                    event.message(),
                    null,
                    event.ipAddress()
            ).onErrorResume(ex -> {
                log.warn("记录批量导入审计失败: {}", ex.getMessage());
                return Mono.empty();
            }).subscribe();
        }
    }

    /**
     * 从消息中提取事件类型
     */
    private String extractEventType(final String message) {
        if (message == null || !message.startsWith("[")) {
            return "SECURITY_EVENT";
        }
        int endIndex = message.indexOf("]");
        if (endIndex > 1) {
            return message.substring(1, endIndex);
        }
        return "SECURITY_EVENT";
    }
}