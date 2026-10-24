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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitService;
import org.unreal.modelrouter.router.ratelimit.enduser.LocalEndUserRateLimitCounter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * v3.5.2 PR-2: {@link ServiceRequestHandler} 终端用户级限流接入集成测试。
 *
 * <p>走真实 {@code handleRequest(exchange)} 流程（真实安全上下文 + 真实 WebFlux 交换对象），
 * 端用户限流侧使用真实 {@link EndUserRateLimitService} 与
 * {@link LocalEndUserRateLimitCounter}；仅认证/路由/适配器协作方用 Mockito 桩。</p>
 *
 * <p>覆盖：未超限放行（无 {@code X-RateLimit-*} 头）、超限 429 + 三头 + {@code Retry-After}
 * 且不触实例选择与下游、标识缺失的 skip/reject 两种策略、JWT 调用方跳过、
 * {@code enabled=false} 与服务未装配两条零行为变更路径、429 响应体由全局异常处理器渲染。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceRequestHandler 终端用户限流接入集成测试")
class ServiceRequestHandlerEndUserRateLimitIntegrationTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String KEY_ID = "key-end-user";
    private static final String END_USER = "alice";

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceCapability adapter;

    private EndUserRateLimitProperties properties;

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

        properties = new EndUserRateLimitProperties();
        properties.setEnabled(true);
        properties.setRequestsPerMinute(2);
        properties.setHeaderEnabled(true);

        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        installService(new EndUserRateLimitService(properties, new LocalEndUserRateLimitCounter(100)));
    }

    // ==================== 放行 ====================

    @Test
    @DisplayName("未超限：200 放行且不产生任何限流响应头")
    void underLimit_shouldPassThroughWithoutHeaders() throws Exception {
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"), END_USER);
        final ServiceRequestExecutor executor = downstreamExecutor();

        final ResponseEntity<?> response = invoke(exchange, executor).block(Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("downstream-ok", response.getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        assertNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.END_USER_HEADER_LIMIT));
        assertNull(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    // ==================== 超限 ====================

    @Test
    @DisplayName("超限：429 + Retry-After/X-RateLimit-* 头，且不触实例选择与下游")
    void overLimit_shouldReturn429WithHeaders() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < 2; i++) {
            final ServerWebExchange allowed = buildExchange(deterministicChat("hello-" + i), END_USER);
            assertEquals(HttpStatus.OK, invoke(allowed, executor).block(Duration.ofSeconds(10)).getStatusCode());
        }

        final ServerWebExchange exchange = buildExchange(deterministicChat("hello-2"), END_USER);
        final Mono<ResponseEntity<?>> mono = invoke(exchange, executor);

        StepVerifier.create(mono)
                .expectErrorMatches(error -> error instanceof ResponseStatusException statusException
                        && statusException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(Duration.ofSeconds(10));

        final HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("2", headers.getFirst(ServiceRequestHandler.END_USER_HEADER_LIMIT));
        assertEquals("0", headers.getFirst(ServiceRequestHandler.END_USER_HEADER_REMAINING));
        final String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        assertNotNull(retryAfter, "429 必须带 Retry-After");
        final long retryAfterSeconds = Long.parseLong(retryAfter);
        assertTrue(retryAfterSeconds >= 1 && retryAfterSeconds <= 60,
                "Retry-After 应指向下一整分钟，实际: " + retryAfter);
        assertTrue(Long.parseLong(headers.getFirst(ServiceRequestHandler.END_USER_HEADER_RESET))
                > System.currentTimeMillis() / 1000 - 1, "X-RateLimit-Reset 应是未来时刻");

        verify(registry, times(2)).selectInstance(any(), anyString(), anyString(), any());
        verify(executor, times(2)).execute(any(), any(), any());
    }

    @Test
    @DisplayName("超限 429 的响应体由全局异常处理器按既有格式渲染")
    void overLimit_shouldRenderErrorBody() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        invoke(buildExchange(deterministicChat("a"), END_USER), executor).block(Duration.ofSeconds(10));
        invoke(buildExchange(deterministicChat("b"), END_USER), executor).block(Duration.ofSeconds(10));

        final ServerWebExchange exchange = buildExchange(deterministicChat("c"), END_USER);
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
        assertTrue(body.contains("429"), "响应体应带 429 错误码: " + body);
        assertTrue(!body.contains(END_USER), "响应体不得回显终端用户标识: " + body);
    }

    // ==================== 标识缺失策略 ====================

    @Test
    @DisplayName("标识缺失 + 默认 skip：不计入额度、不拒绝")
    void missingIdentity_defaultSkip_shouldPassThrough() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < 3; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("anon-" + i), null);
            assertEquals(HttpStatus.OK, invoke(exchange, executor).block(Duration.ofSeconds(10)).getStatusCode());
        }
    }

    @Test
    @DisplayName("标识缺失 + reject：429，且不触下游")
    void missingIdentity_reject_shouldReturn429() throws Exception {
        properties.setMissingIdentity(EndUserRateLimitProperties.MissingIdentity.REJECT);
        final ServerWebExchange exchange = buildExchange(deterministicChat("anon"), null);
        final ServiceRequestExecutor executor = downstreamExecutor();

        StepVerifier.create(invoke(exchange, executor))
                .expectErrorMatches(error -> error instanceof ResponseStatusException statusException
                        && statusException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(Duration.ofSeconds(10));
        verify(executor, never()).execute(any(), any(), any());
    }

    // ==================== 零行为变更 ====================

    @Test
    @DisplayName("JWT 调用方：跳过端用户限流（不接受自报标识冒充终端用户）")
    void jwtCaller_shouldBeSkipped() throws Exception {
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < 3; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("jwt-" + i), END_USER);
            final ResponseEntity<?> response = handler
                    .handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(jwtContext())))
                    .block(Duration.ofSeconds(10));
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Test
    @DisplayName("enabled=false：零行为变更（无判定、无响应头）")
    void disabled_shouldBehaveAsBefore() throws Exception {
        properties.setEnabled(false);
        final ServiceRequestExecutor executor = downstreamExecutor();
        for (int i = 0; i < 3; i++) {
            final ServerWebExchange exchange = buildExchange(deterministicChat("off-" + i), END_USER);
            assertEquals(HttpStatus.OK, invoke(exchange, executor).block(Duration.ofSeconds(10)).getStatusCode());
            assertTrue(exchange.getResponse().getHeaders().isEmpty(), "未启用时不得新增任何响应头");
        }
    }

    @Test
    @DisplayName("服务未装配：行为与 v3.5.1 一致（放行、无头）")
    void serviceAbsent_shouldBehaveAsBefore() throws Exception {
        installService(null);
        final ServerWebExchange exchange = buildExchange(deterministicChat("absent"), END_USER);
        final ServiceRequestExecutor executor = downstreamExecutor();

        assertEquals(HttpStatus.OK, invoke(exchange, executor).block(Duration.ofSeconds(10)).getStatusCode());
        assertTrue(exchange.getResponse().getHeaders().isEmpty());
    }

    // ==================== 辅助方法 ====================

    /**
     * 执行完整 handleRequest 流程（认证 + 端用户限流 + 实例选择）。
     *
     * @param exchange 交换对象
     * @param executor 下游执行器
     * @return 响应 Mono
     */
    private Mono<ResponseEntity<?>> invoke(final ServerWebExchange exchange,
                                           final ServiceRequestExecutor executor) {
        return handler.handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(apiKeyContext(KEY_ID, List.of("chat")))));
    }

    /**
     * 反射注入可选依赖 endUserRateLimitService（沿用仓库既有测试注入方式）。
     *
     * @param service 端用户限流服务，可为 {@code null}
     */
    private void installService(final EndUserRateLimitService service) throws Exception {
        final Field field = ServiceRequestHandler.class.getDeclaredField("endUserRateLimitService");
        field.setAccessible(true);
        field.set(handler, service);
    }

    /**
     * 构建真实 WebFlux 交换对象（含原始 DTO 属性与可选的端用户请求头）。
     *
     * @param dto      原始请求 DTO
     * @param endUser  端用户标识，{@code null} 表示不带请求头
     * @return 交换对象
     */
    private ServerWebExchange buildExchange(final ChatDTO.Request dto, final String endUser) {
        final MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.post("/api/v1/chat/completions");
        if (endUser != null) {
            builder.header(EndUserRateLimitProperties.HEADER_NAME, endUser);
        }
        final ServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        return exchange;
    }

    /**
     * API Key 安全上下文。
     *
     * @param keyId       API Key ID
     * @param permissions 权限列表
     * @return 安全上下文
     */
    private SecurityContextImpl apiKeyContext(final String keyId, final List<String> permissions) {
        final ApiKeyAuthentication authentication =
                new ApiKeyAuthentication(keyId, "sk-" + keyId, permissions);
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    /**
     * 控制台 JWT 登录态安全上下文。
     *
     * @return 安全上下文
     */
    private SecurityContextImpl jwtContext() {
        final JwtAuthentication authentication =
                new JwtAuthentication("alice", "jwt-token", List.of("ADMIN"));
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    /**
     * 下游执行器（返回固定 200 响应体）。
     *
     * @return 执行器
     */
    private ServiceRequestExecutor downstreamExecutor() throws Exception {
        final ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return executor;
    }

    /**
     * 带端用户标识的 chat 请求。
     *
     * @param content 用户消息
     * @return 请求 DTO
     */
    private ChatDTO.Request deterministicChat(final String content) {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 256, 0.0, null, null, null, null, null, null, null);
    }
}
