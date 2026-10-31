package org.unreal.modelrouter.config.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.unreal.modelrouter.persistence.store.StoreManager;

/**
 * #876：版本号必须来自持久化历史（重启不重号），回滚必须真的追加历史版本。
 */
@DisplayName("#876 配置版本号与回滚")
class VersionControlServiceTest {

    private static final String KEY = VersionControlService.CURRENT_KEY;

    @Test
    @DisplayName("876-001 无历史时从 1 开始，并把该版本写入历史")
    void firstVersionStartsAtOne() {
        final StoreManager storeManager = Mockito.mock(StoreManager.class);
        when(storeManager.getConfigVersions(KEY)).thenReturn(List.of());
        final Map<String, Object> config = Map.of("k", "v");

        final Integer version = new VersionControlService(storeManager).createNewVersion(config, "init", "admin");

        assertEquals(1, version);
        verify(storeManager).saveConfigVersion(KEY, config, 1);
    }

    @Test
    @DisplayName("876-002 历史已有 1..3 时下一个是 4（进程重启不再从 1 重来）")
    void nextVersionComesFromPersistedHistory() {
        final StoreManager storeManager = Mockito.mock(StoreManager.class);
        when(storeManager.getConfigVersions(KEY)).thenReturn(List.of(1, 2, 3));
        final Map<String, Object> config = Map.of("k", "v");

        final VersionControlService service = new VersionControlService(storeManager);

        assertEquals(4, service.createNewVersion(config, "update", "admin"));
        assertEquals(3, service.getCurrentVersion());
        verify(storeManager).saveConfigVersion(KEY, config, 4);
    }

    @Test
    @DisplayName("876-003 回滚把历史快照追加为新版本，而不是返回当前配置")
    void rollbackAppendsSnapshotAsNewVersion() {
        final StoreManager storeManager = Mockito.mock(StoreManager.class);
        final Map<String, Object> oldSnapshot = Map.of("k", "old");
        when(storeManager.getConfigVersions(KEY)).thenReturn(List.of(1, 2, 3));
        when(storeManager.getConfigByVersion(KEY, 2)).thenReturn(oldSnapshot);

        final Map<String, Object> restored = new VersionControlService(storeManager).rollbackToVersion(2);

        assertEquals(oldSnapshot, restored);
        verify(storeManager).saveConfigVersion(KEY, oldSnapshot, 4);
    }

    @Test
    @DisplayName("876-004 回滚到不存在的版本要报错，且不写任何历史")
    void rollbackToUnknownVersionFails() {
        final StoreManager storeManager = Mockito.mock(StoreManager.class);
        when(storeManager.getConfigVersions(KEY)).thenReturn(List.of(1));
        when(storeManager.getConfigByVersion(KEY, 9)).thenReturn(null);

        final VersionControlService service = new VersionControlService(storeManager);

        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.rollbackToVersion(9));
        assertTrue(error.getMessage().contains("9"), "错误信息应点名版本号，实际: " + error.getMessage());
        verify(storeManager, never()).saveConfigVersion(eq(KEY), any(), anyInt());
    }
}
