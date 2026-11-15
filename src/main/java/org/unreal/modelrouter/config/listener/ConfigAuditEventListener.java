package org.unreal.modelrouter.config.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.config.event.AuditLogEvent;
import org.unreal.modelrouter.config.event.ConfigurationChangeEvent;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigAuditLogEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigAuditLogRepository;

import java.time.Instant;

/**
 * Configuration audit event listener.
 *
 * Handles configuration change and audit log events, persists audit info to database.
 *
 * @since v2.12.0
 * @since v2.6.12 Added database persistence
 */
@Component
@Slf4j
public class ConfigAuditEventListener {

    /** 控制台写操作审计把链路 ID 放在事件的 data 里，落库时单列存一份便于按 traceId 反查 */
    private static final String TRACE_ID_KEY = "traceId";

    private final ConfigAuditLogRepository auditLogRepository;

    @Autowired(required = false)
    public ConfigAuditEventListener(ConfigAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onConfigurationChange(ConfigurationChangeEvent event) {
        log.info("Audit: {} config for service={}, userId={}, timestamp={}",
            event.changeType(),
            event.serviceType(),
            event.userId(),
            event.timestamp());

        persistAuditLog(event);
    }

    private void persistAuditLog(ConfigurationChangeEvent event) {
        if (auditLogRepository == null) {
            log.warn("ConfigAuditLogRepository not available, skipping persistence");
            return;
        }

        try {
            ConfigAuditLogEntity entity = new ConfigAuditLogEntity();
            entity.setChangeType(event.changeType());
            entity.setServiceType(event.serviceType());
            entity.setUserId(event.userId());
            entity.setTimestamp(event.timestamp() != null ? event.timestamp() : Instant.now());

            if (event.oldConfig() != null) {
                entity.setOldConfig(JacksonHelper.getObjectMapper().writeValueAsString(event.oldConfig()));
            }
            if (event.newConfig() != null) {
                entity.setNewConfig(JacksonHelper.getObjectMapper().writeValueAsString(event.newConfig()));
            }

            auditLogRepository.save(entity);
            log.debug("Audit log persisted: id={}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to persist audit log: {}", e.getMessage(), e);
        }
    }

    @Async
    @org.springframework.context.event.EventListener
    public void onAuditLog(AuditLogEvent event) {
        log.info("AuditLog: operation={}, entityType={}, entityId={}, userId={}, timestamp={}",
            event.operation(),
            event.entityType(),
            event.entityId(),
            event.userId(),
            event.timestamp());

        persistAuditLogEvent(event);
    }

    private void persistAuditLogEvent(AuditLogEvent event) {
        if (auditLogRepository == null) {
            log.warn("ConfigAuditLogRepository not available, skipping persistence");
            return;
        }

        try {
            ConfigAuditLogEntity entity = new ConfigAuditLogEntity();
            entity.setChangeType(event.operation());
            entity.setEntityType(event.entityType());
            entity.setEntityId(event.entityId());
            entity.setUserId(event.userId());
            entity.setTimestamp(event.timestamp() != null ? event.timestamp() : Instant.now());

            if (event.data() != null) {
                entity.setDescription(JacksonHelper.getObjectMapper().writeValueAsString(event.data()));
                // 操作记录与链路关联：traceId 由 ConsoleAuditWebFilter 放进 data，
                // 页面据此把「谁改了什么」和「慢在哪一步」接起来（#1160）
                Object traceId = event.data().get(TRACE_ID_KEY);
                if (traceId != null) {
                    entity.setTraceId(String.valueOf(traceId));
                }
            }

            auditLogRepository.save(entity);
            log.debug("Audit log event persisted: id={}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to persist audit log event: {}", e.getMessage(), e);
        }
    }
}
