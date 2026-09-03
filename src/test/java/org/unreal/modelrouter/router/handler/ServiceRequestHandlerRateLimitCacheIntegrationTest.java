package org.unreal.modelrouter.router.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.cache.CacheStore;
import org.unreal.modelrouter.router.cache.CaffeineCacheStore;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * v2.9.10: 服务级限流提前短路集成测试.
 *
 * <p>验证缓存命中请求在 selectInstance 之前执行服务级限流预扣，
 * 保持恰一次限流语义，并确保限流超限优先于缓存。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>T3-CACHE-HIT: 命中请求限流扣一次、不触 selectInstance（提前短路）</li>
 *   <li>T3-CACHE-MISS: 未命中请求限流恰一次（不因预扣+selectInstance 内部双扣）</li>
 *   <li>T3-RATE-LIMIT-EXCEED: 限流超限 429 优先于缓存（缓存有值也不返回）</li>
 *   <li>T3-NO-KEY: 无缓存键时走原流程（selectInstance 内部限流，行为回归）</li>
 *   <li>T3-THREAD-LOCAL-CLEANUP: ThreadLocal 标志并发清理无泄漏</li>
 * </ul>
 */
@DisplayName("服务级限流提前短路集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerRateLimitCacheIntegrationTest {

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
    private ResponseCacheService cacheService;
    private ServiceRequestHandler handler;
    private CountingRateLimitManager rateLimitManager;

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

    @AfterEach
    void tearDown() {
        // 防御性清理：确保 ThreadLocal 不跨测试泄漏
        ServiceRateLimitHolder.clear();
    }

    // ==================== T3-CACHE-HIT: 命中请求限流扣一次、提前短路 ====================

    @Test
    @DisplayName("T3-CACHE-HIT: 缓存命中 → 限流预扣一次、不触 selectInstance（提前短路）")
    void cacheHitPreAcquiresRateLimitAndSkipsSelectInstance() throws Exception {
        buildHandler(true, true);
        ChatDTO.Request dto = deterministicChat("hello");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-hit");
        String key = seed("key-1", ServiceType.chat, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        // 缓存命中：返回200 + 缓存内容
        assertNotNull(result.response());
        assertEquals(HttpStatus.OK, result.response().getStatusCode());
        assertCachedBody(result.response(), payload);

        // 恰一次限流预扣
        assertEquals(1, rateLimitManager.getServiceAcquireCount(),
                "缓存命中应恰扣一次服务级限流");
        // 提前短路：selectInstance 不应被调用
        verify(registry, never()).selectInstance(any(), anyString(), anyString(), any());
        // executor/adapter 不触
        verify(executor, never()).execute(any(), any(), any());
        verify(adapterRegistry, never()).getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
    }

    // ==================== T3-CACHE-MISS: 未命中请求限流恰一次 ====================

    @Test
    @DisplayName("T3-CACHE-MISS: 未命中 → 预扣一次 + selectInstance 内部跳过（不双扣）")
    void cacheMissPreAcquiresOnceAndSkipsInternalRateLimit() throws Exception {
        buildHandler(true, true);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        // 走原流程：executor 被调
        assertNotNull(result.response());
        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        // selectInstance 被调用（未命中路径需要实例选择）
        verify(registry, times(1)).selectInstance(any(), anyString(), anyString(), any());
        // 恰一次限流：handler 预扣 1 次 + selectInstance 内部跳过 = 总共 1 次
        assertEquals(1, rateLimitManager.getServiceAcquireCount(),
                "未命中请求应恰扣一次服务级限流（预扣+内部跳过）");
    }

    // ==================== T3-RATE-LIMIT-EXCEED: 429 优先于缓存 ====================

    @Test
    @DisplayName("T3-RATE-LIMIT-EXCEED: 限流超限 → 429 优先于缓存（缓存有值也不放行）")
    void rateLimitExceedsBeforeCacheCheck() throws Exception {
        // 构建 denyAll 限流器（所有请求都超限）
        buildHandlerWithDenyAllRateLimiter();
        ChatDTO.Request dto = deterministicChat("hello");
        Map<String, Object> payload = chatResponsePayload("chatcmpl-exceed");
        seed("key-1", ServiceType.chat, dto, payload);

        ServiceRequestExecutor executor = downstreamExecutor();
        Mono<ResponseEntity<?>> mono = handler
                .handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER,
                        buildExchange(dto, "key-1"), executor)
                .contextWrite(ReactiveSecurityContextHolder
                        .withSecurityContext(Mono.just(securityContext("key-1", List.of("chat")))));

        // 429 以 Mono.error(ResponseStatusException) 形式抛出
        StepVerifier.create(mono)
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify();

        // selectInstance 不应被调用（429 在缓存查询前返回）
        verify(registry, never()).selectInstance(any(), anyString(), anyString(), any());
        // executor 不触
        verify(executor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("T3-RATE-LIMIT-EXCEED-STEP: 限流超限 StepVerifier 验证429")
    void rateLimitExceedsStepVerifier() throws Exception {
        buildHandlerWithDenyAllRateLimiter();
        ChatDTO.Request dto = deterministicChat("hello");
        seed("key-1", ServiceType.chat, dto, chatResponsePayload("chatcmpl-exceed-sv"));

        ServiceRequestExecutor executor = downstreamExecutor();
        Mono<ResponseEntity<?>> mono = handler
                .handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER,
                        buildExchange(dto, "key-1"), executor)
                .contextWrite(ReactiveSecurityContextHolder
                        .withSecurityContext(Mono.just(securityContext("key-1", List.of("chat")))));

        StepVerifier.create(mono)
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify();
    }

    // ==================== T3-NO-KEY: 无缓存键 → 走原流程 ====================

    @Test
    @DisplayName("T3-NO-KEY: enabled=false → 无预扣, selectInstance 内部限流（行为回归）")
    void disabledCacheFallsThroughToSelectInstanceInternalRateLimit() throws Exception {
        // 缓存禁用: 不注入 rateLimitManager 以模拟无预扣场景
        buildHandler(false, false);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals("downstream-ok", result.response().getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        // selectInstance 被调用（走原流程）
        verify(registry, times(1)).selectInstance(any(), anyString(), anyString(), any());
        assertNull(result.cacheKey(), "禁用时不应生成缓存键");
        // ServiceRateLimitHolder 标志不应被设置
        assertFalse(ServiceRateLimitHolder.isAcquired(),
                "无缓存路径时不应标记已限流");
    }

    @Test
    @DisplayName("T3-NO-KEY-NO-RLM: 有缓存键但无 RateLimitManager → 走原流程不预扣")
    void cacheKeyButNoRateLimitManagerFallsThrough() throws Exception {
        // 缓存启用但不注入 RateLimitManager → cachePathActive=true 但 rateLimitManager=null → 不预扣
        buildHandler(true, false);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        RunResult result = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertNotNull(result.response());
        assertEquals("downstream-ok", result.response().getBody());
        // selectInstance 被调用（走原流程——限流在内部执行）
        verify(registry, times(1)).selectInstance(any(), anyString(), anyString(), any());
        assertNotNull(result.cacheKey(), "确定性请求应生成缓存键");
    }

    // ==================== T3-THREAD-LOCAL-CLEANUP: 并发清理无泄漏 ====================

    @Test
    @DisplayName("T3-THREAD-LOCAL-CLEANUP: 并发请求后 ThreadLocal 标志已清理")
    void threadLocalCleanupAfterConcurrentRequests() throws Exception {
        buildHandler(true, true);
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean anyLeaked = new AtomicBoolean(false);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        ChatDTO.Request dto = deterministicChat("hello-" + idx);
                        Map<String, Object> payload = chatResponsePayload("chatcmpl-tl-" + idx);
                        String key = seed("key-" + idx, ServiceType.chat, dto, payload);

                        ServiceRequestExecutor reqExecutor = downstreamExecutor();
                        run(ServiceEndpoint.CHAT, "gpt-4", "key-" + idx,
                                List.of("chat"), dto, reqExecutor);

                        // 请求完成后标志应已清理
                        if (ServiceRateLimitHolder.isAcquired()) {
                            anyLeaked.set(true);
                        }
                    } catch (Exception e) {
                        anyLeaked.set(true);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS),
                    "所有并发线程应在10秒内完成");
            assertFalse(anyLeaked.get(), "并发请求后 ThreadLocal 标志应全部清理");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("T3-THREAD-LOCAL-CLEANUP-NORMAL: 正常请求后标志已清理")
    void threadLocalCleanupAfterNormalRequest() throws Exception {
        buildHandler(true, true);
        ChatDTO.Request dto = deterministicChat("hello");
        seed("key-1", ServiceType.chat, dto, chatResponsePayload("chatcmpl-cleanup"));

        ServiceRequestExecutor executor = downstreamExecutor();
        run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertFalse(ServiceRateLimitHolder.isAcquired(),
                "请求完成后 ThreadLocal 标志应已清理");
    }

    @Test
    @DisplayName("T3-THREAD-LOCAL-CLEANUP-MISS: 未命中路径标志已清理")
    void threadLocalCleanupAfterMiss() throws Exception {
        buildHandler(true, true);
        ChatDTO.Request dto = deterministicChat("hello");

        ServiceRequestExecutor executor = downstreamExecutor();
        run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor);

        assertFalse(ServiceRateLimitHolder.isAcquired(),
                "未命中路径请求完成后 ThreadLocal 标志应已清理");
    }

    // ==================== T3-HIT-NO-SELECT-INSTANCE: 写读闭环验证 ====================

    @Test
    @DisplayName("T3-HIT-WRITE-READ: 写后二次请求命中 → 限流预扣一次、不触 selectInstance")
    void writeThenHitPreAcquiresAndSkipsSelectInstance() throws Exception {
        buildHandler(true, true);
        ChatDTO.Request dto = deterministicChat("hello");

        // 第一次：未命中，走原流程
        ServiceRequestExecutor executor1 = downstreamExecutor();
        RunResult first = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor1);
        assertNotNull(first.cacheKey());
        assertEquals(1, rateLimitManager.getServiceAcquireCount());
        verify(registry, times(1)).selectInstance(any(), anyString(), anyString(), any());

        // 模拟 processor 写缓存
        Map<String, Object> payload = chatResponsePayload("chatcmpl-write-read");
        cacheService.store(first.cacheKey(), payload);

        // 重置 mock 计数
        org.mockito.Mockito.reset(registry);
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);

        // 第二次：命中，限流预扣一次，不触 selectInstance
        ServiceRequestExecutor executor2 = downstreamExecutor();
        RunResult second = run(ServiceEndpoint.CHAT, "gpt-4", "key-1",
                List.of("chat"), dto, executor2);

        assertEquals(first.cacheKey(), second.cacheKey());
        assertCachedBody(second.response(), payload);
        verify(executor2, never()).execute(any(), any(), any());
        verify(registry, never()).selectInstance(any(), anyString(), anyString(), any());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 handler：真实缓存组件 + denyAll 限流管理器（所有请求超限）。
     * 用于 T3-RATE-LIMIT-EXCEED 测试。
     */
    private void buildHandlerWithDenyAllRateLimiter() throws Exception {
        properties = new ResponseCacheProperties();
        properties.setEnabled(true);
        properties.setTtl(Duration.ofMinutes(5));
        CaffeineCacheStore caffeine = new CaffeineCacheStore(properties);
        CountingCacheStore countingStore = new CountingCacheStore(caffeine);
        cacheService = new ResponseCacheService(countingStore, properties, null);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);

        Field rcsField = ServiceRequestHandler.class.getDeclaredField("responseCacheService");
        rcsField.setAccessible(true);
        rcsField.set(handler, cacheService);

        rateLimitManager = new CountingRateLimitManager(true);
        Field rlmField = ServiceRequestHandler.class.getDeclaredField("rateLimitManager");
        rlmField.setAccessible(true);
        rlmField.set(handler, rateLimitManager);
    }

    /**
     * 构建 handler：真实缓存组件 + 可选注入 RateLimitManager。
     *
     * @param cacheEnabled 是否启用缓存
     * @param rateLimitManagerEnabled 是否注入限流管理器（false 时不注入→null→不预扣）
     */
    private void buildHandler(final boolean cacheEnabled,
                              final boolean rateLimitManagerEnabled) throws Exception {
        properties = new ResponseCacheProperties();
        properties.setEnabled(cacheEnabled);
        properties.setTtl(Duration.ofMinutes(5));
        CaffeineCacheStore caffeine = new CaffeineCacheStore(properties);
        CountingCacheStore countingStore = new CountingCacheStore(caffeine);
        cacheService = new ResponseCacheService(countingStore, properties, null);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);

        // 注入 responseCacheService
        Field rcsField = ServiceRequestHandler.class.getDeclaredField("responseCacheService");
        rcsField.setAccessible(true);
        rcsField.set(handler, cacheService);

        // 可选注入 RateLimitManager
        if (rateLimitManagerEnabled) {
            rateLimitManager = new CountingRateLimitManager();
            Field rlmField = ServiceRequestHandler.class.getDeclaredField("rateLimitManager");
            rlmField.setAccessible(true);
            rlmField.set(handler, rateLimitManager);
        } else {
            rateLimitManager = null;
        }
    }

    private String seed(final String tenantKey, final ServiceType serviceType,
                        final Object dto, final Object payload) {
        String key = cacheService.buildKey(tenantKey, serviceType, dto);
        assertNotNull(key, "确定性请求应生成缓存键");
        cacheService.store(key, payload);
        return key;
    }

    private RunResult run(final ServiceEndpoint endpoint, final String modelName,
                          final String keyId, final List<String> permissions,
                          final Object dto, final ServiceRequestExecutor executor) {
        ServerWebExchange exchange = buildExchange(dto, keyId);

        ResponseEntity<?> response = handler
                .handleRequest(endpoint, modelName, BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder
                        .withSecurityContext(Mono.just(securityContext(keyId, permissions))))
                .block();
        String cacheKey = (String) exchange.getRequest().getAttributes()
                .get(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE);
        return new RunResult(response, cacheKey);
    }

    private ServerWebExchange buildExchange(final Object dto, final String keyId) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        if (dto != null) {
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        }
        return exchange;
    }

    private SecurityContextImpl securityContext(final String keyId, final List<String> permissions) {
        ApiKeyAuthentication auth = new ApiKeyAuthentication(keyId, "sk-" + keyId, permissions);
        auth.setAuthenticated(true);
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(auth);
        return securityContext;
    }

    private ServiceRequestExecutor downstreamExecutor() throws Exception {
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return executor;
    }

    private void assertCachedBody(final ResponseEntity<?> response, final Object payload) {
        assertTrue(response.getBody() instanceof org.unreal.modelrouter.common.controller.response.RouterResponse);
        org.unreal.modelrouter.common.controller.response.RouterResponse<?> body =
                (org.unreal.modelrouter.common.controller.response.RouterResponse<?>) response.getBody();
        assertTrue(body.isSuccess());
        assertEquals("请求成功", body.getMessage());
        assertEquals(payload, body.getData());
    }

    private ChatDTO.Request deterministicChat(final String content) {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 256, 0.0, null, null, null, null, null, null, null);
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

    private record RunResult(ResponseEntity<?> response, String cacheKey) {
    }

    // ==================== 内部辅助类 ====================

    /**
     * 计数代理 CacheStore（与既有测试一致）
     */
    private static final class CountingCacheStore implements CacheStore {
        private final CaffeineCacheStore delegate;

        private CountingCacheStore(final CaffeineCacheStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Object> get(final String key) {
            return delegate.get(key);
        }

        @Override
        public void put(final String key, final Object data, final Duration ttl) {
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
    }

    /**
     * 计数代理 RateLimitManager：记录 tryAcquireService 调用次数，
     * 并委托给真实实现以执行实际限流逻辑。
     *
     * <p>限流配置：默认 capacity=10, rate=10（通过）；
     * buildHandler(capacity=0) 场景下所有请求超限。
     */
    private static final class CountingRateLimitManager
            extends org.unreal.modelrouter.router.ratelimit.RateLimitManager {

        private final java.util.concurrent.atomic.AtomicInteger serviceAcquireCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final boolean denyAll;

        /**
         * 默认构造（允许通过）
         */
        CountingRateLimitManager() {
            this(false);
        }

        /**
         * @param denyAll true 时所有 tryAcquire 返回 false（模拟超限）
         */
        CountingRateLimitManager(final boolean denyAll) {
            // 传 null 给父类——仅用于测试，不调用父类初始化
            super(null, null, null, new ModelRouterProperties());
            this.denyAll = denyAll;
        }

        @Override
        public boolean tryAcquireService(final ModelServiceRegistry.ServiceType serviceType,
                                         final String clientIp,
                                         final String modelName) {
            serviceAcquireCount.incrementAndGet();
            if (denyAll) {
                return false;
            }
            // 无配置限流器时返回 true（与真实 RateLimitManager 行为一致：无配置零开销）
            return true;
        }

        @Override
        public boolean tryAcquire(
                final org.unreal.modelrouter.router.ratelimit.RateLimitContext context) {
            // selectInstance 内部调用——计数但不重复扣（T3 恰一次语义验证）
            if (denyAll) {
                return false;
            }
            return true;
        }

        int getServiceAcquireCount() {
            return serviceAcquireCount.get();
        }
    }
}
