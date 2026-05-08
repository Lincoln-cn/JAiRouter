package org.unreal.modelrouter.config.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unreal.modelrouter.config.event.AuditLogEvent;
import org.unreal.modelrouter.config.event.ConfigurationChangeEvent;

/**
 * 配置审计事件监听器
 *
 * 处理配置变更和审计日志事件，记录审计信息到日志文件。
 * 后续可扩展持久化到数据库。
 *
 * @since v2.12.0
 */
@Component
@Slf4j
public class ConfigAuditEventListener {

    /**
     * 处理配置变更事件
     *
     * <p>使用 @TransactionalEventListener 确保事务提交后才处理，
     * 避免事务回滚导致审计日志不一致。
     *
     * @param event 配置变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onConfigurationChange(ConfigurationChangeEvent event) {
        log.info("Audit: {} config for service={}, userId={}, timestamp={}",
            event.changeType(),
            event.serviceType(),
            event.userId(),
            event.timestamp());

        // 记录变更详情（脱敏后）
        if (event.isCreate()) {
            log.info("AuditDetail: Created new config, newConfig keys={}",
                event.newConfig() != null ? event.newConfig().keySet() : "null");
        } else if (event.isUpdate()) {
            log.info("AuditDetail: Updated config, oldConfig keys={}, newConfig keys={}",
                event.oldConfig() != null ? event.oldConfig().keySet() : "null",
                event.newConfig() != null ? event.newConfig().keySet() : "null");
        } else if (event.isDelete()) {
            log.info("AuditDetail: Deleted config, oldConfig keys={}",
                event.oldConfig() != null ? event.oldConfig().keySet() : "null");
        }

        // TODO: 后续持久化到审计表
    }

    /**
     * 处理通用审计日志事件
     *
     * <p>使用普通 @EventListener，适用于非事务场景。
     *
     * @param event 审计日志事件
     */
    @Async
    @org.springframework.context.event.EventListener
    public void onAuditLog(AuditLogEvent event) {
        log.info("AuditLog: operation={}, entityType={}, entityId={}, userId={}, timestamp={}",
            event.operation(),
            event.entityType(),
            event.entityId(),
            event.userId(),
            event.timestamp());

        // 记录操作数据（脱敏后）
        if (event.data() != null && !event.data().isEmpty()) {
            log.info("AuditData: entityType={}, entityId={}, dataKeys={}",
                event.entityType(),
                event.entityId(),
                event.data().keySet());
        }

        // TODO: 后续持久化到审计表
    }
}