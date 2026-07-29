package org.unreal.modelrouter.config.listener;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.unreal.modelrouter.config.core.ConfigSyncService;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.config.sync.service.ConfigMigrationService;
import org.unreal.modelrouter.config.sync.service.ConfigPushService;
import org.unreal.modelrouter.config.sync.service.InstanceConfigUpdateService;

/**
 * Config sync event listener.
 *
 * Handles config sync events (REFRESH, ROLLBACK, INSTANCE_UPDATE, MIGRATE).
 *
 * @since v2.12.0
 * @since v2.6.12 Added push, update, and migration services
 */
@Component
@Slf4j
public class ConfigSyncEventListener {

    private final ConfigSyncService configSyncService;
    private final ConfigPushService configPushService;
    private final InstanceConfigUpdateService instanceConfigUpdateService;
    private final ConfigMigrationService configMigrationService;

    @Autowired(required = false)
    public ConfigSyncEventListener(
            ConfigSyncService configSyncService,
            ConfigPushService configPushService,
            InstanceConfigUpdateService instanceConfigUpdateService,
            ConfigMigrationService configMigrationService) {
        this.configSyncService = configSyncService;
        this.configPushService = configPushService;
        this.instanceConfigUpdateService = instanceConfigUpdateService;
        this.configMigrationService = configMigrationService;
    }

    @EventListener
    @Async
    public void onConfigSync(ConfigSyncEvent event) {
        log.info("ConfigSync: type={}, target={}, broadcast={}, timestamp={}",
            event.syncType(),
            event.targetService(),
            event.isBroadcast(),
            event.timestamp());

        try {
            handleSyncEvent(event);
        } catch (Exception e) {
            log.error("ConfigSync failed: type={}, error={}", event.syncType(), e.getMessage(), e);
        }
    }

    private void handleSyncEvent(ConfigSyncEvent event) {
        switch (event.syncType()) {
            case "ROLLBACK" -> handleRollback(event);
            case "REFRESH" -> handleRefresh(event);
            case "INSTANCE_UPDATE" -> handleInstanceUpdate(event);
            case "MIGRATE" -> handleMigrate(event);
            default -> log.warn("Unknown sync type: {}", event.syncType());
        }
    }

    private void handleRollback(ConfigSyncEvent event) {
        log.info("Handling ROLLBACK sync for target: {}",
            event.targetService() != null ? event.targetService() : "all services");

        if (configSyncService == null) {
            log.warn("ConfigSyncService not available, skipping ROLLBACK");
            return;
        }

        if (event.config() != null) {
            configSyncService.syncInstancesToDatabase(event.config());
            log.info("ROLLBACK sync completed successfully");
        } else {
            log.warn("ROLLBACK event has no config data, skipping");
        }
    }

    private void handleRefresh(ConfigSyncEvent event) {
        log.info("Handling REFRESH sync, broadcast={}, target={}",
            event.isBroadcast(), event.targetService());

        if (configPushService == null) {
            log.warn("ConfigPushService not available, skipping REFRESH");
            return;
        }

        if (event.config() == null) {
            log.warn("REFRESH event has no config data, skipping");
            return;
        }

        if (event.isBroadcast()) {
            configPushService.broadcastConfig(event.config())
                .subscribe(result -> {
                    if (result.success()) {
                        log.info("Broadcast completed: {} services updated", result.successCount());
                    } else {
                        log.warn("Broadcast partially failed: {} failures", result.failureCount());
                    }
                });
        } else if (event.targetService() != null) {
            configPushService.pushToService(event.targetService(), event.config())
                .subscribe(result -> {
                    if (result.success()) {
                        log.info("Push to {} completed successfully", event.targetService());
                    } else {
                        log.warn("Push to {} failed: {}", event.targetService(), result.message());
                    }
                });
        } else {
            log.warn("REFRESH event has no target service and not broadcast, skipping");
        }
    }

    private void handleInstanceUpdate(ConfigSyncEvent event) {
        log.info("Handling INSTANCE_UPDATE sync for service: {}",
            event.targetService() != null ? event.targetService() : "unknown");

        if (instanceConfigUpdateService == null) {
            log.warn("InstanceConfigUpdateService not available, skipping INSTANCE_UPDATE");
            return;
        }

        Map<String, Object> config = event.config();
        if (config == null) {
            log.warn("INSTANCE_UPDATE event has no config data, skipping");
            return;
        }

        String tempInstanceId = (String) config.get("instanceId");
        final String instanceId = (tempInstanceId != null) ? tempInstanceId : event.targetService();

        if (instanceId != null) {
            // Create mutable copy of config to safely remove instanceId
            Map<String, Object> mutableConfig = new java.util.HashMap<>(config);
            mutableConfig.remove("instanceId");
            instanceConfigUpdateService.updateInstanceConfig(instanceId, mutableConfig)
                .subscribe(result -> {
                    if (result.success()) {
                        log.info("Instance config updated: instanceId={}", instanceId);
                    } else {
                        log.warn("Instance config update failed: instanceId={}, error={}",
                            instanceId, result.message());
                    }
                });
        } else {
            log.warn("INSTANCE_UPDATE event has no instance ID, skipping");
        }
    }

    private void handleMigrate(ConfigSyncEvent event) {
        log.info("Handling MIGRATE sync for target: {}",
            event.targetService() != null ? event.targetService() : "unknown");

        if (configMigrationService == null) {
            log.warn("ConfigMigrationService not available, skipping MIGRATE");
            return;
        }

        Map<String, Object> config = event.config();
        if (config == null) {
            log.warn("MIGRATE event has no config data, skipping");
            return;
        }

        String sourceEnv = (String) config.get("sourceEnv");
        String targetEnv = (String) config.get("targetEnv");

        if (sourceEnv == null || targetEnv == null) {
            log.warn("MIGRATE event missing sourceEnv or targetEnv, skipping");
            return;
        }

        Object servicesObj = config.get("serviceTypes");
        final String[] serviceTypes;
        if (servicesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> services = (List<String>) servicesObj;
            serviceTypes = services.toArray(new String[0]);
        } else {
            serviceTypes = null;
        }

        configMigrationService.exportConfig(sourceEnv, targetEnv, serviceTypes)
            .subscribe(result -> {
                if (result.success()) {
                    log.info("Migration completed: {} configs migrated from {} to {}",
                        result.migratedCount(), sourceEnv, targetEnv);
                } else {
                    log.warn("Migration failed: {}", result.message());
                }
            });
    }
}
