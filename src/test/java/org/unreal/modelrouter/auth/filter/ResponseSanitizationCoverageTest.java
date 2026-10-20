package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 响应脱敏过滤器的「覆盖面」特征化测试.
 *
 * <p>用<b>真实</b>脱敏服务 + 真实过滤器，钉住三条现状，避免「生产已开启脱敏」被误读成「客户端拿到的响应都已脱敏」：</p>
 * <ol>
 *   <li>{@code /v1/**}（AI 实时推理）与 {@code /api/**}（管理台）都被
 *       {@code ExcludedPathsConfig.DATA_MASKING_EXCLUDED_PATHS} 排除 —— 见该文件 {@code :73-107} 的设计裁决注释；</li>
 *   <li>未被排除的路径上，只有 {@code Content-Type: application/json} 会被脱敏；</li>
 *   <li>{@code text/event-stream}（SSE 流式返回）原样透传。</li>
 * </ol>
 *
 * <p>因此线上 PII 的主防线是记录侧 {@code SanitizationService.sanitizeForStorage}，不是本过滤器。
 * 将来若调整排除面或把脱敏接进流式链路，本类会红，从而强制同步修改语料与对应 issue。</p>
 *
 * @since 3.4.0
 */
@DisplayName("响应脱敏覆盖面（/v1 与 /api 被排除、SSE 不脱敏）")
class ResponseSanitizationCoverageTest {

    private static final String PHONE = "13912345678";
    private static final String JSON_BODY = "{\"phone\":\"" + PHONE + "\",\"password\":\"p@ssw0rd\"}";
    private static final String SSE_BODY = "data: " + JSON_BODY + "\n\n";
    private static final String CUSTOM_PATH = "/custom/json-endpoint";

    private ResponseSanitizationFilter filter;

    @BeforeEach
    void setUp() {
        final SecurityProperties properties = new SecurityProperties();
        final SanitizationConfig.ResponseSanitization response = properties.getSanitization().getResponse();
        response.setEnabled(true);
        response.setSensitiveWords(List.of("password"));
        response.setPiiPatterns(List.of("\\d{11}"));
        response.setMaskingChar("*");
        response.setPreserveJsonStructure(true);
        response.setLogSanitization(false);
        response.setFailOnError(false);

        final SanitizationService sanitizationService =
                new DefaultSanitizationService(new DefaultSanitizationRuleEngine(), properties);
        ((DefaultSanitizationService) sanitizationService).initializeRules();

        final SecurityAuditService auditService = mock(SecurityAuditService.class);
        when(auditService.recordEvent(any())).thenReturn(Mono.empty());

        filter = new ResponseSanitizationFilter(sanitizationService, auditService, properties);
    }

    @Test
    @DisplayName("COV-001: /v1/** 的 JSON 响应被排除，手机号明文到达客户端（设计裁决，非回归）")
    void v1ResponseIsExcludedFromSanitization() {
        final String body = run("/v1/chat/completions", MediaType.APPLICATION_JSON, JSON_BODY);
        assertEquals(JSON_BODY, body, "/v1/** 在 ExcludedPathsConfig 的脱敏排除清单里");
        assertTrue(body.contains(PHONE), "缺口实证：/v1 响应中的手机号未被掩码");
    }

    @Test
    @DisplayName("COV-002: /api/** 的 JSON 响应被排除（管理台需要看到真实配置值）")
    void adminApiResponseIsExcludedFromSanitization() {
        final String body = run("/api/config/quota", MediaType.APPLICATION_JSON, JSON_BODY);
        assertEquals(JSON_BODY, body, "/api/** 在 ExcludedPathsConfig 的脱敏排除清单里");
    }

    @Test
    @DisplayName("COV-003: 未被排除的路径 + JSON ⇒ 确实脱敏（证明规则与夹具都生效）")
    void customJsonResponseIsSanitized() {
        final String body = run(CUSTOM_PATH, MediaType.APPLICATION_JSON, JSON_BODY);
        assertFalse(body.contains(PHONE), "手机号必须被打码，实际=" + body);
        assertTrue(body.contains("***********"), "手机号应替换为等长掩码，实际=" + body);
        assertTrue(body.contains("\"phone\""), "JSON 键必须保留，实际=" + body);
    }

    @Test
    @DisplayName("COV-004: 未被排除的路径 + SSE ⇒ 原样透传（流式返回不脱敏）")
    void sseOnNonExcludedPathIsNotSanitized() {
        final String body = run("/custom/stream", MediaType.TEXT_EVENT_STREAM, SSE_BODY);
        assertEquals(SSE_BODY, body, "text/event-stream 不在 JSON 内容类型判断内，直接放行");
        assertTrue(body.contains(PHONE), "缺口实证：SSE 帧里的手机号明文");
    }

    @Test
    @DisplayName("COV-005: 未被排除的路径 + 分块传输的 JSON ⇒ 仍然脱敏")
    void chunkedJsonOnNonExcludedPathIsSanitized() {
        final MockServerWebExchange exchange = newExchange(CUSTOM_PATH, MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Transfer-Encoding", "chunked");
        StepVerifier.create(filter.filter(exchange, writingChain(JSON_BODY))).verifyComplete();

        final String body = exchange.getResponse().getBodyAsString().block();
        assertFalse(body.contains(PHONE), "分块传输的 JSON 也必须脱敏，实际=" + body);
    }

    // ============ 夹具 ============

    private String run(final String path, final MediaType contentType, final String body) {
        final MockServerWebExchange exchange = newExchange(path, contentType);
        StepVerifier.create(filter.filter(exchange, writingChain(body))).verifyComplete();
        return exchange.getResponse().getBodyAsString().block();
    }

    private static MockServerWebExchange newExchange(final String path, final MediaType responseContentType) {
        final MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        final MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().getHeaders().setContentType(responseContentType);
        return exchange;
    }

    private static WebFilterChain writingChain(final String body) {
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            final ServerWebExchange target = invocation.getArgument(0);
            final DataBuffer buffer = target.getResponse().bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return target.getResponse().writeWith(Mono.just(buffer));
        });
        return chain;
    }
}
