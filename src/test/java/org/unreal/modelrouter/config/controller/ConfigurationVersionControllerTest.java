package org.unreal.modelrouter.config.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.config.core.ConfigurationService;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.dto.VersionInfoResponse;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.config.version.diff.ConfigDiff;
import org.unreal.modelrouter.config.version.diff.VersionDiffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ConfigurationVersionController 单元测试
 * 
 * <p>测试覆盖版本管理、配置回滚等核心功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ConfigurationVersionController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigurationVersionControllerTest {

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private ConfigVersionManager configVersionManager;

    @Mock
    private StoreManager storeManager;

    @Mock
    private VersionDiffService versionDiffService;

    @InjectMocks
    private ConfigurationVersionController controller;

    // ==================== 获取版本列表测试 ====================

    @Nested
    @DisplayName("GET /api/config/version - 获取版本列表测试")
    class GetConfigVersionsTests {

        @Test
        @DisplayName("VERSION-001: 成功获取版本列表")
        void testGetConfigVersions_success() {
            // Given
            List<Integer> versions = List.of(1, 2, 3);
            when(configVersionManager.getAllVersions()).thenReturn(versions);

            // When
            var result = controller.getConfigVersions();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(3, response.getData().size());
                        assertTrue(response.getData().contains(1));
                        assertTrue(response.getData().contains(2));
                        assertTrue(response.getData().contains(3));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-002: 空版本列表")
        void testGetConfigVersions_empty() {
            // Given
            when(configVersionManager.getAllVersions()).thenReturn(new ArrayList<>());

            // When
            var result = controller.getConfigVersions();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getData().isEmpty());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取版本详情测试 ====================

    @Nested
    @DisplayName("GET /api/config/version/{version} - 获取版本详情测试")
    class GetConfigByVersionTests {

        @Test
        @DisplayName("VERSION-003: 成功获取版本详情")
        void testGetConfigByVersion_success() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("key", "value");
            when(configVersionManager.getVersionConfig(1)).thenReturn(config);

            // When
            var result = controller.getConfigByVersion(1);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                        assertEquals("value", response.getData().get("key"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-004: 版本不存在返回错误")
        void testGetConfigByVersion_notFound() {
            // Given
            when(configVersionManager.getVersionConfig(999)).thenReturn(null);

            // When
            var result = controller.getConfigByVersion(999);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不存在"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 删除版本测试 ====================

    @Nested
    @DisplayName("DELETE /api/config/version/{version} - 删除版本测试")
    class DeleteConfigVersionTests {

        @Test
        @DisplayName("VERSION-005: 成功删除非当前版本")
        void testDeleteConfigVersion_success() {
            // Given
            when(configVersionManager.getCurrentVersion()).thenReturn(3);
            doNothing().when(configVersionManager).deleteConfigVersion(1);

            // When
            var result = controller.deleteConfigVersion(1);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-006: 不能删除当前版本")
        void testDeleteConfigVersion_currentVersion() {
            // Given
            when(configVersionManager.getCurrentVersion()).thenReturn(3);

            // When
            var result = controller.deleteConfigVersion(3);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不能删除当前版本"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取当前版本测试 ====================

    @Nested
    @DisplayName("GET /api/config/version/current - 获取当前版本测试")
    class GetCurrentVersionTests {

        @Test
        @DisplayName("VERSION-007: 成功获取当前版本")
        void testGetCurrentVersion_success() {
            // Given
            when(configVersionManager.getCurrentVersion()).thenReturn(5);

            // When
            var result = controller.getCurrentVersion();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(5, response.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 应用版本测试 ====================

    @Nested
    @DisplayName("POST /api/config/version/apply/{version} - 应用版本测试")
    class ApplyVersionTests {

        @Test
        @DisplayName("VERSION-008: 成功应用版本")
        void testApplyVersion_success() {
            // Given
            doNothing().when(configVersionManager).applyVersion(2);

            // When
            var result = controller.applyVersion(2);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 比较版本测试 ====================

    @Nested
    @DisplayName("GET /api/config/version/compare - 比较版本测试")
    class CompareVersionsTests {

        @Test
        @DisplayName("VERSION-009: 成功比较两个版本")
        void testCompareVersions_success() {
            // Given
            ConfigDiff diff = mock(ConfigDiff.class);
            when(diff.getTotalChanges()).thenReturn(5);
            when(versionDiffService.compareVersions(1, 2)).thenReturn(diff);

            // When
            var result = controller.compareVersions(1, 2);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-010: 相同版本返回错误")
        void testCompareVersions_sameVersion() {
            // When
            var result = controller.compareVersions(2, 2);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不能相同"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-011: 负数版本号返回错误")
        void testCompareVersions_negativeVersion() {
            // When
            var result = controller.compareVersions(-1, 2);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("非负数"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取版本变更测试 ====================

    @Nested
    @DisplayName("GET /api/config/version/compare/{version} - 获取版本变更测试")
    class GetVersionChangesTests {

        @Test
        @DisplayName("VERSION-012: 成功获取版本变更")
        void testGetVersionChanges_success() {
            // Given
            List<Integer> versions = List.of(1, 2, 3);
            when(configVersionManager.getAllVersions()).thenReturn(versions);
            ConfigDiff diff = mock(ConfigDiff.class);
            when(diff.getTotalChanges()).thenReturn(3);
            when(versionDiffService.compareVersions(1, 2)).thenReturn(diff);

            // When
            var result = controller.getVersionChanges(2);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-013: 版本不存在返回错误")
        void testGetVersionChanges_versionNotFound() {
            // Given
            when(configVersionManager.getAllVersions()).thenReturn(List.of(1, 2));

            // When
            var result = controller.getVersionChanges(999);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不存在"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("VERSION-014: 版本号必须为正整数")
        void testGetVersionChanges_invalidVersion() {
            // When
            var result = controller.getVersionChanges(0);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("正整数"));
                    })
                    .verifyComplete();
        }
    }
}
