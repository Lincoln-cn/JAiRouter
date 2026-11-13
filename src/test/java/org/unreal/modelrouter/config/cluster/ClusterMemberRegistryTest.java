package org.unreal.modelrouter.config.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 集群成员注册与形态判定的行为测试（#1136）。
 */
@DisplayName("集群成员发现与形态判定（#1136）")
class ClusterMemberRegistryTest {

    private ClusterMemberStore memberStore;
    private ClusterProperties properties;
    private ClusterMemberRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        memberStore = Mockito.mock(ClusterMemberStore.class);
        properties = new ClusterProperties();
        ConfigVersionManager versionManager = Mockito.mock(ConfigVersionManager.class);
        when(versionManager.getCurrentVersion()).thenReturn(7);
        ObjectProvider<ModelServiceRegistry> registryProvider = Mockito.mock(ObjectProvider.class);
        registry = new ClusterMemberRegistry(memberStore, properties, versionManager, registryProvider, 18080);
    }

    @Test
    @DisplayName("显式 cluster 形态直接成立")
    void explicitClusterMode() {
        properties.setMode(ClusterProperties.MODE_CLUSTER);

        assertTrue(registry.isClusterMode());
        assertEquals("explicit:cluster", registry.modeSource());
    }

    @Test
    @DisplayName("显式 standalone 形态直接否定")
    void explicitStandaloneMode() {
        properties.setMode(ClusterProperties.MODE_STANDALONE);

        assertFalse(registry.isClusterMode());
        assertEquals("explicit:standalone", registry.modeSource());
    }

    @Test
    @DisplayName("auto 形态下按副本数判定")
    void autoModeUsesReplicaCount() {
        properties.setMode(ClusterProperties.MODE_AUTO);
        properties.setReplicas(3);

        assertTrue(registry.isClusterMode());
        assertEquals("auto:replicas=3", registry.modeSource());
    }

    @Test
    @DisplayName("成员表过滤掉超过 TTL 未续期的记录")
    void expiredMembersAreFiltered() {
        properties.setTtlSeconds(15);
        long now = System.currentTimeMillis();
        ClusterMember fresh = member("a", now - 1_000L);
        ClusterMember stale = member("b", now - 60_000L);
        when(memberStore.list()).thenReturn(List.of(fresh, stale));

        List<ClusterMember> members = registry.members();

        assertEquals(1, members.size());
        assertEquals("a", members.get(0).instanceId());
    }

    private static ClusterMember member(String instanceId, long lastSeen) {
        return new ClusterMember(
                instanceId,
                instanceId,
                "http://" + instanceId + ":18080",
                lastSeen - 5_000L,
                7,
                2,
                2,
                "3.8.5",
                lastSeen);
    }
}
