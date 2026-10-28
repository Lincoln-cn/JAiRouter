package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #706：配置重置的可逆性与失败可见性。
 *
 * <p>本测试在修复前的实现上是红的：旧实现先删版本再删当前配置、没有任何备份，并且把删除异常吞掉。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#706 配置重置：重置前备份、清除失败不吞异常")
class ConfigMergeServiceResetTest {

    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager = Mockito.mock(StoreManager.class);

    private final ConfigMergeService service = new ConfigMergeService(
            storeManager,
            Mockito.mock(ModelRouterProperties.class),
            Mockito.mock(ConfigConverterHelper.class));

    @Test
    @DisplayName("RESET-706-1 先备份生效配置到独立键，再删除全部版本与当前配置")
    void resetBacksUpEffectiveConfigBeforeClearing() {
        final Map<String, Object> current = new HashMap<>();
        current.put("services", Map.of("chat", Map.of("instances", List.of())));
        when(storeManager.exists(CONFIG_KEY)).thenReturn(true);
        when(storeManager.getConfig(CONFIG_KEY)).thenReturn(current);
        when(storeManager.getConfigVersions(CONFIG_KEY)).thenReturn(List.of(1, 2));

        final String backupKey = service.resetToYamlConfig();

        assertEquals(ConfigMergeService.PRE_RESET_BACKUP_KEY, backupKey);
        verify(storeManager).saveConfig(ConfigMergeService.PRE_RESET_BACKUP_KEY, current);
        verify(storeManager).deleteConfigVersion(CONFIG_KEY, 1);
        verify(storeManager).deleteConfigVersion(CONFIG_KEY, 2);
        verify(storeManager).deleteConfig(CONFIG_KEY);
        // 备份必须发生在任何删除之前，否则清除失败时原始配置就丢了
        final InOrder order = inOrder(storeManager);
        order.verify(storeManager).saveConfig(eq(ConfigMergeService.PRE_RESET_BACKUP_KEY), eq(current));
        order.verify(storeManager).deleteConfigVersion(CONFIG_KEY, 1);
    }

    @Test
    @DisplayName("RESET-706-2 删除版本失败时抛出 IllegalStateException，不再吞掉")
    void deleteVersionFailurePropagates() {
        final Map<String, Object> current = Map.of("services", Map.of());
        when(storeManager.exists(CONFIG_KEY)).thenReturn(true);
        when(storeManager.getConfig(CONFIG_KEY)).thenReturn(current);
        when(storeManager.getConfigVersions(CONFIG_KEY)).thenReturn(List.of(1));
        doThrow(new RuntimeException("db down")).when(storeManager).deleteConfigVersion(CONFIG_KEY, 1);

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.resetToYamlConfig());

        assertTrue(error.getMessage().contains("配置可能只被部分清除"));
        assertTrue(error.getCause() instanceof RuntimeException);
        verify(storeManager).saveConfig(ConfigMergeService.PRE_RESET_BACKUP_KEY, current);
        verify(storeManager, never()).deleteConfig(CONFIG_KEY);
    }

    @Test
    @DisplayName("RESET-706-3 删除当前配置失败时同样抛出")
    void deleteCurrentConfigFailurePropagates() {
        final Map<String, Object> current = Map.of("services", Map.of());
        when(storeManager.exists(CONFIG_KEY)).thenReturn(true);
        when(storeManager.getConfig(CONFIG_KEY)).thenReturn(current);
        when(storeManager.getConfigVersions(CONFIG_KEY)).thenReturn(List.of());
        doThrow(new RuntimeException("db down")).when(storeManager).deleteConfig(CONFIG_KEY);

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.resetToYamlConfig());

        assertTrue(error.getMessage().contains("配置可能只被部分清除"));
        verify(storeManager).deleteConfig(CONFIG_KEY);
    }

    @Test
    @DisplayName("RESET-706-4 没有持久化配置时不备份、不删除")
    void nothingToBackupWhenNoPersistedConfig() {
        when(storeManager.exists(CONFIG_KEY)).thenReturn(false);
        when(storeManager.getConfigVersions(CONFIG_KEY)).thenReturn(List.of());

        assertNull(service.resetToYamlConfig());

        verify(storeManager, never()).saveConfig(anyString(), anyMap());
        verify(storeManager, never()).deleteConfig(anyString());
        verify(storeManager, never()).deleteConfigVersion(anyString(), anyInt());
    }

    @Test
    @DisplayName("RESET-706-5 当前配置缺失时备份最新版本")
    void backsUpLatestVersionWhenCurrentConfigMissing() {
        final Map<String, Object> versioned = Map.of("services", Map.of("chat", Map.of()));
        when(storeManager.exists(CONFIG_KEY)).thenReturn(false);
        when(storeManager.getConfigVersions(CONFIG_KEY)).thenReturn(List.of(3, 5));
        when(storeManager.getConfigByVersion(CONFIG_KEY, 5)).thenReturn(versioned);

        assertEquals(ConfigMergeService.PRE_RESET_BACKUP_KEY, service.resetToYamlConfig());

        verify(storeManager).saveConfig(ConfigMergeService.PRE_RESET_BACKUP_KEY, versioned);
        verify(storeManager).deleteConfigVersion(CONFIG_KEY, 3);
        verify(storeManager).deleteConfigVersion(CONFIG_KEY, 5);
    }
}
