package org.unreal.modelrouter.config.listener;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.unreal.modelrouter.config.core.ConfigSyncService;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;

/**
 * 配置同步事件监听器
 *
 * <p>处理配置同步事件（REFRESH、ROLLBACK、INSTANCE_UPDATE），
 * 异步执行配置同步操作。
 *
 * <p>使用 {@code @Async} 注解实现异步处理，避免阻塞主线程。
 * ConfigSyncService 为可选依赖，当服务不可用时仅记录日志。
 *
 * @since v2.12.0
 */
@Component
@Slf4j
public class ConfigSyncEventListener {

    private final ConfigSyncService configSyncService;

    /**
     * 构造函数注入 ConfigSyncService（可选依赖）
     *
     * @param configSyncService 配置同步服务（可选）
     */
    @Autowired(required = false)
    public ConfigSyncEventListener(ConfigSyncService configSyncService) {
        this.configSyncService = configSyncService;
    }

    /**
     * 处理配置同步事件
     *
     * <p>根据事件类型执行不同的同步操作：
     * <ul>
     *   <li>ROLLBACK: 同步实例数据到数据库</li>
     *   <li>REFRESH: 记录日志，后续可扩展推送配置</li>
     *   <li>INSTANCE_UPDATE: 记录日志，后续可扩展更新实例</li>
     * </ul>
     *
     * @param event 配置同步事件
     */
    @EventListener
    @Async
    public void onConfigSync(ConfigSyncEvent event) {
        log.info("ConfigSync: type={}, target={}, broadcast={}, timestamp={}",
            event.syncType(),
            event.targetService(),
            event.isBroadcast(),
            event.timestamp());

        if (configSyncService == null) {
            log.debug("ConfigSyncService not available, sync skipped");
            return;
        }

        try {
            handleSyncEvent(event);
        } catch (Exception e) {
            log.error("ConfigSync failed: type={}, error={}",
                event.syncType(), e.getMessage(), e);
        }
    }

    /**
     * 根据事件类型处理同步逻辑
     *
     * @param event 配置同步事件
     */
    private void handleSyncEvent(ConfigSyncEvent event) {
        switch (event.syncType()) {
            case "ROLLBACK" -> handleRollback(event);
            case "REFRESH" -> handleRefresh(event);
            case "INSTANCE_UPDATE" -> handleInstanceUpdate(event);
            case "MIGRATE" -> handleMigrate(event);
            default -> log.warn("Unknown sync type: {}", event.syncType());
        }
    }

    /**
     * 处理回滚同步
     *
     * <p>将配置中的实例数据同步回数据库，恢复之前的状态
     *
     * @param event 配置同步事件
     */
    private void handleRollback(ConfigSyncEvent event) {
        log.info("Handling ROLLBACK sync for target: {}",
            event.targetService() != null ? event.targetService() : "all services");

        if (event.config() != null) {
            configSyncService.syncInstancesToDatabase(event.config());
            log.info("ROLLBACK sync completed successfully");
        } else {
            log.warn("ROLLBACK event has no config data, skipping");
        }
    }

    /**
     * 处理刷新同步
     *
     * <p>广播配置刷新通知，后续可扩展推送配置到各服务
     *
     * @param event 配置同步事件
     */
    private void handleRefresh(ConfigSyncEvent event) {
        log.info("Handling REFRESH sync, broadcast={}", event.isBroadcast());

        // TODO: 实现配置推送逻辑
        // 当 isBroadcast() 为 true 时，推送配置到所有服务
        // 当 targetService 不为 null 时，推送到指定服务
        log.debug("REFRESH sync completed (no action required currently)");
    }

    /**
     * 处理实例更新同步
     *
     * <p>更新特定服务实例的配置，后续可扩展实例级别配置同步
     *
     * @param event 配置同步事件
     */
    private void handleInstanceUpdate(ConfigSyncEvent event) {
        log.info("Handling INSTANCE_UPDATE sync for service: {}",
            event.targetService() != null ? event.targetService() : "unknown");

        // TODO: 实现实例级别配置更新逻辑
        log.debug("INSTANCE_UPDATE sync completed (no action required currently)");
    }

    /**
     * 处理迁移同步
     *
     * <p>处理配置迁移事件，后续可扩展跨环境配置同步
     *
     * @param event 配置同步事件
     */
    private void handleMigrate(ConfigSyncEvent event) {
        log.info("Handling MIGRATE sync for target: {}",
            event.targetService() != null ? event.targetService() : "unknown");

        // TODO: 实现配置迁移逻辑
        log.debug("MIGRATE sync completed (no action required currently)");
    }
}