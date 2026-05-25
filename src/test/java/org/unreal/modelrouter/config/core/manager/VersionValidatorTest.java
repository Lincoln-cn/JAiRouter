package org.unreal.modelrouter.config.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.version.ConfigMetadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VersionValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("VersionValidator 测试")
class VersionValidatorTest {

    private VersionValidator validator;
    private ConfigMetadata metadata;

    @BeforeEach
    void setUp() {
        validator = new VersionValidator();
        metadata = mock(ConfigMetadata.class);
    }

    @Nested
    @DisplayName("版本存在性验证测试")
    class VersionExistsTests {

        @Test
        @DisplayName("VAL-032: 版本存在 - 正常版本")
        void testVersionExistsTrue() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));

            boolean result = validator.versionExists(2, metadata);

            assertTrue(result);
        }

        @Test
        @DisplayName("VAL-033: 版本不存在 - 版本号不在列表中")
        void testVersionExistsFalse() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));

            boolean result = validator.versionExists(5, metadata);

            assertFalse(result);
        }

        @Test
        @DisplayName("VAL-034: 版本不存在 - 无效版本号(<=0)")
        void testVersionExistsInvalidVersion() {
            boolean result = validator.versionExists(0, metadata);

            assertFalse(result);
        }

        @Test
        @DisplayName("VAL-035: 版本不存在 - 负数版本号")
        void testVersionExistsNegativeVersion() {
            boolean result = validator.versionExists(-1, metadata);

            assertFalse(result);
        }

        @Test
        @DisplayName("VAL-036: 版本不存在 - metadata为null")
        void testVersionExistsNullMetadata() {
            boolean result = validator.versionExists(1, null);

            assertFalse(result);
        }

        @Test
        @DisplayName("VAL-037: 版本不存在 - 版本列表为null")
        void testVersionExistsNullVersionList() {
            when(metadata.getExistingVersions()).thenReturn(null);

            boolean result = validator.versionExists(1, metadata);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("版本存在性验证(抛异常)测试")
    class ValidateExistsTests {

        @Test
        @DisplayName("VAL-038: 验证存在 - 版本存在不抛异常")
        void testValidateExistsSuccess() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            assertDoesNotThrow(() -> validator.validateExists(2, metadata, allVersions));
        }

        @Test
        @DisplayName("VAL-039: 验证存在 - 从allVersions找到版本")
        void testValidateExistsFromAllVersions() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Collections.singletonList(1)));
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            // metadata中只有版本1，但allVersions中有版本2
            assertDoesNotThrow(() -> validator.validateExists(2, metadata, allVersions));
        }

        @Test
        @DisplayName("VAL-040: 验证存在 - 版本不存在抛异常")
        void testValidateExistsNotFound() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateExists(5, metadata, allVersions)
            );

            assertTrue(ex.getMessage().contains("版本 5 不存在"));
        }

        @Test
        @DisplayName("VAL-041: 验证存在 - allVersions为null")
        void testValidateExistsNullAllVersions() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));

            assertDoesNotThrow(() -> validator.validateExists(2, metadata, null));
        }
    }

    @Nested
    @DisplayName("删除验证测试")
    class ValidateCanDeleteTests {

        @Test
        @DisplayName("VAL-042: 删除验证 - 可以删除")
        void testValidateCanDeleteSuccess() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
            when(metadata.getCurrentVersion()).thenReturn(3);
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            assertDoesNotThrow(() -> validator.validateCanDelete(1, metadata, allVersions));
        }

        @Test
        @DisplayName("VAL-043: 删除验证 - 不能删除当前版本")
        void testValidateCanDeleteCurrentVersion() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
            when(metadata.getCurrentVersion()).thenReturn(2);
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.validateCanDelete(2, metadata, allVersions)
            );

            assertTrue(ex.getMessage().contains("不能删除当前版本"));
        }

        @Test
        @DisplayName("VAL-044: 删除验证 - 不能删除最后一个版本")
        void testValidateCanDeleteLastVersion() {
            // 只有版本1，但当前版本设为不同值模拟边界情况
            // 注意：由于版本1是当前版本，会先触发"不能删除当前版本"
            // 所以这个测试验证的是当只有一个版本时删除其他版本会触发"不能删除最后一个版本"
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Collections.singletonList(1)));
            when(metadata.getCurrentVersion()).thenReturn(2); // 当前版本不存在于列表中
            List<Integer> allVersions = Collections.singletonList(1);

            // 尝试删除版本1，但只有1个版本
            // 由于版本1存在且不是当前版本(2)，会检查最后一个版本约束
            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.validateCanDelete(1, metadata, allVersions)
            );

            assertTrue(ex.getMessage().contains("不能删除最后一个版本"));
        }

        @Test
        @DisplayName("VAL-045: 删除验证 - 版本不存在")
        void testValidateCanDeleteNotExists() {
            when(metadata.getExistingVersions()).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
            List<Integer> allVersions = Arrays.asList(1, 2, 3);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCanDelete(5, metadata, allVersions)
            );

            assertTrue(ex.getMessage().contains("版本 5 不存在"));
        }
    }

    @Nested
    @DisplayName("配置内容验证测试")
    class ValidateConfigNotEmptyTests {

        @Test
        @DisplayName("VAL-046: 配置验证 - 有效配置")
        void testValidateConfigNotEmptySuccess() {
            java.util.Map<String, Object> config = java.util.Map.of("key", "value");

            assertDoesNotThrow(() -> validator.validateConfigNotEmpty(config, 1));
        }

        @Test
        @DisplayName("VAL-047: 配置验证 - 配置为null")
        void testValidateConfigNotEmptyNull() {
            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.validateConfigNotEmpty(null, 1)
            );

            assertTrue(ex.getMessage().contains("无法读取版本 1 的配置内容"));
        }

        @Test
        @DisplayName("VAL-048: 配置验证 - 配置为空Map")
        void testValidateConfigNotEmptyEmpty() {
            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.validateConfigNotEmpty(Collections.emptyMap(), 1)
            );

            assertTrue(ex.getMessage().contains("无法读取版本 1 的配置内容"));
        }
    }
}
