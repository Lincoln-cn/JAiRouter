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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private ChatDTO.Request chatRequest(final String content, final Double temperature,
                                        final Integer n) {
        ChatDTO.Options options = n != null
                ? ChatDTO.Options.builder().n(n).build() : null;
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 100, temperature, null, null, null, null, null, null, options);
    }
}
