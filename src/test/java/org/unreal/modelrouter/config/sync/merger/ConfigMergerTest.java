package org.unreal.modelrouter.config.sync.merger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.core.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.core.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.core.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.core.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMergerTest {

    private ConfigMerger merger;

    @BeforeEach
    void setUp() {
        merger = new ConfigMerger();
    }

    @Test
    void merge_withNullExisting_returnsUpdates() {
        ServiceConfiguration updates = new ServiceConfiguration(
                "normal", List.of(), null, null, null, null
        );

        ServiceConfiguration result = merger.merge(null, updates);

        assertEquals(updates, result);
    }

    @Test
    void merge_withNullUpdates_returnsExisting() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "normal", List.of(), null, null, null, null
        );

        ServiceConfiguration result = merger.merge(existing, null);

        assertEquals(existing, result);
    }

    @Test
    void merge_withBothNull_returnsNull() {
        ServiceConfiguration result = merger.merge(null, null);
        assertNull(result);
    }

    @Test
    void merge_adapter_overridesExisting() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "old-adapter", List.of(), null, null, null, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                "new-adapter", List.of(), null, null, null, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals("new-adapter", result.adapter());
    }

    @Test
    void merge_adapter_preservesExistingWhenNull() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "existing-adapter", List.of(), null, null, null, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                null, List.of(), null, null, null, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals("existing-adapter", result.adapter());
    }

    @Test
    void merge_loadBalance_overridesExisting() {
        LoadBalanceConfiguration existingLB = new LoadBalanceConfiguration("round-robin", null);
        LoadBalanceConfiguration updatesLB = new LoadBalanceConfiguration("least-connections", null);

        ServiceConfiguration existing = new ServiceConfiguration(
                "adapter", List.of(), existingLB, null, null, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                "adapter", List.of(), updatesLB, null, null, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals("least-connections", result.loadBalance().type());
    }

    @Test
    void merge_rateLimit_overridesExisting() {
        RateLimitConfiguration existingRL = new RateLimitConfiguration(100, null, null, null, 10, true);
        RateLimitConfiguration updatesRL = new RateLimitConfiguration(200, null, null, null, 20, true);

        ServiceConfiguration existing = new ServiceConfiguration(
                "adapter", List.of(), null, existingRL, null, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                "adapter", List.of(), null, updatesRL, null, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals(200, result.rateLimit().requestsPerSecond());
    }

    @Test
    void merge_circuitBreaker_overridesExisting() {
        CircuitBreakerConfiguration existingCB = new CircuitBreakerConfiguration(5, 30L, 3, true);
        CircuitBreakerConfiguration updatesCB = new CircuitBreakerConfiguration(10, 60L, 5, true);

        ServiceConfiguration existing = new ServiceConfiguration(
                "adapter", List.of(), null, null, existingCB, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                "adapter", List.of(), null, null, updatesCB, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals(10, result.circuitBreaker().failureThreshold());
    }

    @Test
    void merge_instances_mergesLists() {
        ModelInstanceConfiguration inst1 = new ModelInstanceConfiguration(
                "inst1", "http://localhost:8080", "/v1", "normal",
                1, "active", null, null, null, null, null
        );
        ModelInstanceConfiguration inst2 = new ModelInstanceConfiguration(
                "inst2", "http://localhost:8081", "/v1", "normal",
                1, "active", null, null, null, null, null
        );

        ServiceConfiguration existing = new ServiceConfiguration(
                "adapter", List.of(inst1), null, null, null, null
        );
        ServiceConfiguration updates = new ServiceConfiguration(
                "adapter", List.of(inst2), null, null, null, null
        );

        ServiceConfiguration result = merger.merge(existing, updates);

        assertEquals(2, result.instances().size());
    }

    @Test
    void mergeMaps_withNullExisting_returnsUpdates() {
        Map<String, Object> updates = Map.of("key", "value");
        Map<String, Object> result = merger.mergeMaps(null, updates);
        assertEquals(updates, result);
    }

    @Test
    void mergeMaps_withNullUpdates_returnsExisting() {
        Map<String, Object> existing = Map.of("key", "value");
        Map<String, Object> result = merger.mergeMaps(existing, null);
        assertEquals(existing, result);
    }

    @Test
    void mergeMaps_deepMerge() {
        Map<String, Object> existing = new java.util.HashMap<>();
        existing.put("key1", "value1");
        existing.put("nested", Map.of("a", 1));

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("key2", "value2");
        updates.put("nested", Map.of("b", 2));

        Map<String, Object> result = merger.mergeMaps(existing, updates);

        assertEquals("value1", result.get("key1"));
        assertEquals("value2", result.get("key2"));
    }
}
