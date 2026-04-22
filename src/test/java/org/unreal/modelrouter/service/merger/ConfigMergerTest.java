package org.unreal.modelrouter.service.merger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigMerger 单元测试
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
class ConfigMergerTest {

    private ConfigMerger merger;

    @BeforeEach
    void setUp() {
        merger = new ConfigMerger();
    }

    @Test
    void testMerge_UpdatesNull() {
        // Given
        ServiceConfiguration existing = createTestConfig("existing");
        ServiceConfiguration updates = null;

        // When
        ServiceConfiguration result = merger.merge(existing, updates);

        // Then
        assertEquals(existing, result);
    }

    @Test
    void testMerge_ExistingNull() {
        // Given
        ServiceConfiguration existing = null;
        ServiceConfiguration updates = createTestConfig("updates");

        // When
        ServiceConfiguration result = merger.merge(existing, updates);

        // Then
        assertEquals(updates, result);
    }

    @Test
    void testMerge_BothNull() {
        // Given
        ServiceConfiguration existing = null;
        ServiceConfiguration updates = null;

        // When
        ServiceConfiguration result = merger.merge(existing, updates);

        // Then
        assertNull(result);
    }

    @Test
    void testMerge_PartialUpdates() {
        // Given
        ServiceConfiguration existing = new ServiceConfiguration(
                "existing-adapter",
                List.of(),
                new LoadBalanceConfiguration("round_robin", "murmur3"),
                new RateLimitConfiguration(100, 6000, 360000, 8640000, 50, true),
                new CircuitBreakerConfiguration(5, 60000L, 2, true),
                new FallbackConfiguration(true, "http://fallback.com", 3, 1000L, true)
        );

        ServiceConfiguration updates = new ServiceConfiguration(
                "updated-adapter",
                null, // 不更新 instances
                null, // 不更新 loadBalance
                null, // 不更新 rateLimit
                null, // 不更新 circuitBreaker
                null  // 不更新 fallback
        );

        // When
        ServiceConfiguration result = merger.merge(existing, updates);

        // Then
        assertEquals("updated-adapter", result.adapter());
        assertEquals(existing.loadBalance(), result.loadBalance());
        assertEquals(existing.rateLimit(), result.rateLimit());
        assertEquals(existing.circuitBreaker(), result.circuitBreaker());
        assertEquals(existing.fallback(), result.fallback());
    }

    @Test
    void testMerge_FullUpdates() {
        // Given
        ServiceConfiguration existing = createTestConfig("existing");
        ServiceConfiguration updates = createTestConfig("updated");

        // When
        ServiceConfiguration result = merger.merge(existing, updates);

        // Then
        assertEquals("updated-adapter", result.adapter());
    }

    @Test
    void testMergeInstances_UpdatesNull() {
        // Given
        List<ModelInstanceConfiguration> existing = List.of(
                createInstance("instance1", "http://localhost:8080")
        );
        List<ModelInstanceConfiguration> updates = null;

        // When
        List<ModelInstanceConfiguration> result = merger.mergeInstances(existing, updates);

        // Then
        assertEquals(1, result.size());
        assertEquals("instance1", result.get(0).name());
    }

    @Test
    void testMergeInstances_ExistingNull() {
        // Given
        List<ModelInstanceConfiguration> existing = null;
        List<ModelInstanceConfiguration> updates = List.of(
                createInstance("instance2", "http://localhost:8081")
        );

        // When
        List<ModelInstanceConfiguration> result = merger.mergeInstances(existing, updates);

        // Then
        assertEquals(1, result.size());
        assertEquals("instance2", result.get(0).name());
    }

    @Test
    void testMergeInstances_MergeSameBaseUrl() {
        // Given
        List<ModelInstanceConfiguration> existing = List.of(
                createInstance("instance1", "http://localhost:8080")
        );
        List<ModelInstanceConfiguration> updates = List.of(
                createInstance("updated-instance1", "http://localhost:8080")
        );

        // When
        List<ModelInstanceConfiguration> result = merger.mergeInstances(existing, updates);

        // Then
        assertEquals(1, result.size());
        assertEquals("updated-instance1", result.get(0).name());
    }

    @Test
    void testMergeInstances_AddNewInstance() {
        // Given
        List<ModelInstanceConfiguration> existing = List.of(
                createInstance("instance1", "http://localhost:8080")
        );
        List<ModelInstanceConfiguration> updates = List.of(
                createInstance("instance2", "http://localhost:8081")
        );

        // When
        List<ModelInstanceConfiguration> result = merger.mergeInstances(existing, updates);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void testMergeLoadBalance_UpdatesNull() {
        // Given
        LoadBalanceConfiguration existing = new LoadBalanceConfiguration("round_robin", "murmur3");
        LoadBalanceConfiguration updates = null;

        // When
        LoadBalanceConfiguration result = mergeLoadBalance(existing, updates);

        // Then
        assertEquals("round_robin", result.type());
    }

    @Test
    void testMergeLoadBalance_PartialUpdate() {
        // Given
        LoadBalanceConfiguration existing = new LoadBalanceConfiguration("round_robin", "murmur3");
        LoadBalanceConfiguration updates = new LoadBalanceConfiguration("weighted", null);

        // When
        LoadBalanceConfiguration result = mergeLoadBalance(existing, updates);

        // Then
        assertEquals("weighted", result.type());
        assertEquals("murmur3", result.hashAlgorithm());
    }

    @Test
    void testMergeRateLimit_PartialUpdate() {
        // Given
        RateLimitConfiguration existing = new RateLimitConfiguration(
                100, 6000, 360000, 8640000, 50, true
        );
        RateLimitConfiguration updates = new RateLimitConfiguration(
                200, null, null, null, null, null
        );

        // When
        RateLimitConfiguration result = mergeRateLimit(existing, updates);

        // Then
        assertEquals(200, result.requestsPerSecond());
        assertEquals(6000, result.requestsPerMinute());
        assertEquals(true, result.enabled());
    }

    @Test
    void testMergeCircuitBreaker_PartialUpdate() {
        // Given
        CircuitBreakerConfiguration existing = new CircuitBreakerConfiguration(
                5, 60000L, 2, true
        );
        CircuitBreakerConfiguration updates = new CircuitBreakerConfiguration(
                10, null, null, null
        );

        // When
        CircuitBreakerConfiguration result = mergeCircuitBreaker(existing, updates);

        // Then
        assertEquals(10, result.failureThreshold());
        assertEquals(60000L, result.timeout());
        assertEquals(true, result.enabled());
    }

    @Test
    void testMergeFallback_PartialUpdate() {
        // Given
        FallbackConfiguration existing = new FallbackConfiguration(
                true, "http://fallback.com", 3, 1000L, true
        );
        FallbackConfiguration updates = new FallbackConfiguration(
                null, "http://new-fallback.com", null, null, null
        );

        // When
        FallbackConfiguration result = mergeFallback(existing, updates);

        // Then
        assertEquals("http://new-fallback.com", result.fallbackUrl());
        assertEquals(3, result.maxRetries());
    }

    @Test
    void testMergeMaps_BothNull() {
        // Given
        Map<String, Object> base = null;
        Map<String, Object> updates = null;

        // When
        Map<String, Object> result = merger.mergeMaps(base, updates);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMergeMaps_UpdatesNull() {
        // Given
        Map<String, Object> base = Map.of("key1", "value1");
        Map<String, Object> updates = null;

        // When
        Map<String, Object> result = merger.mergeMaps(base, updates);

        // Then
        assertEquals(1, result.size());
        assertEquals("value1", result.get("key1"));
    }

    @Test
    void testMergeMaps_SimpleMerge() {
        // Given
        Map<String, Object> base = new HashMap<>();
        base.put("key1", "value1");
        base.put("key2", "value2");

        Map<String, Object> updates = new HashMap<>();
        updates.put("key2", "updated-value2");
        updates.put("key3", "value3");

        // When
        Map<String, Object> result = merger.mergeMaps(base, updates);

        // Then
        assertEquals(3, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals("updated-value2", result.get("key2"));
        assertEquals("value3", result.get("key3"));
    }

    @Test
    void testMergeMaps_NestedMerge() {
        // Given
        Map<String, Object> base = new HashMap<>();
        Map<String, Object> nestedBase = new HashMap<>();
        nestedBase.put("nestedKey1", "nestedValue1");
        base.put("nested", nestedBase);

        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> nestedUpdates = new HashMap<>();
        nestedUpdates.put("nestedKey2", "nestedValue2");
        updates.put("nested", nestedUpdates);

        // When
        Map<String, Object> result = merger.mergeMaps(base, updates);

        // Then
        assertEquals(1, result.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> resultNested = (Map<String, Object>) result.get("nested");
        assertEquals(2, resultNested.size());
        assertEquals("nestedValue1", resultNested.get("nestedKey1"));
        assertEquals("nestedValue2", resultNested.get("nestedKey2"));
    }

    // 辅助方法
    private ServiceConfiguration createTestConfig(String adapterPrefix) {
        return new ServiceConfiguration(
                adapterPrefix + "-adapter",
                List.of(),
                LoadBalanceConfiguration.defaultConfig(),
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig()
        );
    }

    private ModelInstanceConfiguration createInstance(String name, String baseUrl) {
        return new ModelInstanceConfiguration(
                name,
                baseUrl,
                "/v1/chat",
                "normal",
                1,
                "active",
                null, null, null, null, null
        );
    }

    // 调用私有方法的测试辅助方法
    private LoadBalanceConfiguration mergeLoadBalance(LoadBalanceConfiguration existing, LoadBalanceConfiguration updates) {
        ServiceConfiguration existingConfig = new ServiceConfiguration(
                "test", List.of(), existing, null, null, null
        );
        ServiceConfiguration updatesConfig = new ServiceConfiguration(
                "test", List.of(), updates, null, null, null
        );
        ServiceConfiguration result = merger.merge(existingConfig, updatesConfig);
        return result.loadBalance();
    }

    private RateLimitConfiguration mergeRateLimit(RateLimitConfiguration existing, RateLimitConfiguration updates) {
        ServiceConfiguration existingConfig = new ServiceConfiguration(
                "test", List.of(), null, existing, null, null
        );
        ServiceConfiguration updatesConfig = new ServiceConfiguration(
                "test", List.of(), null, updates, null, null
        );
        ServiceConfiguration result = merger.merge(existingConfig, updatesConfig);
        return result.rateLimit();
    }

    private CircuitBreakerConfiguration mergeCircuitBreaker(CircuitBreakerConfiguration existing, CircuitBreakerConfiguration updates) {
        ServiceConfiguration existingConfig = new ServiceConfiguration(
                "test", List.of(), null, null, existing, null
        );
        ServiceConfiguration updatesConfig = new ServiceConfiguration(
                "test", List.of(), null, null, updates, null
        );
        ServiceConfiguration result = merger.merge(existingConfig, updatesConfig);
        return result.circuitBreaker();
    }

    private FallbackConfiguration mergeFallback(FallbackConfiguration existing, FallbackConfiguration updates) {
        ServiceConfiguration existingConfig = new ServiceConfiguration(
                "test", List.of(), null, null, null, existing
        );
        ServiceConfiguration updatesConfig = new ServiceConfiguration(
                "test", List.of(), null, null, null, updates
        );
        ServiceConfiguration result = merger.merge(existingConfig, updatesConfig);
        return result.fallback();
    }
}
