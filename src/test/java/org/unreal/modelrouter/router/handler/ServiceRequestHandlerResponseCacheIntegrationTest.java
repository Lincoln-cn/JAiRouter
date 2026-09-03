package org.unreal.modelrouter.router.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.CacheStore;
import org.unreal.modelrouter.router.cache.CaffeineCacheStore;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v2.9.9: ServiceRequestHandler 响应缓存读短路集成测试.
 *
 * <p>仿 {@code ModelServiceRegistryTagRoutingIntegrationTest} 的 Mockito LENIENT +
 * 反射注入真实组件风格：handler 的 executor/registry/adapter 用 mock（既有
 * {@code ServiceRequestHandlerAdapterRuleTest} 同款），缓存侧用真实实现
 * （CaffeineCacheStore + ResponseCacheService + 计数代理 CacheStore），并走完整
 * {@code handleRequest(exchange)} 认证流程（ReactiveSecurityContextHolder +
 * MockServerWebExchange + DTO attribute → prepareResponseCacheKey → 读短路），
 * 覆盖：
 * <ul>
 *   <li>命中：返回缓存 RouterResponse 200、不触 executor/adapter、selectInstance 不执行（T3 语义：命中短路在 selectInstance 前）</li>
 *   <li>未命中：走原流程（executor 被调）</li>
 *   <li>enabled=false：永不查缓存</li>
 *   <li>流式 / 非确定性（temperature&gt;0 / n&gt;1）：不生成键不查缓存</li>
 *   <li>不同 apiKeyId（租户）：键不同、不共享缓存</li>
 *   <li>同租户相同请求：键稳定，写后二次请求命中</li>
 *   <li>embedding / rerank：读短路同样生效</li>
 * </ul>
 *
 * <p>与正常响应结构一致性：命中响应的 RouterResponse 由 handler 以
 * {@code RouterResponse.success(data, "请求成功")} 构建，与非流式正常路径
 * （NonStreamingRequestProcessor 同款包装）结构一致——测试断言 success/message/data。
 */
@DisplayName("ServiceRequestHandler 响应缓存读短路集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerResponseCacheIntegrationTest {

    /** 传递给下游 executor 的 Authorization（handler 层透传，与缓存键无关） */
    private static final String BEARER = "Bearer downstream-token";

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceCapability adapter;

    private ResponseCacheProperties properties;
    private CountingCacheStore cacheStore;
    private ResponseCacheService cacheService;
    private ServiceRequestHandler handler;

    @BeforeEach
    void setUp() {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);
        when(adapterRegistry.getAdapter(any(ModelServiceRegistry.ServiceType.class), any()))
                .thenReturn(adapter);
    }

    // ==================== 用例 1: 缓存启用 + 命中 ====================

    @Test
    @DisplayName("RC-INTEG-001: 命中返回缓存响应(200 RouterResponse), 不触 executor/adapter, selectInstance 不执行(T3 短路)")
    void chatHitReturnsCachedResponseWithoutExecutorOrAdapter() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-cached");
        String key = seed("key-1", ServiceType.chat, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, result.response().getHeaders().getContentType());
        assertCachedBody(result.response(), payload);

        // 命中短路: executor 与适配器获取都不应发生
        verify(executor, never()).execute(any(), any(), any());
        verify(adapterRegistry, never()).getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        // T3 语义: 缓存读在 selectInstance 之前, 命中直接返回——跳过实例选择
        // (不论 RateLimitManager 是否注入, 命中短路均在 selectInstance 前;
        //  限流由 handler 预扣时恰一次, 见 ServiceRequestHandlerRateLimitCacheIntegrationTest)
        verify(registry, never()).selectInstance(any(), anyString(), anyString(), any());
        // 恰一次缓存读
        assertEquals(1, cacheStore.getCount());
        assertEquals(key, result.cacheKey());
    }

    @Test
    @DisplayName("RC-INTEG-002: 命中响应结构与正常路径 RouterResponse.success(data,'请求成功') 一致")
    void hitResponseMatchesNormalSuccessStructure() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-normal");
        seed("key-1", ServiceType.chat, dto, payload);

        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, downstreamExecutor());

        assertTrue(result.response().getBody() instanceof RouterResponse);
        RouterResponse<?> body = (RouterResponse<?>) result.response().getBody();
        RouterResponse<Map<String, Object>> expected = RouterResponse.success(payload, "请求成功");
        assertEquals(expected.isSuccess(), body.isSuccess());
        assertEquals(expected.getMessage(), body.getMessage());
        assertEquals(expected.getData(), body.getData());
        assertNull(body.getErrorCode());
    }

    // ==================== 用例 2: 缓存启用 + 未命中 ====================

    @Test
    @DisplayName("RC-INTEG-003: 未命中走原流程(executor 被调), 查一次缓存")
    void chatMissFallsThroughToExecutor() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        verify(adapterRegistry, times(1)).getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        assertEquals(1, cacheStore.getCount(), "未命中也应查一次缓存(记 miss)");
        assertNotNull(result.cacheKey());
    }

    // ==================== 用例 3: 缓存禁用 ====================

    @Test
    @DisplayName("RC-INTEG-004: enabled=false 永不查缓存, 走原流程")
    void disabledNeverQueriesCache() throws Exception {
        buildHandler(false);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertEquals(0, cacheStore.getCount(), "禁用时不应访问缓存存储");
        assertNull(result.cacheKey(), "禁用时不应生成缓存键");
    }

    // ==================== 用例 4: 流式 / 非确定性不生成键不查缓存 ====================

    @Test
    @DisplayName("RC-INTEG-005: 流式请求不生成键不查缓存")
    void streamingRequestSkipsCache() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = chatRequest("hello", true, 0.0, null);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertEquals(0, cacheStore.getCount(), "流式请求不应访问缓存");
        assertNull(result.cacheKey());
    }

    @Test
    @DisplayName("RC-INTEG-006: temperature>0 非确定性请求不生成键不查缓存")
    void nonDeterministicTemperatureSkipsCache() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = chatRequest("hello", false, 0.9, null);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertEquals(0, cacheStore.getCount(), "非确定性请求不应访问缓存");
        assertNull(result.cacheKey());
    }

    @Test
    @DisplayName("RC-INTEG-007: n>1 多候选请求不生成键不查缓存")
    void multipleChoicesSkipsCache() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = chatRequest("hello", false, 0.0,
                ChatDTO.Options.builder().n(3).build());

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertEquals(0, cacheStore.getCount(), "n>1 请求不应访问缓存");
        assertNull(result.cacheKey());
    }

    // ==================== 用例 5: 租户隔离 ====================

    @Test
    @DisplayName("RC-INTEG-008: 不同 apiKeyId 同请求 → 键不同, 且不共享缓存")
    void differentApiKeyIdProducesDifferentKeyAndDoesNotShareCache() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");

        // key-1 首次请求(未命中)后, 将响应写入 key-1 的缓存
        RunResult first = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, downstreamExecutor());
        assertNotNull(first.cacheKey(), "确定性请求应生成缓存键");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-tenant-a");
        cacheService.store(first.cacheKey(), payload);

        // 相同请求但不同租户 key-2 → 不命中 key-1 的缓存, 走原流程
        ServiceRequestExecutor executor2 = downstreamExecutor();
        RunResult second = run(ServiceEndpoint.CHAT, "gpt-4", "key-2",
                List.of("chat"), dto, executor2);

        assertNotEquals(first.cacheKey(), second.cacheKey(), "不同租户键必须不同");
        assertEquals("downstream-ok", second.response().getBody(),
                "key-2 不应命中 key-1 的缓存");
        verify(executor2, times(1)).execute(any(), any(), any());
        // 键由租户前缀参与: 与真实 ResponseCacheService 计算一致
        assertEquals(cacheService.buildKey("key-1", ServiceType.chat, dto), first.cacheKey());
        assertEquals(cacheService.buildKey("key-2", ServiceType.chat, dto), second.cacheKey());
    }

    @Test
    @DisplayName("RC-INTEG-009: 同租户相同请求 → 键稳定, 写后二次请求命中(完整写读闭环)")
    void sameTenantStableKeyAndSecondRunHits() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");

        // 第一次: 未命中, 记录键(相当于 processor 写缓存前的同一键)
        RunResult first = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, downstreamExecutor());
        assertNotNull(first.cacheKey());
        Map<String, Object> payload = chatResponsePayload("chatcmpl-write-read");
        // 模拟写挂载: 经真实 ResponseCacheService.store 落入 Caffeine
        cacheService.store(first.cacheKey(), payload);

        // 第二次完全相同请求 → 命中
        ServiceRequestExecutor executor2 = downstreamExecutor();
        RunResult second = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor2);

        assertEquals(first.cacheKey(), second.cacheKey(), "同租户同请求键必须稳定");
        assertCachedBody(second.response(), payload);
        verify(executor2, never()).execute(any(), any(), any());
        assertTrue(cacheService.lookup(second.cacheKey(), "chat", "gpt-4").isPresent());
    }

    // ==================== 用例 6: DTO 缺失(非 P0 服务形态) ====================

    @Test
    @DisplayName("RC-INTEG-010: 未放入 DTO(非 P0 端点形态) 不生成键不查缓存")
    void missingDtoSkipsCache() throws Exception {
        buildHandler(true);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), null, executor);

        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertEquals(0, cacheStore.getCount());
        assertNull(result.cacheKey());
    }

    // ==================== 用例 7: embedding / rerank 读短路 ====================

    @Test
    @DisplayName("RC-INTEG-011: embedding 命中读短路返回缓存响应")
    void embeddingHitShortCircuits() throws Exception {
        buildHandler(true);
        EmbeddingDTO.Request dto = new EmbeddingDTO.Request("text-embedding-3-small",
                "hello", null, null, null, null);
        Map<String, Object> payload = embeddingResponsePayload();
        seed("key-1", ServiceType.embedding, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.EMBEDDING, "text-embedding-3-small", "key-1",
                List.of("embedding"), dto, executor);

        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertCachedBody(result.response(), payload);
        verify(executor, never()).execute(any(), any(), any());
        assertEquals(1, cacheStore.getCount());
    }

    @Test
    @DisplayName("RC-INTEG-012: rerank 命中读短路返回缓存响应")
    void rerankHitShortCircuits() throws Exception {
        buildHandler(true);
        RerankDTO.Request dto = new RerankDTO.Request("rerank-model", "query",
                List.of("doc1", "doc2"), 3, false, null);
        Map<String, Object> payload = rerankResponsePayload();
        seed("key-1", ServiceType.rerank, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.RERANK, "rerank-model", "key-1",
                List.of("rerank"), dto, executor);

        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertCachedBody(result.response(), payload);
        verify(executor, never()).execute(any(), any(), any());
        assertEquals(1, cacheStore.getCount());
    }

    @Test
    @DisplayName("RC-INTEG-013: embedding 未命中走原流程")
    void embeddingMissFallsThrough() throws Exception {
        buildHandler(true);
        EmbeddingDTO.Request dto = new EmbeddingDTO.Request("text-embedding-3-small",
                "hello", null, null, null, null);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.EMBEDDING, "text-embedding-3-small", "key-1",
                List.of("embedding"), dto, executor);

        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertNotNull(result.cacheKey(), "embedding 确定性请求应生成键");
    }

    // ==================== 用例 8: v2.9.10 流式缓存读短路 ====================

    @Test
    @DisplayName("RC-INTEG-020: 流式缓存命中返回 text/event-stream + Flux 逐块回放")
    void streamingCacheHitReturnsSseResponse() throws Exception {
        buildHandlerWithStreamingCache(true);
        ChatDTO.Request dto = deterministicStreamingChat("hello");

        // 构造流式缓存值并预置
        List<String> chunks = List.of(
                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                        + "\"created\":1700000000,\"model\":\"gpt-4\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"},"
                        + "\"finish_reason\":null}]}",
                "{\"id\":\"chatcmpl-2\",\"object\":\"chat.completion.chunk\","
                        + "\"created\":1700000001,\"model\":\"gpt-4\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\" there\"},"
                        + "\"finish_reason\":null}]}",
                "{\"id\":\"chatcmpl-3\",\"object\":\"chat.completion.chunk\","
                        + "\"created\":1700000002,\"model\":\"gpt-4\","
                        + "\"choices\":[{\"index\":0,\"delta\":{},"
                        + "\"finish_reason\":\"stop\"}]}",
                "[DONE]");
        CachedStreamingResponse cachedStreaming = new CachedStreamingResponse(
                chunks, "gpt-4", 10L, 5L, 15L, "stop");
        String key = seedStreaming("key-1", ServiceType.chat, dto, cachedStreaming);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertEquals(MediaType.TEXT_EVENT_STREAM, result.response().getHeaders().getContentType());
        assertTrue(result.response().getBody() instanceof Flux,
                "流式缓存命中应返回 Flux body");

        // 不触 executor/adapter
        verify(executor, never()).execute(any(), any(), any());
        verify(adapterRegistry, never()).getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        assertEquals(1, cacheStore.getCount());
        assertEquals(key, result.cacheKey());
    }

    @Test
    @DisplayName("RC-INTEG-021: 非流式缓存命中仍返回 RouterResponse JSON（回归验证）")
    void nonStreamingCacheHitStillReturnsJson() throws Exception {
        buildHandler(true);
        ChatDTO.Request dto = deterministicChat("hello");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-json-regression");
        seed("key-1", ServiceType.chat, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, result.response().getHeaders().getContentType());
        assertCachedBody(result.response(), payload);
        verify(executor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("RC-INTEG-022: 流式与非流式同请求键分桶不互污染")
    void streamingAndNonStreamingKeysAreSeparated() throws Exception {
        buildHandlerWithStreamingCache(true);
        ChatDTO.Request nonStreaming = deterministicChat("hello");
        ChatDTO.Request streaming = deterministicStreamingChat("hello");

        String nonStreamKey = cacheService.buildKey("key-1", ServiceType.chat, nonStreaming);
        String streamKey = cacheService.buildKey("key-1", ServiceType.chat, streaming);

        assertNotNull(nonStreamKey);
        assertNotNull(streamKey);
        assertNotEquals(nonStreamKey, streamKey, "流式与非流式键应不同");

        // 写入非流式缓存
        Map<String, Object> jsonPayload = chatResponsePayload("chatcmpl-non-stream");
        cacheStore.put(nonStreamKey, jsonPayload, Duration.ofMinutes(5));

        // 流式请求不应命中非流式缓存
        ServiceRequestExecutor executor1 = downstreamExecutor();
        RunResult streamingResult = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), streaming, executor1);
        assertEquals("downstream-ok", streamingResult.response().getBody(),
                "流式请求不应命中非流式缓存");
        verify(executor1, times(1)).execute(any(), any(), any());

        // 写入流式缓存
        CachedStreamingResponse streamingCached = new CachedStreamingResponse(
                List.of("{\"id\":\"1\"}", "[DONE]"), "gpt-4", null, null, null, "stop");
        cacheStore.put(streamKey, streamingCached, Duration.ofMinutes(5));

        // 非流式请求不应命中流式缓存
        ServiceRequestExecutor executor2 = downstreamExecutor();
        RunResult nonStreamingResult = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), nonStreaming, executor2);
        // 非流式缓存仍然存在，所以应该命中非流式缓存
        assertTrue(nonStreamingResult.response().getBody() instanceof RouterResponse,
                "非流式请求应命中非流式缓存并返回 RouterResponse");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 handler：真实 ResponseCacheProperties + CaffeineCacheStore(计数代理) +
     * ResponseCacheService，反射注入 handler 的 responseCacheService 字段。
     */
    private void buildHandler(final boolean cacheEnabled) throws Exception {
        properties = new ResponseCacheProperties();
        properties.setEnabled(cacheEnabled);
        properties.setTtl(Duration.ofMinutes(5));
        CaffeineCacheStore caffeine = new CaffeineCacheStore(properties);
        cacheStore = new CountingCacheStore(caffeine);
        cacheService = new ResponseCacheService(cacheStore, properties, null);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        Field field = ServiceRequestHandler.class.getDeclaredField("responseCacheService");
        field.setAccessible(true);
        field.set(handler, cacheService);
    }

    /**
     * v2.9.10: 构建支持流式缓存的 handler（skipStreaming=false）.
     */
    private void buildHandlerWithStreamingCache(final boolean cacheEnabled) throws Exception {
        properties = new ResponseCacheProperties();
        properties.setEnabled(cacheEnabled);
        properties.setSkipStreaming(false);
        properties.setTtl(Duration.ofMinutes(5));
        CaffeineCacheStore caffeine = new CaffeineCacheStore(properties);
        cacheStore = new CountingCacheStore(caffeine);
        cacheService = new ResponseCacheService(cacheStore, properties, null);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        Field field = ServiceRequestHandler.class.getDeclaredField("responseCacheService");
        field.setAccessible(true);
        field.set(handler, cacheService);
    }

    /**
     * 预置缓存: 以与 handler 完全相同的租户键/服务类型/DTO 计算键并写入。
     *
     * @return 生成的缓存键
     */
    private String seed(final String tenantKey, final ServiceType serviceType,
                        final Object dto, final Object payload) {
        String key = cacheService.buildKey(tenantKey, serviceType, dto);
        assertNotNull(key, "确定性请求应生成缓存键");
        cacheStore.put(key, payload, Duration.ofMinutes(5));
        return key;
    }

    /**
     * v2.9.10: 预置流式缓存: 计算流式请求键并写入 CachedStreamingResponse。
     *
     * @return 生成的缓存键
     */
    private String seedStreaming(final String tenantKey, final ServiceType serviceType,
                                 final Object dto, final CachedStreamingResponse payload) {
        String key = cacheService.buildKey(tenantKey, serviceType, dto);
        assertNotNull(key, "确定性流式请求（skipStreaming=false）应生成缓存键");
        cacheStore.put(key, payload, Duration.ofMinutes(5));
        return key;
    }

    /**
     * 执行完整 handleRequest(exchange) 流程（认证后 buildKey + 缓存读短路）。
     */
    private RunResult run(final ServiceEndpoint endpoint, final String modelName,
                          final String keyId, final List<String> permissions,
                          final Object dto, final ServiceRequestExecutor executor) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        if (dto != null) {
            // 模拟 UniversalController: 原始 DTO 放入 exchange attribute
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        }

        ApiKeyAuthentication auth = new ApiKeyAuthentication(keyId, "sk-" + keyId, permissions);
        auth.setAuthenticated(true);
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(auth);

        ResponseEntity<?> response = handler
                .handleRequest(endpoint, modelName, BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder
                        .withSecurityContext(Mono.just(securityContext)))
                .block();
        String cacheKey = (String) exchange.getRequest().getAttributes()
                .get(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE);
        return new RunResult(response, cacheKey);
    }

    private ServiceRequestExecutor downstreamExecutor() throws Exception {
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return executor;
    }

    private void assertCachedBody(final ResponseEntity<?> response, final Object payload) {
        assertTrue(response.getBody() instanceof RouterResponse);
        RouterResponse<?> body = (RouterResponse<?>) response.getBody();
        assertTrue(body.isSuccess());
        assertEquals("请求成功", body.getMessage());
        assertEquals(payload, body.getData());
    }

    private ChatDTO.Request deterministicChat(final String content) {
        return chatRequest(content, false, 0.0, null);
    }

    /**
     * v2.9.10: 确定性流式 chat 请求（stream=true, temperature=0, n=1）
     */
    private ChatDTO.Request deterministicStreamingChat(final String content) {
        return chatRequest(content, true, 0.0, null);
    }

    private ChatDTO.Request chatRequest(final String content, final boolean stream,
                                        final Double temperature, final ChatDTO.Options options) {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                stream, 256, temperature, null, null, null, null, null, null, options);
    }

    private Map<String, Object> chatResponsePayload(final String id) {
        return Map.of(
                "id", id,
                "object", "chat.completion",
                "created", 1700000000L,
                "model", "gpt-4",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", "你好，我是 AI"),
                        "finish_reason", "stop")));
    }

    private Map<String, Object> embeddingResponsePayload() {
        return Map.of("object", "list", "model", "text-embedding-3-small",
                "data", List.of(Map.of("object", "embedding", "index", 0,
                        "embedding", List.of(0.1d, 0.2d, 0.3d))));
    }

    private Map<String, Object> rerankResponsePayload() {
        return Map.of("id", "rerank-1", "model", "rerank-model",
                "results", List.of(Map.of("index", 0, "score", 0.95d)));
    }

    /**
     * 请求执行结果: 响应 + 流程结束后 httpRequest 中的缓存键。
     */
    private record RunResult(ResponseEntity<?> response, String cacheKey) {
    }

    /**
     * 缓存存储计数代理：委托真实 CaffeineCacheStore，统计 get/put 调用次数，
     * 用于断言「不查缓存」「查一次缓存」等行为（对真实实现零 mock）。
     */
    private static final class CountingCacheStore implements CacheStore {

        private final CaffeineCacheStore delegate;
        private int getCount;
        private int putCount;

        private CountingCacheStore(final CaffeineCacheStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Object> get(final String key) {
            getCount++;
            return delegate.get(key);
        }

        @Override
        public void put(final String key, final Object data, final Duration ttl) {
            putCount++;
            delegate.put(key, data, ttl);
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public void delete(final String key) {
            delegate.delete(key);
        }

        @Override
        public void deleteByPrefix(final String prefix) {
            delegate.deleteByPrefix(prefix);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        int getCount() {
            return getCount;
        }

        int putCount() {
            return putCount;
        }
    }
}
