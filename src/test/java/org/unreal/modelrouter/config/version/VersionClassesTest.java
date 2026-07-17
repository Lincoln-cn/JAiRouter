package org.unreal.modelrouter.config.version;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.version.strategy.SequentialVersionGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersionClassesTest {

    @Test
    void configMetadata_gettersSetters() {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setConfigKey("test-key");
        metadata.setCurrentVersion(5);
        metadata.setInitialVersion(1);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("admin");
        metadata.setTotalVersions(5);

        assertEquals("test-key", metadata.getConfigKey());
        assertEquals(5, metadata.getCurrentVersion());
        assertEquals(1, metadata.getInitialVersion());
        assertNotNull(metadata.getCreatedAt());
        assertNotNull(metadata.getLastModified());
        assertEquals("admin", metadata.getLastModifiedBy());
        assertEquals(5, metadata.getTotalVersions());
    }

    @Test
    void configMetadata_existingVersions() {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setExistingVersions(new java.util.HashSet<>(List.of(1, 2, 3)));

        assertTrue(metadata.getExistingVersions().contains(2));
        assertFalse(metadata.getExistingVersions().contains(4));
    }

    @Test
    void versionInfo_gettersSetters() {
        VersionInfo info = new VersionInfo();
        info.setVersion(3);
        info.setCreatedAt(LocalDateTime.now());
        info.setCreatedBy("admin");
        info.setDescription("Test version");
        info.setChangeType(VersionInfo.ChangeType.UPDATE);
        info.setConfigSnapshot(Map.of("key", "value"));

        assertEquals(3, info.getVersion());
        assertNotNull(info.getCreatedAt());
        assertEquals("admin", info.getCreatedBy());
        assertEquals("Test version", info.getDescription());
        assertEquals(VersionInfo.ChangeType.UPDATE, info.getChangeType());
        assertEquals("value", info.getConfigSnapshot().get("key"));
    }

    @Test
    void versionContext_builder() {
        List<Integer> versions = new ArrayList<>(List.of(1, 2, 3));
        VersionContext context = VersionContext.builder()
                .configKey("test-key")
                .currentVersion(3)
                .existingVersions(versions)
                .totalVersions(3)
                .operation("UPDATE")
                .operatorId("admin")
                .build();

        assertEquals("test-key", context.getConfigKey());
        assertEquals(3, context.getCurrentVersion());
        assertEquals(3, context.getTotalVersions());
        assertEquals("UPDATE", context.getOperation());
        assertEquals("admin", context.getOperatorId());
    }

    @Test
    void versionContext_versionExists() {
        List<Integer> versions = new ArrayList<>(List.of(1, 2, 3));
        VersionContext context = VersionContext.builder()
                .existingVersions(versions)
                .build();

        assertTrue(context.versionExists(2));
        assertFalse(context.versionExists(4));
    }

    @Test
    void versionContext_versionExists_withNullVersions() {
        VersionContext context = VersionContext.builder()
                .existingVersions(null)
                .build();

        assertFalse(context.versionExists(1));
    }

    @Test
    void versionContext_getMaxVersion() {
        List<Integer> versions = new ArrayList<>(List.of(1, 3, 5));
        VersionContext context = VersionContext.builder()
                .existingVersions(versions)
                .build();

        assertEquals(5, context.getMaxVersion());
    }

    @Test
    void versionContext_getMaxVersion_withEmptyVersions() {
        VersionContext context = VersionContext.builder()
                .existingVersions(new ArrayList<>())
                .build();

        assertEquals(0, context.getMaxVersion());
    }

    @Test
    void sequentialVersionGenerator_basic() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();
        List<Integer> versions = new ArrayList<>(List.of(1, 2, 3));
        VersionContext context = VersionContext.builder()
                .existingVersions(versions)
                .build();

        int nextVersion = generator.generateNextVersion(context);

        assertEquals(4, nextVersion);
    }

    @Test
    void sequentialVersionGenerator_emptyVersions() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();
        VersionContext context = VersionContext.builder()
                .existingVersions(new ArrayList<>())
                .build();

        int nextVersion = generator.generateNextVersion(context);

        assertEquals(1, nextVersion);
    }

    @Test
    void sequentialVersionGenerator_withGaps() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();
        List<Integer> versions = new ArrayList<>(List.of(1, 5));
        VersionContext context = VersionContext.builder()
                .existingVersions(versions)
                .build();

        int nextVersion = generator.generateNextVersion(context);

        assertEquals(6, nextVersion);
    }

    @Test
    void sequentialVersionGenerator_strategyName() {
        SequentialVersionGenerator generator = new SequentialVersionGenerator();
        assertEquals("SEQUENTIAL", generator.getStrategyName());
    }
}
