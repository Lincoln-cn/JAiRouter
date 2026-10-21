package org.unreal.modelrouter.auth.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>用<b>真实</b>脱敏服务 + 真实过滤器，钉住 issue #560 方案 C 第一阶段的新口径：</p>
 * <ol>
 *   <li>{@code /v1/**} 的<b>非流式 JSON</b> 响应<b>进入</b>脱敏链路（COV-001）；</li>
 *   <li>{@code /v1/**} 的 SSE（{@code text/event-stream}）响应<b>本期仍原样透传</b> ——
 *       为下一期 SSE 覆盖留基线（COV-004）；</li>
 *   <li>{@code /api/**}（管理台）仍被 {@code ExcludedPathsConfig.DATA_MASKING_EXCLUDED_PATHS}
 *       排除，管理响应不得被掩码（COV-002）；</li>
 *   <li>运维端点（{@code /actuator/health}）仍排除（COV-007）；</li>
 *   <li>其余未被排除的路径上，只有 {@code application/json} 会被脱敏（COV-003 / COV-005 / COV-006
 *       是反空转对照）。</li>
 * </ol>
 *
 * <p>线上 PII 的主防线仍是记录侧 {@code SanitizationService.sanitizeForStorage}，不是本过滤器。
 * 将来若再调整排除面、或把脱敏接进流式链路（下一期），本类会红，从而强制同步修改语料与对应 issue。</p>
 *
 * @since 3.4.0
 */
@DisplayName("响应脱敏覆盖面（/v1 非流式 JSON 纳入、/api 与运维端点排除、SSE 本期仍跳过）")
class ResponseSanitizationCoverageTest {

    private static final String PHONE = "13800138000";
    private static final String MASKED_PHONE = "***********";
    private static final String JSON_BODY = "{\"phone\":\"" + PHONE + "\",\"password\":\"p@ssw0rd\"}";
    private static final String SSE_BODY = "data: " + JSON_BODY + "\n\n";
    private static final String CUSTOM_PATH = "/custom/json-endpoint";
    private static final String V1_CHAT_PATH = "/v1/chat/completions";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    @DisplayName("COV-001: /v1/chat/completions 的非流式 JSON 响应被脱敏，结构/键名完好（issue #560）")
    void v1JsonResponseIsSanitized() throws Exception {
        final String body = run(V1_CHAT_PATH, MediaType.APPLICATION_JSON, JSON_BODY);

        assertFalse(body.contains(PHONE), "/v1 非流式 JSON 必须脱敏，实际=" + body);
        assertTrue(body.contains(MASKED_PHONE), "手机号应替换为等长掩码，实际=" + body);
        assertTrue(body.contains("\"phone\""), "JSON 键必须保留，实际=" + body);

        final JsonNode root = OBJECT_MAPPER.readTree(body);
        assertTrue(root.isObject(), "脱敏后仍必须是合法 JSON 对象，实际=" + body);
        assertTrue(root.has("phone"), "脱敏后 phone 键必须仍在，实际=" + body);
        assertEquals(MASKED_PHONE, root.get("phone").asText(), "phone 值应为等长掩码，实际=" + body);
        assertTrue(root.has("password"), "脱敏后 password 键必须仍在，实际=" + body);
    }

    @Test
    @DisplayName("COV-002: /api/** 的 JSON 响应被排除（管理台需要看到真实配置值）")
    void adminApiResponseIsExcludedFromSanitization() {
        final String body = run("/api/config/quota", MediaType.APPLICATION_JSON, JSON_BODY);
        assertEquals(JSON_BODY, body, "/api/** 在 ExcludedPathsConfig 的脱敏排除清单里");
        assertTrue(body.contains(PHONE), "/api/** 管理面不得被掩码");
    }

    @Test
    @DisplayName("COV-003: 未被排除的路径 + JSON ⇒ 确实脱敏（反空转对照）")
    void customJsonResponseIsSanitized() {
        final String body = run(CUSTOM_PATH, MediaType.APPLICATION_JSON, JSON_BODY);
        assertFalse(body.contains(PHONE), "手机号必须被打码，实际=" + body);
        assertTrue(body.contains(MASKED_PHONE), "手机号应替换为等长掩码，实际=" + body);
        assertTrue(body.contains("\"phone\""), "JSON 键必须保留，实际=" + body);
    }

    @Test
    @DisplayName("COV-004: /v1 的 SSE 本期仍原样透传（sseStillBypassedInThisPhase，下一期基线）")
    void sseStillBypassedInThisPhaseOnV1Path() {
        final String body = run(V1_CHAT_PATH, MediaType.TEXT_EVENT_STREAM, SSE_BODY);
        assertEquals(SSE_BODY, body,
                "SSE 由 shouldSanitizeContentType 的 JSON 判定跳过；本期不得改动流式行为");
        assertTrue(body.contains(PHONE), "缺口实证（下一期处理）：SSE 帧里的手机号仍为明文");
    }

    @Test
    @DisplayName("COV-005: 未被排除的路径 + SSE ⇒ 原样透传（流式返回不脱敏）")
    void sseOnNonExcludedPathIsNotSanitized() {
        final String body = run("/custom/stream", MediaType.TEXT_EVENT_STREAM, SSE_BODY);
        assertEquals(SSE_BODY, body, "text/event-stream 不在 JSON 内容类型判断内，直接放行");
        assertTrue(body.contains(PHONE), "缺口实证：SSE 帧里的手机号明文");
    }

    @Test
    @DisplayName("COV-006: 未被排除的路径 + 分块传输的 JSON ⇒ 仍然脱敏")
    void chunkedJsonOnNonExcludedPathIsSanitized() {
        final MockServerWebExchange exchange = newExchange(CUSTOM_PATH, MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Transfer-Encoding", "chunked");
        StepVerifier.create(filter.filter(exchange, writingChain(JSON_BODY))).verifyComplete();

        final String body = exchange.getResponse().getBodyAsString().block();
        assertFalse(body.contains(PHONE), "分块传输的 JSON 也必须脱敏，实际=" + body);
    }

    @Test
    @DisplayName("COV-007: /actuator/health 的 JSON 响应被排除（运维端点未放开）")
    void actuatorHealthResponseIsExcludedFromSanitization() {
        final String body = run("/actuator/health", MediaType.APPLICATION_JSON, JSON_BODY);
        assertEquals(JSON_BODY, body, "/actuator/** 仍在 ExcludedPathsConfig 的脱敏排除清单里");
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
