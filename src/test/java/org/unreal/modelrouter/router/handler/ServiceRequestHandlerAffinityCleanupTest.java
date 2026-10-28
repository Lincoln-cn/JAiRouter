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
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 亲和性上下文清理测试（#689）.
 *
 * <p>限流拒绝的三处早退发生在 try 之前，旧实现因此一次都不会执行 clear()，把上一个请求的
 * apiKeyId 留给复用同一线程的下一个请求。这里用「同线程连续两次调用」把该残留变成可断言的事实：
 * 第一次带 callerId 被 429 拒绝，第二次不带 callerId，第二次必须看到 null。</p>
 */
@DisplayName("亲和性上下文清理测试（#689）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerAffinityCleanupTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String CALLER_KEY_A = "key-A";

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    private ServiceRequestHandler handler;

    private Method syncMethod;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-affinity");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);
        when(adapterRegistry.getAdapter(any(ServiceType.class), any())).thenReturn(mock(ServiceCapability.class));

        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        syncMethod = ServiceRequestHandler.class.getDeclaredMethod("handleWithInstanceAdapterSync",
                ServiceEndpoint.class, String.class, String.class,
                org.springframework.http.server.reactive.ServerHttpRequest.class,
                TracingContext.class, ServiceRequestExecutor.class, ServerWebExchange.class);
        syncMethod.setAccessible(true);
        AffinityContextHolder.clear();
        ServiceRateLimitHolder.clear();
    }

    @AfterEach
    void tearDown() {
        AffinityContextHolder.clear();
        ServiceRateLimitHolder.clear();
    }

    @Test
    @DisplayName("限流早退后亲和性上下文必须已被清理")
    void clientDimensionEarlyReturnStillClearsAffinity() throws Exception {
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireClientIp(any())).thenReturn(false);
        inject("rateLimitManager", rateLimitManager);

        StepExpect.error(invokeSync(exchangeWithCaller(CALLER_KEY_A)));

        assertNull(AffinityContextHolder.apiKeyId(), "早退路径必须清理 apiKeyId");
        assertNull(AffinityContextHolder.resolveKey("tenant_model"), "早退路径必须清理全部亲和性组件");
    }

    @Test
    @DisplayName("上一个请求的 callerId 不会串到下一个请求")
    void staleCallerIdIsNotInheritedByTheNextRequest() throws Exception {
        List<String> observedApiKeyIds = new ArrayList<>();
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireClientIp(any())).thenAnswer(invocation -> {
            observedApiKeyIds.add(AffinityContextHolder.apiKeyId());
            return false;
        });
        inject("rateLimitManager", rateLimitManager);

        StepExpect.error(invokeSync(exchangeWithCaller(CALLER_KEY_A)));
        StepExpect.error(invokeSync(exchangeWithoutCaller()));

        assertEquals(2, observedApiKeyIds.size());
        assertEquals(CALLER_KEY_A, observedApiKeyIds.get(0), "第一次请求应看到自己的 callerId");
        assertNull(observedApiKeyIds.get(1), "第二次请求不得看到上一个请求的 callerId");
        assertNotEquals(CALLER_KEY_A, observedApiKeyIds.get(1));
    }

    @Test
    @DisplayName("set 传入 null 必须清除旧值而不是保留")
    void setWithNullsClearsPreviousValues() {
        AffinityContextHolder.set(CALLER_KEY_A, "203.0.113.7", ServiceType.chat.name(), "gpt-4");
        assertEquals(CALLER_KEY_A, AffinityContextHolder.apiKeyId());

        AffinityContextHolder.set(null, null, ServiceType.chat.name(), "gpt-4");

        assertNull(AffinityContextHolder.apiKeyId(), "null 入参必须清除旧值");
        assertNull(AffinityContextHolder.resolveKey("tenant_model"), "用于定键的组件不得残留");
    }

    private Mono<ResponseEntity<?>> invokeSync(final ServerWebExchange exchange) throws Exception {
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any())).thenReturn(Mono.just(ResponseEntity.ok("ok")));
        Mono<ResponseEntity<?>> result = (Mono<ResponseEntity<?>>) syncMethod.invoke(handler,
                ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange.getRequest(),
                mock(TracingContext.class), executor, exchange);
        return result;
    }

    private ServerWebExchange exchangeWithCaller(final String callerId) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .header("X-Forwarded-For", "203.0.113.7")
                .build();
        // MockServerHttpRequest 的属性表是只读的，这里用装饰器补一个可写属性表
        java.util.Map<String, Object> attributes = new java.util.HashMap<>(request.getAttributes());
        attributes.put(ServiceRequestHandler.CALLER_ID_ATTRIBUTE, callerId);
        ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
            @Override
            public java.util.Map<String, Object> getAttributes() {
                return attributes;
            }
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        return new ServerWebExchangeDecorator(exchange) {
            @Override
            public ServerHttpRequest getRequest() {
                return decorated;
            }
        };
    }

    private ServerWebExchange exchangeWithoutCaller() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .header("X-Forwarded-For", "203.0.113.7")
                .build();
        return MockServerWebExchange.from(request);
    }

    private void inject(final String fieldName, final Object value) throws Exception {
        java.lang.reflect.Field field = ServiceRequestHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    /** 断言同步方法返回的 Mono 以错误结束（不关心具体异常类型）。 */
    private static final class StepExpect {
        private StepExpect() {
        }

        static void error(final Mono<?> mono) {
            reactor.test.StepVerifier.create(mono)
                    .expectError()
                    .verify();
        }
    }
}
