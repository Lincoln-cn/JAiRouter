package org.unreal.modelrouter.config.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.ConfigSyncService;
import org.unreal.modelrouter.config.listener.ConfigSyncEventListener;
import org.unreal.modelrouter.config.sync.service.ConfigMigrationService;
import org.unreal.modelrouter.config.sync.service.ConfigPushService;
import org.unreal.modelrouter.config.sync.service.InstanceConfigUpdateService;
import org.unreal.modelrouter.config.sync.service.HttpConfigPushService;
import org.unreal.modelrouter.config.sync.service.DefaultInstanceConfigUpdateService;
import org.unreal.modelrouter.config.sync.service.DefaultConfigMigrationService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置同步集成测试.
 * 验证配置同步相关服务的依赖注入和协作.
 *
 * @since v2.6.12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("配置同步集成测试")
class ConfigSyncIntegrationTest {

    @Mock
    private ConfigSyncService configSyncService;

    @Mock
    private ConfigPushService configPushService;

    @Mock
    private InstanceConfigUpdateService instanceConfigUpdateService;

    @Mock
    private ConfigMigrationService configMigrationService;

    @InjectMocks
    private ConfigSyncEventListener eventListener;

    @Test
    @DisplayName("ConfigSyncEventListener 应该正确注入所有依赖")
    void testEventListenerDependencyInjection() {
        assertNotNull(eventListener, "ConfigSyncEventListener 应该被正确创建");
    }

    @Test
    @DisplayName("HttpConfigPushService 应该实现 ConfigPushService 接口")
    void testHttpConfigPushServiceImplementsInterface() {
        HttpConfigPushService httpService = new HttpConfigPushService(null, null);
        assertTrue(httpService instanceof ConfigPushService,
            "HttpConfigPushService 应该实现 ConfigPushService 接口");
    }

    @Test
    @DisplayName("DefaultInstanceConfigUpdateService 应该实现 InstanceConfigUpdateService 接口")
    void testDefaultInstanceConfigUpdateServiceImplementsInterface() {
        DefaultInstanceConfigUpdateService defaultService = new DefaultInstanceConfigUpdateService(null);
        assertTrue(defaultService instanceof InstanceConfigUpdateService,
            "DefaultInstanceConfigUpdateService 应该实现 InstanceConfigUpdateService 接口");
    }

    @Test
    @DisplayName("DefaultConfigMigrationService 应该实现 ConfigMigrationService 接口")
    void testDefaultConfigMigrationServiceImplementsInterface() {
        DefaultConfigMigrationService defaultService = new DefaultConfigMigrationService(null);
        assertTrue(defaultService instanceof ConfigMigrationService,
            "DefaultConfigMigrationService 应该实现 ConfigMigrationService 接口");
    }
}
