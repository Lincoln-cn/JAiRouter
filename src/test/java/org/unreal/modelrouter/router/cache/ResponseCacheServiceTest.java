package org.unreal.modelrouter.router.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v2.9.9: ResponseCacheService 单元测试
 *
 * <p>验证：enabled=false 短路 / lookup 命中记 hit / 未命中记 miss / store /
 * TTL 传递 / 确定性门控。
 */
@DisplayName("ResponseCacheService 测试")
@ExtendWith(MockitoExtension.class)
class ResponseCacheServiceTest {

    @Mock
    private CacheStore cacheStore;

    @Mock
    private MetricsCollector metricsCollector;

    private ResponseCacheProperties properties;
    private ResponseCacheService service;

    @BeforeEach
    void setUp() {
        properties = new ResponseCacheProperties();
        properties.setEnabled(true);
        properties.setTtl(Duration.ofMinutes(5));
        service = new ResponseCacheService(cacheStore, properties, metricsCollector);
    }

    @Test
    @DisplayName("enabled=false 时全部短路")
    void disabledShortCircuits() {
        properties.setEnabled(false);
        ChatDTO.Request request = chatRequest("hello", 0.0, null);

        assertFalse(service.isEnabled());
        assertNull(service.buildKey("tenant-a", ServiceType.chat, request));
        assertFalse(service.lookup("some-key", "chat", "gpt-4").isPresent());
        service.store("some-key", "data");

        verifyNoInteractions(cacheStore, metricsCollector);
    }

    @Test
    @DisplayName("buildKey: 仅确定性请求生成键")
    void buildKeyGatesDeterministicRequests() {
        ChatDTO.Request deterministic = chatRequest("hello", 0.0, null);
        ChatDTO.Request randomTemp = chatRequest("hello", 0.9, null);
        ChatDTO.Request multipleChoices = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null,
                ChatDTO.Options.builder().n(3).build());
        ChatDTO.Request streaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 100, 0.0, null, null, null, null, null, null, null);

        assertNotNull(service.buildKey("tenant-a", ServiceType.chat, deterministic));
        assertNull(service.buildKey("tenant-a", ServiceType.chat, randomTemp));
        assertNull(service.buildKey("tenant-a", ServiceType.chat, multipleChoices));
        assertNull(service.buildKey("tenant-a", ServiceType.chat, streaming));
    }

    @Test
    @DisplayName("buildKey: embedding/rerank 天然可缓存，其他服务类型不可缓存")
    void buildKeySupportsP0ServiceTypesOnly() {
        EmbeddingDTO.Request embedding = new EmbeddingDTO.Request("text-embedding-3-small",
                "hello", null, null, null, null);
        RerankDTO.Request rerank = new RerankDTO.Request("rerank-model", "query",
                List.of("doc"), 3, false, null);

        assertNotNull(service.buildKey("tenant-a", ServiceType.embedding, embedding));
        assertNotNull(service.buildKey("tenant-a", ServiceType.rerank, rerank));
        assertNull(service.buildKey("tenant-a", ServiceType.tts, "not-supported"));
    }

    @Test
    @DisplayName("lookup 命中时记录 hit 指标并返回缓存值")
    void lookupHitRecordsHit() {
        when(cacheStore.get("key-1")).thenReturn(Optional.of("cached-data"));

        Optional<Object> result = service.lookup("key-1", "chat", "gpt-4");

        assertTrue(result.isPresent());
        assertEquals("cached-data", result.get());
        verify(metricsCollector).recordResponseCacheHit("chat", "gpt-4");
    }

    @Test
    @DisplayName("lookup 未命中时记录 miss 指标并返回 empty")
    void lookupMissRecordsMiss() {
        when(cacheStore.get("key-1")).thenReturn(Optional.empty());

        Optional<Object> result = service.lookup("key-1", "embedding", "text-embedding");

        assertFalse(result.isPresent());
        verify(metricsCollector).recordResponseCacheMiss("embedding", "text-embedding");
    }

    @Test
    @DisplayName("store 将配置 TTL 传递给 CacheStore")
    void storePassesConfiguredTtl() {
        service.store("key-1", "data");

        verify(cacheStore).put(eq("key-1"), eq("data"), eq(properties.getTtl()));
    }

    @Test
    @DisplayName("store 数据为 null 或键为空时 no-op")
    void storeSkipsNullDataAndBlankKey() {
        service.store("key-1", null);
        service.store(null, "data");
        service.store("  ", "data");

        verifyNoInteractions(cacheStore);
    }

    // ==================== v2.9.10: skipStreaming 门控测试 ====================

    @Test
    @DisplayName("skipStreaming=true(默认) 时流式请求不生成键")
    void skipStreamingTrueBlocksStreamingRequests() {
        properties.setSkipStreaming(true);
        ChatDTO.Request streaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 100, 0.0, null, null, null, null, null, null, null);

        assertNull(service.buildKey("tenant-a", ServiceType.chat, streaming),
                "skipStreaming=true 时流式请求不应生成缓存键");
    }

    @Test
    @DisplayName("skipStreaming=false 时确定性流式请求生成键")
    void skipStreamingFalseAllowsDeterministicStreamingRequests() {
        properties.setSkipStreaming(false);
        ChatDTO.Request streaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 100, 0.0, null, null, null, null, null, null, null);

        assertNotNull(service.buildKey("tenant-a", ServiceType.chat, streaming),
                "skipStreaming=false 时确定性流式请求应生成缓存键");
    }

    @Test
    @DisplayName("skipStreaming=false + temperature>0 非确定性流式请求不生成键")
    void skipStreamingFalseNonDeterministicStreamingStillBlocked() {
        properties.setSkipStreaming(false);
        properties.setOnlyDeterministic(true);
        ChatDTO.Request streaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 100, 0.9, null, null, null, null, null, null, null);

        assertNull(service.buildKey("tenant-a", ServiceType.chat, streaming),
                "非确定性流式请求即使 skipStreaming=false 也不应生成缓存键");
    }

    @Test
    @DisplayName("流式与非流式同请求键不同（天然分桶）")
    void streamingAndNonStreamingKeysDiffer() {
        properties.setSkipStreaming(false);
        ChatDTO.Request nonStreaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null, null);
        ChatDTO.Request streaming = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 100, 0.0, null, null, null, null, null, null, null);

        String nonStreamKey = service.buildKey("tenant-a", ServiceType.chat, nonStreaming);
        String streamKey = service.buildKey("tenant-a", ServiceType.chat, streaming);

        assertNotNull(nonStreamKey);
        assertNotNull(streamKey);
        assertNotEquals(nonStreamKey, streamKey,
                "流式与非流式请求键应不同（天然分桶）");
    }

    // ==================== v2.9.10: invalidation 测试 ====================

    @Test
    @DisplayName("invalidate: enabled=false 时不执行，返回 false")
    void invalidateWhenDisabledReturnsFalse() {
        properties.setEnabled(false);

        assertFalse(service.invalidate(ServiceType.chat, "gpt-4"));
        assertFalse(service.invalidateAll());

        verifyNoInteractions(cacheStore);
    }

    @Test
    @DisplayName("invalidate: serviceType=null 清空全部")
    void invalidateWithNullServiceTypeClearsAll() {
        assertTrue(service.invalidate(null, null));

        verify(cacheStore).clear();
    }

    @Test
    @DisplayName("invalidate: serviceType 非空，按前缀删除")
    void invalidateByServiceTypePrefix() {
        assertTrue(service.invalidate(ServiceType.chat, null));

        verify(cacheStore).deleteByPrefix("rc:chat:");
    }

    @Test
    @DisplayName("invalidate: serviceType + model 按精确前缀删除")
    void invalidateByServiceTypeAndModel() {
        assertTrue(service.invalidate(ServiceType.chat, "gpt-4"));

        verify(cacheStore).deleteByPrefix("rc:chat:gpt-4:");
    }

    @Test
    @DisplayName("invalidateAll: enabled=false 时不执行，返回 false")
    void invalidateAllWhenDisabledReturnsFalse() {
        properties.setEnabled(false);

        assertFalse(service.invalidateAll());

        verifyNoInteractions(cacheStore);
    }

    @Test
    @DisplayName("invalidateAll: 清空全部缓存")
    void invalidateAllClearsCache() {
        assertTrue(service.invalidateAll());

        verify(cacheStore).clear();
    }

    @Test
    @DisplayName("buildKey 返回三段式键格式")
    void buildKeyReturnsThreeSegmentFormat() {
        ChatDTO.Request request = new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null, null);

        String key = service.buildKey("tenant-a", ServiceType.chat, request);

        assertNotNull(key);
        assertTrue(key.startsWith("rc:chat:gpt-4:"),
                "键应以三段式前缀开头，实际=" + key);
    }

    // ==================== 运行时计数器测试 ====================

    @Test
    @DisplayName("snapshot 初始状态：hits=0, misses=0, hitRatio=null")
    void snapshotInitialState() {
        Map<String, Object> snap = service.snapshot();

        assertEquals(0L, snap.get("hits"));
        assertEquals(0L, snap.get("misses"));
        assertNull(snap.get("hitRatio"), "无请求数据时 hitRatio 应为 null");
    }

    @Test
    @DisplayName("lookup 命中后 snapshot hits 递增")
    void lookupHitIncrementsHitsCounter() {
        when(cacheStore.get("key-1")).thenReturn(Optional.of("data"));
        when(cacheStore.get("key-2")).thenReturn(Optional.of("data2"));

        service.lookup("key-1", "chat", "gpt-4");
        service.lookup("key-2", "chat", "gpt-4");

        Map<String, Object> snap = service.snapshot();
        assertEquals(2L, snap.get("hits"));
        assertEquals(0L, snap.get("misses"));
        assertEquals(1.0, snap.get("hitRatio"));
    }

    @Test
    @DisplayName("lookup 未命中后 snapshot misses 递增")
    void lookupMissIncrementsMissesCounter() {
        when(cacheStore.get("key-1")).thenReturn(Optional.empty());

        service.lookup("key-1", "chat", "gpt-4");

        Map<String, Object> snap = service.snapshot();
        assertEquals(0L, snap.get("hits"));
        assertEquals(1L, snap.get("misses"));
        assertEquals(0.0, snap.get("hitRatio"));
    }

    @Test
    @DisplayName("snapshot hitRatio = hits / (hits + misses)")
    void snapshotHitRatioCalculatedCorrectly() {
        when(cacheStore.get("hit")).thenReturn(Optional.of("data"));
        when(cacheStore.get("miss")).thenReturn(Optional.empty());

        // 3 hits + 1 miss = 0.75
        service.lookup("hit", "chat", "gpt-4");
        service.lookup("hit", "chat", "gpt-4");
        service.lookup("hit", "chat", "gpt-4");
        service.lookup("miss", "chat", "gpt-4");

        Map<String, Object> snap = service.snapshot();
        assertEquals(3L, snap.get("hits"));
        assertEquals(1L, snap.get("misses"));
        assertEquals(0.75, (Double) snap.get("hitRatio"), 1e-9);
    }

    @Test
    @DisplayName("snapshot 包含所有配置字段")
    void snapshotContainsAllConfigFields() {
        Map<String, Object> snap = service.snapshot();

        assertEquals(true, snap.get("enabled"));
        assertEquals(300L, snap.get("ttlSeconds"));  // 5 min
        assertEquals(10000, snap.get("maxSize"));
        assertNotNull(snap.get("size"));
        assertEquals(true, snap.get("skipStreaming"));
        assertEquals(true, snap.get("onlyDeterministic"));
    }

    // ==================== updateRuntimeConfig 测试 ====================

    @Test
    @DisplayName("updateRuntimeConfig 部分更新 enabled")
    void updateRuntimeConfigPartialUpdateEnabled() {
        assertTrue(properties.isEnabled());

        Map<String, Object> snap = service.updateRuntimeConfig(false, null, null, null);

        assertFalse(properties.isEnabled());
        assertEquals(false, snap.get("enabled"));
    }

    @Test
    @DisplayName("updateRuntimeConfig 部分更新 ttlSeconds")
    void updateRuntimeConfigPartialUpdateTtl() {
        assertEquals(Duration.ofMinutes(5), properties.getTtl());

        Map<String, Object> snap = service.updateRuntimeConfig(null, null, null, 120L);

        assertEquals(Duration.ofSeconds(120), properties.getTtl());
        assertEquals(120L, snap.get("ttlSeconds"));
    }

    @Test
    @DisplayName("updateRuntimeConfig 全 null → IllegalArgumentException")
    void updateRuntimeConfigAllNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRuntimeConfig(null, null, null, null));
    }

    @Test
    @DisplayName("updateRuntimeConfig ttlSeconds 超下界 → IllegalArgumentException")
    void updateRuntimeConfigTtlTooLowThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRuntimeConfig(null, null, null, 0L));
    }

    @Test
    @DisplayName("updateRuntimeConfig ttlSeconds 超上界 → IllegalArgumentException")
    void updateRuntimeConfigTtlTooHighThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRuntimeConfig(null, null, null, 604_801L));
    }

    @Test
    @DisplayName("updateRuntimeConfig ttlSeconds 边界值 1 和 604800 合法")
    void updateRuntimeConfigTtlBoundaryValues() {
        service.updateRuntimeConfig(null, null, null, 1L);
        assertEquals(Duration.ofSeconds(1), properties.getTtl());

        service.updateRuntimeConfig(null, null, null, 604_800L);
        assertEquals(Duration.ofSeconds(604_800), properties.getTtl());
    }

    @Test
    @DisplayName("updateRuntimeConfig 多字段同时更新")
    void updateRuntimeConfigMultipleFields() {
        Map<String, Object> snap = service.updateRuntimeConfig(false, false, false, 60L);

        assertFalse(properties.isEnabled());
        assertFalse(properties.isSkipStreaming());
        assertFalse(properties.isOnlyDeterministic());
        assertEquals(Duration.ofSeconds(60), properties.getTtl());
        assertEquals(false, snap.get("enabled"));
        assertEquals(false, snap.get("skipStreaming"));
        assertEquals(false, snap.get("onlyDeterministic"));
        assertEquals(60L, snap.get("ttlSeconds"));
    }

    private ChatDTO.Request chatRequest(final String content, final Double temperature,
                                        final Integer n) {
        ChatDTO.Options options = n != null
                ? ChatDTO.Options.builder().n(n).build() : null;
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 100, temperature, null, null, null, null, null, null, options);
    }
}
