package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.buffer.DataBuffer;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ResponseSanitizationFilter filter;

    @BeforeEach
    void setUp() {
        sanitizationService = mock(SanitizationService.class);
        auditService = mock(SecurityAuditService.class);
        securityProperties = mock(SecurityProperties.class);

        sanitizationConfig = mock(SanitizationConfig.class);
        responseConfig = mock(SanitizationConfig.ResponseSanitization.class);
        when(securityProperties.getSanitization()).thenReturn(sanitizationConfig);
        when(sanitizationConfig.getResponse()).thenReturn(responseConfig);
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

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError()
                    .verify();
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
}
