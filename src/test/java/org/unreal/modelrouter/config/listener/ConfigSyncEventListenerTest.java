package org.unreal.modelrouter.config.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.ConfigSyncService;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.config.sync.service.ConfigMigrationService;
import org.unreal.modelrouter.config.sync.service.ConfigPushService;
import org.unreal.modelrouter.config.sync.service.ConfigPushService.BroadcastResult;
import org.unreal.modelrouter.config.sync.service.ConfigPushService.PushResult;
import org.unreal.modelrouter.config.sync.service.InstanceConfigUpdateService;
import org.unreal.modelrouter.config.sync.service.InstanceConfigUpdateService.UpdateResult;
import org.unreal.modelrouter.config.sync.service.ConfigMigrationService.MigrationResult;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ConfigSyncEventListener 单元测试.
 *
 * @since v2.6.12
 */
@DisplayName("ConfigSyncEventListener 测试")
@ExtendWith(MockitoExtension.class)
class ConfigSyncEventListenerTest {

    @Mock
    private ConfigSyncService configSyncService;

    @Mock
    private ConfigPushService configPushService;

    @Mock
    private InstanceConfigUpdateService instanceConfigUpdateService;

    @Mock
    private ConfigMigrationService configMigrationService;

    @InjectMocks
    private ConfigSyncEventListener listener;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("处理 ROLLBACK 事件")
    void testHandleRollbackEvent() {
        var event = ConfigSyncEvent.rollback(Map.of("key", "value"), "test-service");

        listener.onConfigSync(event);

        verify(configSyncService).syncInstancesToDatabase(any());
    }

    @Test
    @DisplayName("处理 REFRESH 广播事件")
    void testHandleRefreshBroadcastEvent() {
        when(configPushService.broadcastConfig(any()))
            .thenReturn(reactor.core.publisher.Mono.just(BroadcastResult.success(1)));

        var event = ConfigSyncEvent.refresh(Map.of("key", "value"));

        listener.onConfigSync(event);

        verify(configPushService).broadcastConfig(any());
    }

    @Test
    @DisplayName("处理 REFRESH 目标服务事件")
    void testHandleRefreshTargetEvent() {
        when(configPushService.pushToService(anyString(), any()))
            .thenReturn(reactor.core.publisher.Mono.just(PushResult.success("chat")));

        var event = new ConfigSyncEvent("REFRESH", Map.of("key", "value"), "chat", Instant.now());

        listener.onConfigSync(event);

        verify(configPushService).pushToService(eq("chat"), any());
    }

    @Test
    @DisplayName("处理 INSTANCE_UPDATE 事件")
    void testHandleInstanceUpdateEvent() throws InterruptedException {
        when(instanceConfigUpdateService.updateInstanceConfig(anyString(), any()))
            .thenReturn(reactor.core.publisher.Mono.just(UpdateResult.success("test-instance")));

        var event = ConfigSyncEvent.instanceUpdate(
            Map.of("instanceId", "test-instance", "key", "value"), "test-instance");

        listener.onConfigSync(event);

        // Wait for async execution
        Thread.sleep(200);
        verify(instanceConfigUpdateService, timeout(1000)).updateInstanceConfig(eq("test-instance"), any());
    }

    @Test
    @DisplayName("处理 MIGRATE 事件")
    void testHandleMigrateEvent() {
        when(configMigrationService.exportConfig(anyString(), anyString(), any()))
            .thenReturn(reactor.core.publisher.Mono.just(
                MigrationResult.success("dev", "prod", 1)));

        var event = new ConfigSyncEvent("MIGRATE",
            Map.of("sourceEnv", "dev", "targetEnv", "prod"), null, Instant.now());

        listener.onConfigSync(event);

        verify(configMigrationService).exportConfig(eq("dev"), eq("prod"), any());
    }

    @Test
    @DisplayName("处理未知同步类型")
    void testHandleUnknownSyncType() {
        var event = new ConfigSyncEvent("UNKNOWN", Map.of(), null, Instant.now());

        listener.onConfigSync(event);

        verifyNoInteractions(configSyncService);
        verifyNoInteractions(configPushService);
        verifyNoInteractions(instanceConfigUpdateService);
        verifyNoInteractions(configMigrationService);
    }
}
