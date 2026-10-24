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
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务级 / 实例级限流拒绝的退避提示测试（#624）.
 *
 * <p>验证三件事：</p>
 * <ul>
 *   <li>缓存路径的服务级预扣拒绝（429）带 {@code Retry-After}；</li>
 *   <li>{@code selectInstance} 内部抛出的 429（服务级超限）与 503（实例全被限流）都带 {@code Retry-After}；</li>
 *   <li>其它异常（如 500）不受影响，不凭空多出该头。</li>
 * </ul>
 */
@DisplayName("服务级/实例级限流退避提示测试（#624）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerRateLimitHintsTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String KEY_ID = "key-hints";

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
    void setUp() throws Exception {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);
        when(adapterRegistry.getAdapter(any(ServiceType.class), any())).thenReturn(adapter);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
    }

    @AfterEach
    void tearDown() {
        ServiceRateLimitHolder.clear();
    }

    @Test
    @DisplayName("缓存路径服务级预扣拒绝 - 429 带 Retry-After")
    void cachePathServiceRateLimit_returnsRetryAfter() throws Exception {
        RateLimitManager rateLimitManager = mock(RateLimitManager.class);
        when(rateLimitManager.tryAcquireService(any(), anyString(), anyString())).thenReturn(false);
        inject("rateLimitManager", rateLimitManager);

        ServerWebExchange exchange = exchangeWithCacheKey();

        StepVerifier.create(invoke(exchange))
                .expectErrorSatisfies(error -> {
                    assertEquals(ResponseStatusException.class, error.getClass());
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();

        assertEquals("1", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("selectInstance 抛 429 - 带 Retry-After")
    void selectInstanceTooManyRequests_returnsRetryAfter() throws Exception {
        when(registry.selectInstance(any(), anyString(), anyString(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Service rate limit exceeded for service type 'chat'"));
        ServerWebExchange exchange = exchangeWithoutCacheKey();

        StepVerifier.create(invoke(exchange))
                .expectError(ResponseStatusException.class)
                .verify();

        assertEquals("1", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("selectInstance 抛 503（实例全被限流）- 带 Retry-After")
    void selectInstanceServiceUnavailable_returnsRetryAfter() throws Exception {
        when(registry.selectInstance(any(), anyString(), anyString(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "All instances rate limited for model 'gpt-4'"));
        ServerWebExchange exchange = exchangeWithoutCacheKey();

        StepVerifier.create(invoke(exchange))
                .expectError(ResponseStatusException.class)
                .verify();

        assertEquals("1", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("selectInstance 抛 500 - 不得凭空加 Retry-After")
    void selectInstanceServerError_hasNoRetryAfter() throws Exception {
        when(registry.selectInstance(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("selector exploded"));
        ServerWebExchange exchange = exchangeWithoutCacheKey();

        StepVerifier.create(invoke(exchange))
                .expectError(IllegalStateException.class)
                .verify();

        assertNull(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    private Mono<ResponseEntity<?>> invoke(final ServerWebExchange exchange) throws Exception {
        ChatDTO.Request dto = new ChatDTO.Request(
                "gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                false, 64, 0.0, null, null, null, null, null, null, null);
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        lenient().when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return handler.handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(securityContext())));
    }

    private ServerWebExchange exchangeWithoutCacheKey() {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .build());
    }

    private ServerWebExchange exchangeWithCacheKey() {
        ServerWebExchange exchange = exchangeWithoutCacheKey();
        exchange.getRequest().getAttributes()
                .put(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE, "precomputed-cache-key");
        return exchange;
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
