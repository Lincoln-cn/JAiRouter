package org.unreal.modelrouter.version.strategy;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.version.VersionContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 版本号生成策略单元测试
 */
class VersionGeneratorTest {

    @Test
    void testSequentialVersionGenerator_Basic() {
        // 测试顺序递增策略
        SequentialVersionGenerator generator = new SequentialVersionGenerator();

        VersionContext context = VersionContext.builder()
                .configKey("test-config")
                .currentVersion(0)
                .existingVersions(Collections.emptyList())
                .totalVersions(0)
                .build();

        int version = generator.generateNextVersion(context);

        assertEquals(1, version, "第一个版本应该是 1");
        assertTrue(generator.isValidVersion(version), "版本号应该有效");
    }

    @Test
    void testSequentialVersionGenerator_Increment() {
        // 测试顺序递增
        SequentialVersionGenerator generator = new SequentialVersionGenerator();

        List<Integer> existing = Arrays.asList(1, 2, 3);
        VersionContext context = VersionContext.builder()
                .configKey("test-config")
                .currentVersion(3)
                .existingVersions(existing)
                .totalVersions(3)
                .build();

        int version = generator.generateNextVersion(context);

        assertEquals(4, version, "版本应该递增到 4");
    }

    @Test
    void testSequentialVersionGenerator_WithGaps() {
        // 测试有缺失版本时仍然递增
        SequentialVersionGenerator generator = new SequentialVersionGenerator();

        List<Integer> existing = Arrays.asList(1, 3, 5);  // 有缺失
        VersionContext context = VersionContext.builder()
                .configKey("test-config")
                .currentVersion(5)
                .existingVersions(existing)
                .totalVersions(3)
                .build();

        int version = generator.generateNextVersion(context);

        assertEquals(6, version, "应该基于最大值递增");
    }

    @Test
    void testTimestampVersionGenerator_Basic() {
        // 测试时间戳策略
        TimestampVersionGenerator generator = new TimestampVersionGenerator();

        VersionContext context = VersionContext.builder()
                .configKey("test-config")
                .currentVersion(0)
                .existingVersions(Collections.emptyList())
                .totalVersions(0)
                .build();

        int version = generator.generateNextVersion(context);

        // 时间戳版本应该是 14 位数字 (YYYYMMDDHHMMSS)
        assertTrue(version > 2000000000, "时间戳版本应该大于 2000000000");
        assertTrue(generator.isValidVersion(version), "版本号应该有效");
    }

    @Test
    void testTimestampVersionGenerator_StrategyName() {
        TimestampVersionGenerator generator = new TimestampVersionGenerator();
        assertEquals("TIMESTAMP", generator.getStrategyName());
    }

    @Test
    void testSequentialVersionGenerator_StrategyName() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();
        assertEquals("SEQUENTIAL", generator.getStrategyName());
    }

    @Test
    void testVersionContext_Builder() {
        // 测试 VersionContext 构建
        List<Integer> versions = Arrays.asList(1, 2, 3);

        VersionContext context = VersionContext.builder()
                .configKey("my-config")
                .currentVersion(3)
                .existingVersions(versions)
                .totalVersions(3)
                .operation("CREATE")
                .operatorId("admin")
                .build();

        assertEquals("my-config", context.getConfigKey());
        assertEquals(3, context.getCurrentVersion());
        assertEquals(3, context.getTotalVersions());
        assertTrue(context.versionExists(1));
        assertTrue(context.versionExists(2));
        assertTrue(context.versionExists(3));
        assertFalse(context.versionExists(4));
        assertEquals(3, context.getMaxVersion());
    }

    @Test
    void testVersionContext_EmptyVersions() {
        VersionContext context = VersionContext.builder()
                .configKey("empty-config")
                .currentVersion(0)
                .existingVersions(Collections.emptyList())
                .totalVersions(0)
                .build();

        assertEquals(0, context.getMaxVersion());
        assertFalse(context.versionExists(1));
    }

    @Test
    void testSequentialVersionGenerator_InvalidVersion() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();

        assertFalse(generator.isValidVersion(0), "0 应该无效");
        assertFalse(generator.isValidVersion(-1), "负数应该无效");
        assertTrue(generator.isValidVersion(1), "正数应该有效");
        assertTrue(generator.isValidVersion(99999999), "大正数应该有效");
    }
}
