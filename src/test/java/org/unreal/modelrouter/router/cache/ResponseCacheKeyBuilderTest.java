package org.unreal.modelrouter.router.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.9.9: ResponseCacheKeyBuilder 单元测试
 *
 * <p>验证：键稳定 / 字段序无关 / 空白归一 / user 入键 / 租户隔离 /
 * cacheSalt 绕过 / embedding+rerank 键。
 */
@DisplayName("ResponseCacheKeyBuilder 测试")
class ResponseCacheKeyBuilderTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String MODEL = "gpt-4";

    @Test
    @DisplayName("相同请求生成相同键")
    void sameRequestProducesSameKey() {
        ChatDTO.Request first = chatRequest("你好");
        ChatDTO.Request second = chatRequest("你好");

        assertEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, first),
                ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, second));
    }

    @Test
    @DisplayName("字段序无关: options 为空与 options 全 null 键相同")
    void nullOptionsAndEmptyOptionsProduceSameKey() {
        ChatDTO.Request noOptions = chatRequest("hello");
        ChatDTO.Request emptyOptions = new ChatDTO.Request(MODEL,
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null,
                ChatDTO.Options.builder().build());

        assertEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, noOptions),
                ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, emptyOptions));
    }

    @Test
    @DisplayName("字符串空白归一: 消息内容首尾空白不影响键")
    void whitespaceNormalized() {
        ChatDTO.Request trimmed = chatRequest("你好");
        ChatDTO.Request padded = chatRequest("  你好  ");

        assertEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, trimmed),
                ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, padded));
    }

    @Test
    @DisplayName("user 入键: 相同请求不同 user 键不同")
    void userIncludedInKey() {
        ChatDTO.Request noUser = chatRequest("hello");
        ChatDTO.Request withUser = new ChatDTO.Request(MODEL,
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, "alice", null);

        assertNotEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, noUser),
                ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, withUser));
    }

    @Test
    @DisplayName("租户隔离: 不同租户相同请求键不同")
    void differentTenantsDifferentKeys() {
        ChatDTO.Request request = chatRequest("hello");

        assertNotEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, request),
                ResponseCacheKeyBuilder.build(TENANT_B, ServiceType.chat, request));
    }

    @Test
    @DisplayName("cacheSalt 非空时整体返回 null 表示绕过缓存")
    void cacheSaltBypassesCache() {
        ChatDTO.Request salted = new ChatDTO.Request(MODEL,
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null,
                ChatDTO.Options.builder().cacheSalt("rotation-1").build());

        assertNull(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, salted));
    }

    @Test
    @DisplayName("模型不同键不同")
    void differentModelsDifferentKeys() {
        ChatDTO.Request gpt4 = chatRequest("hello");
        ChatDTO.Request gpt35 = new ChatDTO.Request("gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 100, 0.0, null, null, null, null, null, null, null);

        assertNotEquals(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, gpt4),
                ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, gpt35));
    }

    @Test
    @DisplayName("embedding 键: 稳定、input 顺序敏感、encodingFormat 入键")
    void embeddingKeyBehavior() {
        EmbeddingDTO.Request first = embeddingRequest("text-embedding-3-small",
                List.of("doc a", "doc b"), "float");
        EmbeddingDTO.Request same = embeddingRequest("text-embedding-3-small",
                List.of("doc a", "doc b"), "float");
        EmbeddingDTO.Request reversed = embeddingRequest("text-embedding-3-small",
                List.of("doc b", "doc a"), "float");
        EmbeddingDTO.Request base64 = embeddingRequest("text-embedding-3-small",
                List.of("doc a", "doc b"), "base64");

        String keyFirst = ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.embedding, first);
        assertEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.embedding, same));
        assertNotEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.embedding, reversed));
        assertNotEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.embedding, base64));
    }

    @Test
    @DisplayName("embedding cacheSalt 非空时返回 null")
    void embeddingCacheSaltBypassesCache() {
        EmbeddingDTO.Request salted = new EmbeddingDTO.Request("text-embedding-3-small",
                "hello", null, null, null,
                EmbeddingDTO.Options.builder().cacheSalt("salt").build());

        assertNull(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.embedding, salted));
    }

    @Test
    @DisplayName("rerank 键: 稳定且 query/documents/topN/returnDocuments 参与")
    void rerankKeyBehavior() {
        RerankDTO.Request first = rerankRequest("rerank-model", "什么是缓存",
                List.of("缓存说明A", "缓存说明B"), 3, false);
        RerankDTO.Request same = rerankRequest("rerank-model", "什么是缓存",
                List.of("缓存说明A", "缓存说明B"), 3, false);
        RerankDTO.Request differentQuery = rerankRequest("rerank-model", "另一个问题",
                List.of("缓存说明A", "缓存说明B"), 3, false);
        RerankDTO.Request differentTopN = rerankRequest("rerank-model", "什么是缓存",
                List.of("缓存说明A", "缓存说明B"), 5, false);
        RerankDTO.Request returnDocs = rerankRequest("rerank-model", "什么是缓存",
                List.of("缓存说明A", "缓存说明B"), 3, true);

        String keyFirst = ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.rerank, first);
        assertEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.rerank, same));
        assertNotEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.rerank, differentQuery));
        assertNotEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.rerank, differentTopN));
        assertNotEquals(keyFirst, ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.rerank, returnDocs));
    }

    @Test
    @DisplayName("P0 之外服务类型与 DTO 形态不匹配返回 null")
    void unsupportedServiceTypeReturnsNull() {
        ChatDTO.Request request = chatRequest("hello");

        assertNull(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.tts, request));
        assertNull(ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, "not-a-dto"));
    }

    @Test
    @DisplayName("键为 64 位十六进制且不含明文内容")
    void keyIsSha256Hex() {
        ChatDTO.Request request = chatRequest("sensitive-content-不缓存明文");
        String key = ResponseCacheKeyBuilder.build(TENANT_A, ServiceType.chat, request);

        assertNotNull(key);
        assertEquals(64, key.length());
        assertTrue(key.matches("[0-9a-f]{64}"));
        assertTrue(!key.contains("sensitive-content"));
    }

    private ChatDTO.Request chatRequest(final String content) {
        return new ChatDTO.Request(MODEL,
                List.of(new ChatDTO.Message("user", content, null)),
                false, 100, 0.0, null, null, null, null, null, null, null);
    }

    private EmbeddingDTO.Request embeddingRequest(final String model, final Object input,
                                                  final String encodingFormat) {
        return new EmbeddingDTO.Request(model, input, encodingFormat, null, null, null);
    }

    private RerankDTO.Request rerankRequest(final String model, final String query,
                                            final List<String> documents, final Integer topN,
                                            final Boolean returnDocuments) {
        return new RerankDTO.Request(model, query, documents, topN, returnDocuments, null);
    }
}
