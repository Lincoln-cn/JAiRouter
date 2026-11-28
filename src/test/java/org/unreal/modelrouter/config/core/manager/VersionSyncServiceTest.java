package org.unreal.modelrouter.config.core.manager;

import org.hibernate.NonUniqueResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.config.version.ConfigMetadata;
import org.unreal.modelrouter.config.version.VersionInfo;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VersionSyncService} 的降级语义（#1249）.
 *
 * <p>缺陷史：{@code config_data} 缺 {@code (config_key, version)} 唯一约束，并发保存后
 * 同一版本出现两行；启动时 {@code VersionSyncService} 为每个版本读创建时间，
 * 用的是预期返回单行的 {@code findByConfigKeyAndVersion} ⇒ 抛
 * {@code NonUniqueResultException}，被 {@code syncFromDatabase} 升级成
 * {@code RuntimeException("初始化版本控制失败")}，一路冒到
 * {@code ConfigurationService.postConstructInit} ⇒ 进程以退出码 1 结束，重启不自愈。</p>
 *
 * <p>本类断言「单条脏数据不再等于实例起不来」：一个版本读不出时间时，
 * 同步仍然完成、版本历史仍然包含该版本（时间留空），且不抛异常。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VersionSyncService 单版本失败降级")
class VersionSyncServiceTest {

    private static final String KEY = "current_config";

    @Mock
    private StoreManager storeManager;

    @Mock
    private VersionMetadataManager metadataManager;

    private VersionSyncService service;

    @BeforeEach
    void setUp() {
        service = new VersionSyncService(storeManager, metadataManager);
        when(metadataManager.getOrCreateMetadata(anyString())).thenReturn(new ConfigMetadata());
        when(storeManager.getConfigVersions(KEY)).thenReturn(List.of(1, 2));
        when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(null);
    }

    @Test
    @DisplayName("某个版本的行不唯一时，同步仍然完成且不抛异常")
    void singleBrokenVersionDoesNotAbortStartup() {
        when(storeManager.getVersionCreatedTime(KEY, 1)).thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(storeManager.getVersionCreatedTime(KEY, 2))
                .thenThrow(new NonUniqueResultException(2));

        assertDoesNotThrow(() -> service.syncFromDatabase(KEY));

        List<VersionInfo> history = capturedHistory();
        assertEquals(2, history.size(), "坏掉的版本仍应留在历史里，只是时间留空");
        assertNotNull(history.get(0).getCreatedAt());
        assertNull(history.get(1).getCreatedAt(), "读不出的版本时间应为空而不是让启动失败");
    }

    @Test
    @DisplayName("版本内容读失败时只丢描述，不影响同步")
    void brokenConfigBodyDoesNotAbortStartup() {
        when(storeManager.getVersionCreatedTime(anyString(), anyInt())).thenReturn(LocalDateTime.now());
        when(storeManager.getConfigByVersion(anyString(), anyInt()))
                .thenThrow(new NonUniqueResultException(2));

        assertDoesNotThrow(() -> service.syncFromDatabase(KEY));
        assertEquals(2, capturedHistory().size());
    }

    @Test
    @DisplayName("全部版本都正常时行为不变")
    void happyPathUnchanged() {
        when(storeManager.getVersionCreatedTime(anyString(), anyInt())).thenReturn(LocalDateTime.now());

        service.syncFromDatabase(KEY);

        List<VersionInfo> history = capturedHistory();
        assertEquals(2, history.size());
        assertEquals("配置版本 1", history.get(0).getDescription());
        assertEquals(VersionInfo.ChangeType.INITIAL, history.get(0).getChangeType());
        assertEquals(VersionInfo.ChangeType.UPDATE, history.get(1).getChangeType());
    }

    @SuppressWarnings("unchecked")
    private List<VersionInfo> capturedHistory() {
        org.mockito.ArgumentCaptor<List<VersionInfo>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(metadataManager).saveVersionHistory(org.mockito.ArgumentMatchers.eq(KEY), captor.capture());
        return captor.getValue();
    }
}
