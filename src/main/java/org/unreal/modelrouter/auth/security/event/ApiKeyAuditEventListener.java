package org.unreal.modelrouter.auth.security.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityAlertService;
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
     * 安全告警服务（issue #276）.
     *
     * <p>该服务此前<b>没有任何调用方</b>，四类告警实际都不会产生；本监听器作为认证相关事件的
     * 单一汇聚点，在失败事件上接线它的「失败次数陡增」与「可疑 IP」两项检查。
     * 用可选注入以兼容 {@code jairouter.security.alerts.enabled=false} 时不装配该 bean 的场景。</p>
     */
    @Autowired(required = false)
    private SecurityAlertService securityAlertService;

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

        // 认证失败时驱动安全告警（issue #276）
        if (!Boolean.TRUE.equals(event.success())) {
            triggerAuthenticationFailureAlerts(event.ipAddress(), event.message());
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
                    AuditEventType.API_KEY_ROTATED.name(),
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
                    AuditEventType.API_KEY_EXPIRED.name(),
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

        // 安全事件（无效密钥 / IP 限制等）同样驱动安全告警（issue #276）
        triggerAuthenticationFailureAlerts(event.ipAddress(), event.message());
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
                    AuditEventType.API_KEY_BATCH_IMPORT.name(),
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
     * 触发认证失败相关的安全告警（issue #276）.
     *
     * <p>{@link SecurityAlertService} 的检查实现完整但此前**零调用方**，因此这些告警实际不会产生。
     * 这里以本监听器为单一汇聚点接线：一次失败事件同时驱动「失败次数陡增」与「可疑 IP」两项检查
     * （两者各自带阈值与冷却，重复触发会被抑制）。</p>
     *
     * <p>两项检查都返回 {@code Mono<Void>}，按本文件既有范式
     * {@code onErrorResume(...).subscribe()} 触发 —— 不阻塞、不影响主流程，告警自身失败只记日志。
     * 不与 {@code extendedAuditService} 的判空耦合：审计服务缺失时告警仍应生效。</p>
     *
     * @param ipAddress 客户端 IP（为空则跳过，避免无意义的分组）
     * @param reason    失败原因（用于告警内容）
     */
    private void triggerAuthenticationFailureAlerts(final String ipAddress, final String reason) {
        if (securityAlertService == null || ipAddress == null) {
            return;
        }
        securityAlertService.checkAuthenticationFailureAlert(ipAddress, reason)
                .onErrorResume(ex -> {
                    log.warn("认证失败告警检查失败: {}", ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
        securityAlertService.checkSuspiciousIpAlert(ipAddress)
                .onErrorResume(ex -> {
                    log.warn("可疑 IP 告警检查失败: {}", ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * 从消息中提取事件类型
     *
     * <p>类型名由发布方拼进消息正文（见 {@link ApiKeyAuditEvent#securityEvent}），这里是
     * 「任意字符串 → 审计事件类型」的唯一开口。{@code AuditEntityMapper#parseEventType}
     * 自 issue #301 起不再静默回退，故未知前缀必须在此就地降级，否则整条审计记录会因解析
     * 失败而丢失。</p>
     */
    private String extractEventType(final String message) {
        String candidate = null;
        if (message != null && message.startsWith("[")) {
            int endIndex = message.indexOf("]");
            if (endIndex > 1) {
                candidate = message.substring(1, endIndex);
            }
        }

        if (candidate != null && isAuditEventType(candidate)) {
            return candidate;
        }

        log.warn("审计事件类型前缀缺失或不可识别（前缀={}），降级为 {}",
                candidate, AuditEventType.SUSPICIOUS_ACTIVITY);
        return AuditEventType.SUSPICIOUS_ACTIVITY.name();
    }

    private static boolean isAuditEventType(final String name) {
        for (AuditEventType type : AuditEventType.values()) {
            if (type.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
