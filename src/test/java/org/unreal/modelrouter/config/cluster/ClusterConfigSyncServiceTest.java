package org.unreal.modelrouter.config.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.cluster.ClusterEventBus;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置同步服务的行为测试（#1132）。
 *
 * <p>关注三个边界：首次建立基线不触发重建、版本前进才重建、读版本失败时不得误判。</p>
 */
@DisplayName("跨副本配置同步（#1132）")
class ClusterConfigSyncServiceTest {

    private StoreManager storeManager;
    private ModelServiceRegistry modelServiceRegistry;
    private ApiKeyService apiKeyService;
    private ClusterConfigSyncProperties properties;
    private ClusterConfigSyncService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        storeManager = Mockito.mock(StoreManager.class);
        modelServiceRegistry = Mockito.mock(ModelServiceRegistry.class);
        apiKeyService = Mockito.mock(ApiKeyService.class);
        properties = new ClusterConfigSyncProperties();

        ObjectProvider<ModelServiceRegistry> registryProvider = Mockito.mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(modelServiceRegistry);
        ObjectProvider<ApiKeyService> apiKeyProvider = Mockito.mock(ObjectProvider.class);
        when(apiKeyProvider.getIfAvailable()).thenReturn(apiKeyService);

        service = new ClusterConfigSyncService(
                storeManager,
                registryProvider,
                apiKeyProvider,
                Mockito.mock(ClusterEventBus.class),
                properties);
        when(storeManager.getConfigVersions(ClusterConfigSyncService.CONFIG_STORE_KEY)).thenReturn(List.of(3));
        when(storeManager.getConfigVersions(ClusterConfigSyncService.API_KEY_STORE_KEY)).thenReturn(List.of(1));
    }

    @Test
    @DisplayName("首次同步只建立基线，不重建注册表")
    void firstSyncOnlyEstablishesBaseline() {
        service.forceSyncNow();

        verify(modelServiceRegistry, never()).refreshFromMergedConfig();
        verify(apiKeyService, never()).refreshCacheFromSharedStore();
        assertEquals(3, service.getAppliedConfigVersion());
        assertEquals(1, service.getAppliedApiKeyVersion());
    }

    @Test
    @DisplayName("版本前进时触发重建并更新已应用版本")
    void newerVersionTriggersRefresh() {
        service.forceSyncNow();
        when(storeManager.getConfigVersions(ClusterConfigSyncService.CONFIG_STORE_KEY)).thenReturn(List.of(4));
        when(storeManager.getConfigVersions(ClusterConfigSyncService.API_KEY_STORE_KEY)).thenReturn(List.of(2));

        service.forceSyncNow();

        verify(modelServiceRegistry).refreshFromMergedConfig();
        verify(apiKeyService).refreshCacheFromSharedStore();
        assertEquals(4, service.getAppliedConfigVersion());
        assertEquals(2, service.getAppliedApiKeyVersion());
    }

    @Test
    @DisplayName("版本未变时不重复重建")
    void unchangedVersionDoesNotRefresh() {
        service.forceSyncNow();
        service.forceSyncNow();

        verify(modelServiceRegistry, never()).refreshFromMergedConfig();
    }

    @Test
    @DisplayName("读取版本失败时跳过该周期，不得把已应用版本写成 -1")
    void readFailureKeepsAppliedVersion() {
        service.forceSyncNow();
        when(storeManager.getConfigVersions(anyString())).thenThrow(new IllegalStateException("db down"));

        service.forceSyncNow();

        verify(modelServiceRegistry, never()).refreshFromMergedConfig();
        assertEquals(3, service.getAppliedConfigVersion());
        assertEquals(1, service.getAppliedApiKeyVersion());
    }

    @Test
    @DisplayName("关闭开关后定时 tick 不做任何同步")
    void disabledSyncDoesNothing() {
        service.forceSyncNow();
        assertEquals(3, service.getAppliedConfigVersion());

        service.resetBaseline();
        properties.setEnabled(false);
        service.tick();

        assertEquals(-1, service.getAppliedConfigVersion());
        assertEquals(-1, service.getAppliedApiKeyVersion());
    }
}
