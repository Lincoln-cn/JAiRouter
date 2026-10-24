package org.unreal.modelrouter.router.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 客户端维度限流接线测试（#623）.
 *
 * <p>该维度此前实现完整但主链路从未调用（{@code tryAcquireClientIp} 只有测试调用），
 * 这里守住三件事：拒绝时真的拦下、放行时不影响主链路、以及调用参数里带的是真实客户端信息。</p>
 */
@DisplayName("客户端维度限流接线测试（#623）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerDimensionRateLimitTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String KEY_ID = "key-dim";

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceCapability adapter;

    private ServiceRequestHandler handler;

    @BeforeEach
    void setUp() {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-dim");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);
        when(adapterRegistry.getAdapter(any(ServiceType.class), any())).thenReturn(adapter);

        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
    }

    @AfterEach
    void tearDown() {
        ServiceRateLimitHolder.clear();
    }

    @Test
    @DisplayName("维度限流拒绝 - 429 且不触达下游")
    void dimensionLimitDenied_returns429() throws Exception {
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireClientIp(any())).thenReturn(false);
        inject("rateLimitManager", rateLimitManager);

        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        ServerWebExchange exchange = exchange();

        StepVerifier.create(invoke(exchange, executor))
                .expectErrorSatisfies(error -> {
                    assertEquals(ResponseStatusException.class, error.getClass());
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();

        // #624 的退避提示同样覆盖这条新接线路径
        assertEquals("1", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        verifyNoInteractions(executor);
    }

    @Test
    @DisplayName("维度限流拒绝 - 调用参数带真实客户端与服务类型")
    void dimensionLimitDenied_passesRequestContext() throws Exception {
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireClientIp(any())).thenReturn(false);
        inject("rateLimitManager", rateLimitManager);

        ServerWebExchange exchange = exchange();
        StepVerifier.create(invoke(exchange, mock(ServiceRequestExecutor.class)))
                .expectError(ResponseStatusException.class)
                .verify();

        ArgumentCaptor<RateLimitContext> captor = ArgumentCaptor.forClass(RateLimitContext.class);
        verify(rateLimitManager).tryAcquireClientIp(captor.capture());
        RateLimitContext context = captor.getValue();
        assertNotNull(context);
        assertEquals(ServiceType.chat, context.getServiceType());
        assertEquals("gpt-4", context.getModelName());
        assertNotNull(context.getClientIp(), "必须带客户端 IP，否则 client-ip 维度会退化成同一个桶");
    }

    @Test
    @DisplayName("维度限流放行 - 请求照常进入下游")
    void dimensionLimitAllowed_proceeds() throws Exception {
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireClientIp(any())).thenReturn(true);
        inject("rateLimitManager", rateLimitManager);

        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any())).thenReturn(Mono.just(ResponseEntity.ok("ok")));

        StepVerifier.create(invoke(exchange(), executor))
                .expectNextMatches(response -> "ok".equals(response.getBody()))
                .verifyComplete();
    }

    @Test
    @DisplayName("未装配 RateLimitManager - 不调用该维度，零行为变更")
    void noRateLimitManager_skipsDimensionCheck() throws Exception {
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any())).thenReturn(Mono.just(ResponseEntity.ok("ok")));

        StepVerifier.create(invoke(exchange(), executor))
                .expectNextMatches(response -> "ok".equals(response.getBody()))
                .verifyComplete();
    }

    private Mono<ResponseEntity<?>> invoke(final ServerWebExchange exchange,
                                           final ServiceRequestExecutor executor) throws Exception {
        ChatDTO.Request dto = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 64, 0.0, null, null, null, null, null, null, null);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        lenient().when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("ok")));
        return handler.handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(securityContext())));
    }

    private ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .header("X-Forwarded-For", "203.0.113.7")
                .build());
    }

    private SecurityContextImpl securityContext() {
        ApiKeyAuthentication authentication =
                new ApiKeyAuthentication(KEY_ID, "sk-" + KEY_ID, List.of("chat"));
        authentication.setAuthenticated(true);
        SecurityContextImpl context = new SecurityContextImpl();
        context.setAuthentication(authentication);
        return context;
    }

    private void inject(final String fieldName, final Object value) throws Exception {
        Field field = ServiceRequestHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
