package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ResponseSanitizationFilter 测试
 *
 * <p>issue #307：本类此前的 {@code setUp} 从未 stub {@code response.enabled}，而 Mockito 对 boolean 的
 * 默认返回值是 {@code false} ⇒ {@code filter()} 在第一个 {@code if} 就原样返回，装饰器从未挂上、
 * {@code sanitizeResponse} 从未被调用，FILT-003~006 甚至没有任何断言，「通过」只等于「没有报错」。
 * 现在默认按<b>生产配置</b>（{@code jairouter.security.sanitization.response.enabled=true}）装配，
 * 并让 mock 的 {@code chain} 真正写一次响应体，使每条用例都走过装饰器的 writeWith 分支。</p>
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ResponseSanitizationFilter 测试")
class ResponseSanitizationFilterTest {

    private static final String JSON_PATH = "/custom/json-endpoint";
    private static final String ORIGINAL_BODY = "{\"phone\":\"13800000000\",\"token\":\"sk-abc\"}";
    private static final String SANITIZED_BODY = "{\"phone\":\"138****0000\",\"token\":\"***\"}";

    private SanitizationService sanitizationService;
    private SecurityAuditService auditService;
    private SecurityProperties securityProperties;
    private SanitizationConfig sanitizationConfig;
    private SanitizationConfig.ResponseSanitization responseConfig;
    private SanitizationConfig.Scope scope;
    private ResponseSanitizationFilter filter;

    @BeforeEach
    void setUp() {
        sanitizationService = mock(SanitizationService.class);
        auditService = mock(SecurityAuditService.class);
        securityProperties = mock(SecurityProperties.class);

        sanitizationConfig = mock(SanitizationConfig.class);
        responseConfig = mock(SanitizationConfig.ResponseSanitization.class);
        scope = new SanitizationConfig.Scope();
        when(securityProperties.getSanitization()).thenReturn(sanitizationConfig);
        when(sanitizationConfig.getResponse()).thenReturn(responseConfig);
        when(responseConfig.getScope()).thenReturn(scope);
        // 生产/预发 profile 的真实取值（issue #307）：dev 与 legacy 才是 false
        when(responseConfig.isEnabled()).thenReturn(true);
        when(responseConfig.isLogSanitization()).thenReturn(false);
        when(responseConfig.isFailOnError()).thenReturn(false);

        when(auditService.recordEvent(any())).thenReturn(Mono.empty());

        filter = new ResponseSanitizationFilter(sanitizationService, auditService, securityProperties);
    }

    // ============ 夹具 ============

    /**
     * 构造一个已设置响应内容类型的 exchange。
     */
    private static MockServerWebExchange newExchange(final String path, final MediaType responseContentType) {
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        if (responseContentType != null) {
            exchange.getResponse().getHeaders().setContentType(responseContentType);
        }
        return exchange;
    }

    /**
     * 让 mock 的 chain 真正写一次响应体 —— 否则装饰器上的 writeWith 不会被触发。
     */
    private static WebFilterChain writingChain(final String body) {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange target = invocation.getArgument(0);
            DataBuffer buffer = target.getResponse().bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return target.getResponse().writeWith(Mono.just(buffer));
        });
        return chain;
    }

    private static WebFilterChain emptyChain() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    /**
     * 断言响应<b>未被装饰</b>：必须把同一个 exchange 实例原样传下去。
     *
     * <p>装饰器经 {@code exchange.mutate().response(...).build()} 产生的是新实例，
     * 因此 {@code assertSame} 能真正区分「原样放行」与「挂上装饰器」。</p>
     */
    private static void assertNotDecorated(final WebFilterChain chain, final ServerWebExchange exchange) {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertSame(exchange, captor.getValue(), "该路径不得被装饰，必须原样传同一个 exchange");
    }

    @Nested
    @DisplayName("排除路径测试")
    class ExcludedPathTests {

        @Test
        @DisplayName("FILT-001: 排除路径 - actuator路径跳过脱敏")
        void testActuatorPathExcluded() {
            MockServerWebExchange exchange = newExchange("/actuator/health", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"status\":\"UP\"}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002: 排除路径 - swagger路径跳过脱敏")
        void testSwaggerPathExcluded() {
            MockServerWebExchange exchange = newExchange("/swagger-ui/index.html", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"openapi\":\"3.0.0\"}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002b: 排除路径 - 管理台 /api/** 跳过网关响应脱敏")
        void testAdminApiPathExcluded() {
            MockServerWebExchange exchange = newExchange("/api/config/quota", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"enabled\":true}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002c: scope=include 时未列出的管理台路径同样不脱敏")
        void testIncludeScopeSkipsUnlistedPath() {
            scope.setMode("include");
            scope.setPatterns(List.of("/v1/**"));

            MockServerWebExchange exchange = newExchange("/api/config/quota", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"enabled\":true}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002d: scope 自定义 exclude 集合整体替换内置集合")
        void testCustomExcludeScopeReplacesDefaults() {
            scope.setPatterns(List.of("/v1/**"));

            MockServerWebExchange exchange = newExchange("/v1/chat/completions", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"phone\":\"13800138000\"}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002e: scope=include 时列出的 /v1 路径仍进入脱敏")
        void testIncludeScopeSanitizesListedPath() {
            scope.setMode("include");
            scope.setPatterns(List.of("/v1/**"));
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just(SANITIZED_BODY));

            MockServerWebExchange exchange = newExchange("/v1/chat/completions", MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain("{\"phone\":\"13800138000\"}");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(sanitizationService).sanitizeResponse(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("enabled 配置测试")
    class EnabledConfigTests {

        @Test
        @DisplayName("FILT-008: enabled=false 时过滤器不执行脱敏，原样透传")
        void testFilterDisabledWhenEnabledFalse() {
            when(responseConfig.isEnabled()).thenReturn(false);

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertNotDecorated(chain, exchange);
            verifyNoInteractions(sanitizationService);
            // 未装饰 ⇒ mock 响应收到的就是 chain 里的原文
            StepVerifier.create(exchange.getResponse().getBodyAsString())
                    .expectNext(ORIGINAL_BODY)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("内容类型测试")
    class ContentTypeTests {

        @Test
        @DisplayName("FILT-003: 内容类型 - JSON 响应确实进入脱敏服务")
        void testJsonContentTypeNeedsSanitization() {
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just(SANITIZED_BODY));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(sanitizationService).sanitizeResponse(eq(ORIGINAL_BODY), contains("application/json"));
        }

        @Test
        @DisplayName("FILT-004: 内容类型 - 非 JSON 响应跳过脱敏并原样透传")
        void testNonJsonContentTypeSkipped() {
            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_OCTET_STREAM);
            WebFilterChain chain = writingChain("binary-payload");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(sanitizationService);
            StepVerifier.create(exchange.getResponse().getBodyAsString())
                    .expectNext("binary-payload")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("脱敏服务调用测试")
    class SanitizationServiceTests {

        @Test
        @DisplayName("FILT-005: 脱敏成功 —— 客户端收到的是脱敏后的内容")
        void testSuccessfulSanitization() {
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just(SANITIZED_BODY));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            StepVerifier.create(exchange.getResponse().getBodyAsString())
                    .expectNext(SANITIZED_BODY)
                    .verifyComplete();
        }

        @Test
        @DisplayName("FILT-006: 脱敏失败 + fail-on-error=false —— 回落到原始内容，客户端仍拿到完整响应")
        void testSanitizationFailureReturnsOriginal() {
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Sanitization failed")));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            // 这是生产配置（enabled=true / fail-on-error 默认 false）下的兜底语义：
            // 脱敏失败不会打断客户端响应，但会静默放行未脱敏内容（只留日志与审计）
            StepVerifier.create(exchange.getResponse().getBodyAsString())
                    .expectNext(ORIGINAL_BODY)
                    .verifyComplete();

            // 可区分性（A/B 反证中发现）：只断言「客户端拿到原文」时，「脱敏失败后回落原文」与
            // 「过滤器压根没跑」两种情形都会通过 ⇒ 必须同时断言脱敏服务确实被调用过。
            verify(sanitizationService).sanitizeResponse(eq(ORIGINAL_BODY), contains("application/json"));
        }

        @Test
        @DisplayName("FILT-006b: 脱敏失败 + fail-on-error=true —— 以异常终止，不放行未脱敏内容")
        void testSanitizationFailureFailsClosedWhenConfigured() {
            when(responseConfig.isFailOnError()).thenReturn(true);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Sanitization failed")));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            // 必须是 SanitizationException：中央处理器正是按这个类型分派错误体（issue #317）
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(thrown -> assertInstanceOf(SanitizationException.class, thrown,
                            "过滤器必须原样抛出 SanitizationException，否则中央处理器上那条脱敏分支不可达"))
                    .verify();
        }

        @Test
        @DisplayName("FILT-006c: 客户端错误体由中央处理器产出，过滤器只终止且不写体（issue #317）")
        void sanitizationFailureLeavesErrorBodyToCentralHandler() {
            when(responseConfig.isFailOnError()).thenReturn(true);
            // 消息里带「脱敏细节」，用来验证它不会被发给客户端
            final SanitizationException failure = new SanitizationException(
                    "内部细节：规则 [phone] 编译失败", SanitizationException.CONTENT_PROCESSING_FAILED);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.error(failure));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            AtomicReference<Throwable> propagated = new AtomicReference<>();
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(propagated::set)
                    .verify();

            // ① 过滤器自己不写响应体 —— MockServerHttpResponse 只有被写或被 complete 之后才是 committed
            // （不能靠「读体为空」证明：未写内容时 getBodyAsString() 会直接抛 IllegalStateException）
            assertFalse(exchange.getResponse().isCommitted(),
                    "过滤器不得自己写错误体：那会产生第二个写入者，以及第三套客户端解析不了的形状");

            // ② 把同一个异常交给中央处理器：这是真实链路上的下一个环节（WebFilter 抛出的异常不走 @RestControllerAdvice）
            MockServerWebExchange terminal = MockServerWebExchange.from(
                    MockServerHttpRequest.post(JSON_PATH).build());
            StepVerifier.create(new ReactiveGlobalExceptionHandler(mock(ErrorTracker.class))
                            .handle(terminal, propagated.get()))
                    .verifyComplete();

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, terminal.getResponse().getStatusCode(),
                    "脱敏失败的终止语义是 5xx（SanitizationException 自身携带的状态码）");
            StepVerifier.create(terminal.getResponse().getBodyAsString())
                    .expectNextMatches(body -> body.contains("数据处理失败")
                            && !body.contains("phone")
                            && !body.contains("内部细节"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("审计日志测试")
    class AuditLogTests {

        @Test
        @DisplayName("FILT-007: 审计日志 - 启用日志时记录脱敏事件，且事件类型可解析（issue #301）")
        void testAuditLogEnabled() {
            when(responseConfig.isLogSanitization()).thenReturn(true);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just(SANITIZED_BODY));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            ArgumentCaptor<SecurityAuditEvent> eventCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(auditService).recordEvent(eventCaptor.capture());
            SecurityAuditEvent event = eventCaptor.getValue();
            assertEquals(AuditEventType.RESPONSE_SANITIZATION, AuditEventType.valueOf(event.getEventType()));
            assertEquals("SANITIZE_RESPONSE", event.getAction());
            assertEquals(JSON_PATH, event.getResource());
            assertTrue(event.isSuccess());
        }

        @Test
        @DisplayName("FILT-007b: 未启用审计日志时不记录事件")
        void testAuditLogDisabled() {
            when(responseConfig.isLogSanitization()).thenReturn(false);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just(SANITIZED_BODY));

            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(auditService);
        }
    }

    @Nested
    @DisplayName("响应缓冲上限测试（issue #594）")
    class BufferLimitTests {

        @Test
        @DisplayName("FILT-008: Content-Length 超过上限时原样放行，不进入脱敏")
        void oversizeByContentLengthPassesThrough() {
            when(responseConfig.getMaxBufferedBytes()).thenReturn(1024L);
            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().setContentLength(4096);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(sanitizationService);
            assertEquals(ORIGINAL_BODY, exchange.getResponse().getBodyAsString().block(),
                    "超限时必须原样输出，不能因为跳过脱敏而丢内容");
        }

        @Test
        @DisplayName("FILT-009: 长度未知（chunked）且超过上限时原样透传，不因体积把成功的响应判死（issue #822）")
        void oversizeWithoutContentLengthPassesThrough() {
            when(responseConfig.getMaxBufferedBytes()).thenReturn(1024L);
            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);

            byte[] chunk = new byte[800];
            java.util.Arrays.fill(chunk, (byte) 'a');
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenAnswer(invocation -> {
                ServerWebExchange target = invocation.getArgument(0);
                return target.getResponse().writeWith(Flux.just(
                        target.getResponse().bufferFactory().wrap(chunk),
                        target.getResponse().bufferFactory().wrap(chunk),
                        target.getResponse().bufferFactory().wrap(chunk)));
            });

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(sanitizationService);
            String written = exchange.getResponse().getBodyAsString().block();
            assertNotNull(written, "超限透传必须写出内容");
            assertEquals(2400, written.length(),
                    "超限时必须原样输出全部字节：既不能丢内容，也不能因为体积把响应判死");
        }

        @Test
        @DisplayName("FILT-010: 上限配置为 0 时按 1 KiB 下限生效，不会退化成无上限")
        void nonPositiveLimitFallsBackToFloor() {
            when(responseConfig.getMaxBufferedBytes()).thenReturn(0L);
            MockServerWebExchange exchange = newExchange(JSON_PATH, MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().setContentLength(2048);
            WebFilterChain chain = writingChain(ORIGINAL_BODY);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(sanitizationService);
        }
    }

    @Nested
    @DisplayName("SSE 逐帧脱敏测试（issue #560 第二阶段）")
    class SseStreamingTests {

        private static final String SSE_FRAMES =
                "event: delta\n"
                + "id: 1\n"
                + "data: {\"phone\":\"13800000000\"}\n"
                + "\n"
                + "data: [DONE]\n"
                + "\n";

        @Test
        @DisplayName("FILT-011: 只脱敏 data: 行负载，event/id/空行/[DONE] 与分帧原样保留")
        void sseDataFramesAreSanitized() {
            when(responseConfig.isSanitizeSse()).thenReturn(true);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenAnswer(invocation -> Mono.just(
                            invocation.<String>getArgument(0).replace("13800000000", "***********")));

            MockServerWebExchange exchange = newExchange("/v1/chat/completions", MediaType.TEXT_EVENT_STREAM);
            StepVerifier.create(filter.filter(exchange, writingChain(SSE_FRAMES))).verifyComplete();

            assertEquals(
                    "event: delta\nid: 1\ndata: {\"phone\":\"***********\"}\n\ndata: [DONE]\n\n",
                    exchange.getResponse().getBodyAsString().block());
            // 帧数与行序不能变：两次 data: 行只有第一条被当作 JSON 处理
            verify(sanitizationService, times(1)).sanitizeResponse(anyString(), anyString());
        }

        @Test
        @DisplayName("FILT-012: sanitize-sse=false 时 SSE 原样透传，不调用脱敏服务")
        void sseDisabledPassesThrough() {
            when(responseConfig.isSanitizeSse()).thenReturn(false);

            MockServerWebExchange exchange = newExchange("/v1/chat/completions", MediaType.TEXT_EVENT_STREAM);
            StepVerifier.create(filter.filter(exchange, writingChain(SSE_FRAMES))).verifyComplete();

            assertEquals(SSE_FRAMES, exchange.getResponse().getBodyAsString().block());
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-013: SSE 不走整段缓冲 —— 分块到达且总量超过 max-buffered-bytes 仍能正常脱敏")
        void sseIsStreamedNotBufferedAsWholeBody() {
            when(responseConfig.isSanitizeSse()).thenReturn(true);
            when(responseConfig.getMaxBufferedBytes()).thenReturn(1024L);
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenAnswer(invocation -> Mono.just(
                            invocation.<String>getArgument(0).replace("13800000000", "***********")));

            // 每块 900 字节，合计 1800 > 1024：若是整段缓冲（#594 的 join 路径）会被判超限
            String filler = "x".repeat(860);
            String frameOne = "data: {\"phone\":\"13800000000\",\"pad\":\"" + filler + "\"}\n\n";
            String frameTwo = "data: {\"phone\":\"13800000000\",\"pad\":\"" + filler + "\"}\n\n";

            MockServerWebExchange exchange = newExchange("/v1/chat/completions", MediaType.TEXT_EVENT_STREAM);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenAnswer(invocation -> {
                ServerWebExchange target = invocation.getArgument(0);
                return target.getResponse().writeWith(Flux.just(
                        target.getResponse().bufferFactory().wrap(frameOne.getBytes(StandardCharsets.UTF_8)),
                        target.getResponse().bufferFactory().wrap(frameTwo.getBytes(StandardCharsets.UTF_8))));
            });

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            String body = exchange.getResponse().getBodyAsString().block();
            assertNotNull(body);
            assertEquals(2, body.split("\\n\\n").length, "两帧必须都还在");
            assertFalse(body.contains("13800000000"), "两块帧都必须完成脱敏");
            // 跨块的残行也要能拼回来：第二帧若被当成半个 JSON，就换不出号码
            assertEquals(2, body.split("\\*{11}").length - 1);
        }
    }
}
