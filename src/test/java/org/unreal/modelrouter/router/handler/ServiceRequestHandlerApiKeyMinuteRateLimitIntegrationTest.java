package org.unreal.modelrouter.router.handler;

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
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitService;
import org.unreal.modelrouter.router.ratelimit.enduser.LocalEndUserRateLimitCounter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * v3.8.6: {@link ServiceRequestHandler} API Key 每分钟限流（L1）接入集成测试.
 *
 * <p>走真实 {@code handleRequest(exchange)} 流程（真实安全上下文 + 真实 WebFlux 交换对象），
 * 限流侧使用真实 {@link ApiKeyMinuteRateLimitService} 与 {@link LocalEndUserRateLimitCounter}；
 * 仅认证/路由/适配器协作方用 Mockito 桩。</p>
 *
 * <p>这里钉住的是<b>客户端可见契约</b>：超限必须 429 + {@code X-RateLimit-*} + {@code Retry-After}，
 * 且错误体里的 {@code code} 仍是 {@code RATE_LIMITED_API_KEY} —— 这就是为什么拒绝路径必须抛
 * {@link AuthenticationException} 而<b>不能</b>抛 {@code ResponseStatusException}：后者会被
 * {@code ReactiveGlobalExceptionHandler} 渲染成 {@code code="429"}，属于协议面破坏
 * （见 {@code ReactiveGlobalExceptionHandler:162-174}）。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceRequestHandler API Key 每分钟限流接入集成测试")
class ServiceRequestHandlerApiKeyMinuteRateLimitIntegrationTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String KEY_ID = "key-minute";
    private static final String KEY_HASH = "hash-minute";
    private static final int LIMIT = 2;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceCapability adapter;

    private ApiKeyMinuteRateLimitProperties properties;
    private ApiKeyService apiKeyService;
    private ServiceRequestHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);
        when(adapterRegistry.getAdapter(any(ServiceType.class), any())).thenReturn(adapter);

        apiKeyService = mock(ApiKeyService.class);
        final ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.getRateLimitPerMinute()).thenReturn(LIMIT);
        final Map<String, String> keyIdIndex = new HashMap<>();
        keyIdIndex.put(KEY_ID, KEY_HASH);
        final Map<String, ApiKey> apiKeyCache = new HashMap<>();
        apiKeyCache.put(KEY_HASH, apiKey);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);

        properties = new ApiKeyMinuteRateLimitProperties();
        properties.setEnabled(true);

        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        installService(new ApiKeyMinuteRateLimitService(
                properties, new LocalEndUserRateLimitCounter(100), apiKeyService, null));
    }

    // ==================== 放行 ====================

    @Test
    @DisplayName("未超限：200 放行且不产生限流响应头")
    void underLimit_shouldPassThroughWithoutHeaders() throws Exception {
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        final ResponseEntity<?> response = invoke(exchange, executor).block(Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("downstream-ok", response.getBody());
        assertNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.END_USER_HEADER_LIMIT));
        assertNull(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    // ==================== 超限 ====================

    @Test
    @DisplayName("超限：429 + Retry-After/X-RateLimit-* 头，且不触实例选择与下游")
    void overLimit_shouldReturn429WithHeaders() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < LIMIT; i++) {
            final ServerWebExchange allowed = buildExchange(deterministicChat("ok-" + i));
            assertEquals(HttpStatus.OK, invoke(allowed, executor).block(Duration.ofSeconds(10)).getStatusCode());
        }

        final ServerWebExchange exchange = buildExchange(deterministicChat("blocked"));
        final Mono<ResponseEntity<?>> mono = invoke(exchange, executor);

        StepVerifier.create(mono)
                .expectErrorMatches(error -> error instanceof AuthenticationException authenticationException
                        && authenticationException.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(Duration.ofSeconds(10));

        final HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals(String.valueOf(LIMIT), headers.getFirst(ServiceRequestHandler.END_USER_HEADER_LIMIT));
        assertEquals("0", headers.getFirst(ServiceRequestHandler.END_USER_HEADER_REMAINING));
        final String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        assertNotNull(retryAfter, "429 必须带 Retry-After");
        final long retryAfterSeconds = Long.parseLong(retryAfter);
        assertTrue(retryAfterSeconds >= 1 && retryAfterSeconds <= 60,
                "Retry-After 应指向下一整分钟，实际: " + retryAfter);
        assertTrue(Long.parseLong(headers.getFirst(ServiceRequestHandler.END_USER_HEADER_RESET))
                > System.currentTimeMillis() / 1000 - 1, "X-RateLimit-Reset 应是未来时刻");

        verify(registry, times(LIMIT)).selectInstance(any(), anyString(), anyString(), any());
        verify(executor, times(LIMIT)).execute(any(), any(), any());
    }

    @Test
    @DisplayName("超限 429 的响应体由全局异常处理器渲染，code 仍是 RATE_LIMITED_API_KEY")
    void overLimit_shouldKeepErrorCode() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < LIMIT; i++) {
            invoke(buildExchange(deterministicChat("ok-" + i)), executor).block(Duration.ofSeconds(10));
        }

        final ServerWebExchange exchange = buildExchange(deterministicChat("blocked"));
        final Throwable error = invoke(exchange, executor)
                .map(response -> (Throwable) null)
                .onErrorResume(throwable -> Mono.just(throwable))
                .block(Duration.ofSeconds(10));
        assertNotNull(error, "超限应以异常形式上抛给全局异常处理器");

        new ReactiveGlobalExceptionHandler(null).handle(exchange, error).block(Duration.ofSeconds(10));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final String body = ((MockServerHttpResponse) exchange.getResponse())
                .getBodyAsString().block(Duration.ofSeconds(10));
        assertNotNull(body);
        assertTrue(body.contains("RATE_LIMITED_API_KEY"),
                "code 必须保持 RATE_LIMITED_API_KEY（换成 ResponseStatusException 会退化成 \"429\"）: " + body);
        assertFalse(body.contains(KEY_ID), "响应体不得回显 API Key ID: " + body);
    }

    // ==================== 不做判定的路径 ====================

    @Test
    @DisplayName("JWT 调用方：跳过 API Key 每分钟限流")
    void jwtCaller_shouldBeSkipped() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < LIMIT + 2; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("jwt-" + i));
            final ResponseEntity<?> response = handler
                    .handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(jwtContext())))
                    .block(Duration.ofSeconds(10));
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "控制台 JWT 不应被每分钟限流拦截");
        }
    }

    @Test
    @DisplayName("enabled=false：零行为变更（无判定、无响应头）")
    void disabled_shouldBehaveAsBefore() throws Exception {
        properties.setEnabled(false);
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < LIMIT + 2; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("off-" + i));
            assertEquals(HttpStatus.OK, invoke(exchange, executor).block(Duration.ofSeconds(10)).getStatusCode());
            assertTrue(exchange.getResponse().getHeaders().isEmpty(), "未启用时不得新增任何响应头");
        }
    }

    @Test
    @DisplayName("服务未装配：行为与改动前一致（放行、无头）")
    void serviceAbsent_shouldBehaveAsBefore() throws Exception {
        installService(null);
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < LIMIT + 2; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("absent-" + i));
            assertEquals(HttpStatus.OK, invoke(exchange, executor).block(Duration.ofSeconds(10)).getStatusCode());
            assertTrue(exchange.getResponse().getHeaders().isEmpty());
        }
    }

    // ==================== 辅助方法 ====================

    private Mono<ResponseEntity<?>> invoke(final ServerWebExchange exchange,
                                           final ServiceRequestExecutor executor) {
        return handler.handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(apiKeyContext(KEY_ID, List.of("chat")))));
    }

    /**
     * 反射注入可选依赖 apiKeyMinuteRateLimitService（沿用仓库既有测试注入方式）。
     *
     * @param service 每分钟限流服务，可为 {@code null}
     */
    private void installService(final ApiKeyMinuteRateLimitService service) throws Exception {
        final Field field = ServiceRequestHandler.class.getDeclaredField("apiKeyMinuteRateLimitService");
        field.setAccessible(true);
        field.set(handler, service);
    }

    private ServerWebExchange buildExchange(final ChatDTO.Request dto) {
        final ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/chat/completions").build());
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        return exchange;
    }

    private SecurityContextImpl apiKeyContext(final String keyId, final List<String> permissions) {
        final ApiKeyAuthentication authentication =
                new ApiKeyAuthentication(keyId, "sk-" + keyId, permissions);
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    private SecurityContextImpl jwtContext() {
        final JwtAuthentication authentication =
                new JwtAuthentication("alice", "jwt-token", List.of("ADMIN"));
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    private ServiceRequestExecutor downstreamExecutor() throws Exception {
        final ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return executor;
    }

    private ChatDTO.Request deterministicChat(final String content) {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 256, 0.0, null, null, null, null, null, null, null);
    }
}
