package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.quota.InMemoryQuotaLedgerRepository;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaEnforcementService;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaRequest;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestExecutor;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v3.1 PR-4d：{@code /v1/**} 网关自身错误体按客户端协议形状输出（真实实现，非堆 mock）。
 *
 * <p>覆盖：{@code /v1/messages}（Anthropic 面）与其余 {@code /v1/**}（OpenAI 面）的 429 / 5xx /
 * 400 / 401 / 403 / 404 形状与状态码保持、类型映射表两侧同源、{@code /api/**} 与非 {@code /v1}
 * 路径的 {@code RouterResponse} 回归、以及<b>真实配额链路</b>在 {@code /v1/messages} 上产出的
 * 429 经真实全局异常处理器渲染为 Anthropic 形状。</p>
 *
 * <p>断言只依赖 ASCII 片段或 JSON 结构（错误消息前缀为中文，而处理器以平台默认字符集写字节，
 * 断言中文字面量会引入与运行环境相关的抖动）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("v3.1 PR-4d: /v1 网关自身错误体协议形状")
class V1ClientErrorBodyMappingTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String ANTHROPIC_PATH = "/v1/messages";

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String API_PATH = "/api/v1/chat/completions";

    private static final String ADMIN_PATH = "/admin/x";

    private static final String DOWNSTREAM_BEARER = "Bearer downstream-token";

    private static final String KEY_ID = "key-v1-error";

    private static final String KEY_HASH = "hash-key-v1-error";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReactiveGlobalExceptionHandler handler;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        // 真实 ErrorTracker（结构化日志协作者用桩），避免 null 分支噪声
        handler = new ReactiveGlobalExceptionHandler(new ErrorTracker(mock(StructuredLogger.class)));
    }

    // ==================== /v1/messages（Anthropic 面） ====================

    @Test
    @DisplayName("/v1/messages + 429：Anthropic 形状 + rate_limit_error + 状态码 429 保持")
    void anthropicMessages429_shouldRenderAnthropicErrorBody() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final ResponseStatusException error = new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "dailyRequestLimit: 3/3");

        final String body = render(exchange, error);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("application/json", exchange.getResponse().getHeaders().getFirst("Content-Type"));

        final JsonNode json = singleJsonObject(body);
        assertEquals(2, json.size(), "Anthropic 错误体只应有 type + error: " + body);
        assertEquals("error", json.path("type").asText());
        assertEquals("rate_limit_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("dailyRequestLimit: 3/3"),
                "必须是原错误消息: " + body);
        assertFalse(json.path("error").has("code"), "Anthropic 面不带 code 字段: " + body);
        assertFalse(json.has("success"), "不得再输出 RouterResponse 字段: " + body);
        assertFalse(json.has("errorCode"), "不得再输出 RouterResponse 字段: " + body);
    }

    @Test
    @DisplayName("/v1/messages/count_tokens + 429：同属 Anthropic 面，形状一致")
    void anthropicCountTokens429_shouldRenderAnthropicErrorBody() throws Exception {
        final MockServerWebExchange exchange = exchangeOf("/v1/messages/count_tokens");
        final String body = render(exchange, new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "rateLimitPerMinute exceeded"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertEquals("error", json.path("type").asText());
        assertEquals("rate_limit_error", json.path("error").path("type").asText());
    }

    @Test
    @DisplayName("/v1/messages + 5xx：api_error（状态码保持）")
    void anthropicMessagesServerError_shouldMapToApiError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final String body = render(exchange, new RuntimeException("downstream reset by peer"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertEquals("api_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("downstream reset by peer"),
                "必须是原错误消息: " + body);
    }

    @Test
    @DisplayName("/v1/messages + 400（请求体不可读）：invalid_request_error")
    void anthropicMessagesInputError_shouldMapToInvalidRequestError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);
        final String body = render(exchange, new ServerWebInputException("Invalid request body"));

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertEquals("invalid_request_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("Invalid request body"),
                "必须是原错误消息: " + body);
    }

    @Test
    @DisplayName("/v1/messages + 401/403：authentication_error / permission_error（鉴权层形状不被破坏）")
    void anthropicMessagesAuthErrors_shouldMapToAuthTypes() throws Exception {
        final MockServerWebExchange unauthorized = exchangeOf(ANTHROPIC_PATH);
        final String unauthorizedBody = render(unauthorized, AuthenticationException.invalidApiKey());

        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getResponse().getStatusCode());
        final JsonNode unauthorizedJson = singleJsonObject(unauthorizedBody);
        assertEquals("error", unauthorizedJson.path("type").asText());
        assertEquals("authentication_error", unauthorizedJson.path("error").path("type").asText());
    }

    // ==================== 其余 /v1/**（OpenAI 面） ====================

    @Test
    @DisplayName("/v1/chat/completions + 429：OpenAI 形状 {error:{message,type,code}} + 状态码 429")
    void openAiChatCompletions429_shouldRenderOpenAiErrorBody() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final ResponseStatusException error = new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "dailyRequestLimit: 3/3");

        final String body = render(exchange, error);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("application/json", exchange.getResponse().getHeaders().getFirst("Content-Type"));

        final JsonNode json = singleJsonObject(body);
        assertEquals(1, json.size(), "OpenAI 错误体只应有 error: " + body);
        final JsonNode errorNode = json.path("error");
        assertEquals(3, errorNode.size(), "error 应含 message/type/code: " + body);
        assertEquals("rate_limit_error", errorNode.path("type").asText());
        assertEquals("429", errorNode.path("code").asText(), "code 应保留原 errorCode: " + body);
        assertTrue(errorNode.path("message").asText().contains("dailyRequestLimit: 3/3"),
                "必须是原错误消息: " + body);
        assertFalse(json.has("success"), "不得再输出 RouterResponse 字段: " + body);
    }

    @Test
    @DisplayName("/v1/chat/completions + 502：api_error + 状态码保持")
    void openAiChatCompletionsBadGateway_shouldMapToApiError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(OPENAI_PATH);
        final String body = render(exchange, new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "upstream disconnected"));

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
        assertEquals("api_error", singleJsonObject(body).path("error").path("type").asText());
    }

    @Test
    @DisplayName("/v1/models + 404：not_found_error（OpenAI 形状）")
    void openAiModelsNotFound_shouldMapToNotFoundError() throws Exception {
        final MockServerWebExchange exchange = exchangeOf("/v1/models");
        final String body = render(exchange, new ResponseStatusException(
                HttpStatus.NOT_FOUND, "model not found"));

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertEquals("not_found_error", json.path("error").path("type").asText());
        assertEquals("404", json.path("error").path("code").asText());
    }

    @Test
    @DisplayName("类型映射表：两侧同源，且状态码逐步保持")
    void errorTypeTable_shouldMapIdenticallyOnBothFaces() throws Exception {
        final Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(400, "invalid_request_error");
        expected.put(401, "authentication_error");
        expected.put(403, "permission_error");
        expected.put(404, "not_found_error");
        expected.put(429, "rate_limit_error");
        expected.put(500, "api_error");
        expected.put(502, "api_error");
        expected.put(503, "api_error");

        for (final Map.Entry<Integer, String> entry : expected.entrySet()) {
            final HttpStatus status = HttpStatus.valueOf(entry.getKey());
            final ResponseStatusException error = new ResponseStatusException(status, "boom");

            final MockServerWebExchange anthropic = exchangeOf(ANTHROPIC_PATH);
            assertEquals(entry.getValue(),
                    singleJsonObject(render(anthropic, error)).path("error").path("type").asText(),
                    "Anthropic 面映射不符: status=" + entry.getKey());
            assertEquals(status, anthropic.getResponse().getStatusCode(), "状态码必须保持原样");

            final MockServerWebExchange openAi = exchangeOf(OPENAI_PATH);
            assertEquals(entry.getValue(),
                    singleJsonObject(render(openAi, error)).path("error").path("type").asText(),
                    "OpenAI 面映射不符: status=" + entry.getKey());
            assertEquals(status, openAi.getResponse().getStatusCode(), "状态码必须保持原样");

            assertEquals(entry.getValue(), V1ErrorBodyMapper.errorTypeOf(entry.getKey()),
                    "映射表直查不符: status=" + entry.getKey());
        }
    }

    // ==================== 回归：非 /v1 路径行为不变 ====================

    @Test
    @DisplayName("回归 /api/v1/chat/completions + 429：仍为 RouterResponse 形状")
    void apiPath429_shouldKeepRouterResponseShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(API_PATH);
        final String body = render(exchange, new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "dailyRequestLimit: 3/3"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertTrue(json.has("success"), "控制台面仍应是 RouterResponse: " + body);
        assertFalse(json.path("success").asBoolean(), "控制台面仍应是 RouterResponse: " + body);
        assertEquals("429", json.path("errorCode").asText());
        assertTrue(json.path("message").asText().contains("dailyRequestLimit: 3/3"),
                "消息应保持既有语义: " + body);
        assertFalse(json.has("error"), "控制台面不得输出 /v1 形状: " + body);
    }

    @Test
    @DisplayName("回归 /admin/x + 429：仍为 RouterResponse 形状")
    void adminPath429_shouldKeepRouterResponseShape() throws Exception {
        final MockServerWebExchange exchange = exchangeOf(ADMIN_PATH);
        final String body = render(exchange, new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "dailyRequestLimit: 3/3"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertTrue(json.has("success"), "非 /v1 路径不应改变形状: " + body);
        assertFalse(json.path("success").asBoolean(), "非 /v1 路径不应改变形状: " + body);
        assertEquals("429", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("回归：交换对象无请求信息（既有 Mockito 桩）时不 NPE，回落 RouterResponse")
    void missingRequestPath_shouldFallBackToLegacyShape() throws Exception {
        final ServerWebExchange exchange = mock(ServerWebExchange.class);
        final MockServerHttpResponse response = new MockServerHttpResponse();
        when(exchange.getResponse()).thenReturn(response);

        assertNull(V1ErrorBodyMapper.requestPathOf(exchange), "无请求信息时路径应为 null");
        assertNull(V1ErrorBodyMapper.toErrorBody(null, 500, "x", "500"), "null 路径不参与映射");

        handler.handle(exchange, new RuntimeException("boom")).block(TIMEOUT);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        final JsonNode json = singleJsonObject(response.getBodyAsString().block(TIMEOUT));
        assertTrue(json.has("success"), "应回落 RouterResponse 形状");
    }

    // ==================== 真实配额链路端到端 ====================

    @Test
    @DisplayName("真实配额超限：/v1/messages → 429 + Anthropic 形状（配额响应头保留）")
    void realQuotaExceededOnAnthropicPath_shouldRenderAnthropicErrorBody() throws Exception {
        final InMemoryQuotaLedgerRepository repository = InMemoryQuotaLedgerRepository.create();
        final QuotaProperties quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);
        final Map<String, ApiKey> apiKeyCache = new LinkedHashMap<>();
        final Map<String, String> keyIdIndex = new LinkedHashMap<>();
        final Clock clock = Clock.fixed(LocalDateTime.of(2026, 3, 14, 13, 45, 30)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        final QuotaLedgerService ledgerService = new QuotaLedgerService(repository.proxy(), quotaProperties, clock);
        final QuotaEnforcementService enforcementService =
                new QuotaEnforcementService(ledgerService, quotaProperties, apiKeyService, clock);
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        // 限额 1；账本已有 1 次请求 → 本次必然命中日请求限额
        apiKeyCache.put(KEY_HASH, ApiKey.builder()
                .keyId(KEY_ID)
                .keyHash(KEY_HASH)
                .dailyRequestLimit(1L)
                .dailyTokenLimit(0L)
                .rateLimitPerMinute(0)
                .enabled(true)
                .build());
        keyIdIndex.put(KEY_ID, KEY_HASH);
        ledgerService.reserve(QuotaRequest.of(QuotaDimension.ofApiKey(KEY_ID), 0L));

        final ServiceRequestHandler requestHandler =
                new ServiceRequestHandler(adapterRegistry, modelServiceRegistry, serviceStateManager, null, null);
        final Field field = ServiceRequestHandler.class.getDeclaredField("quotaEnforcementService");
        field.setAccessible(true);
        field.set(requestHandler, enforcementService);

        final ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        final MockServerWebExchange exchange = exchangeOf(ANTHROPIC_PATH);

        final Throwable error = requestHandler
                .handleRequest(ServiceEndpoint.CHAT, "gpt-4", DOWNSTREAM_BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext())))
                .map(response -> (Throwable) null)
                .onErrorResume(throwable -> Mono.just(throwable))
                .block(TIMEOUT);

        assertInstanceOf(ResponseStatusException.class, error, "超限应以异常形式上抛给全局异常处理器");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ((ResponseStatusException) error).getStatusCode());
        verify(executor, never()).execute(any(), any(), any());
        assertNotNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.QUOTA_HEADER_LIMIT),
                "配额响应头应由配额链路先行写入");

        // 真实全局异常处理器渲染（与生产链路同一实例类型）
        final String body = render(exchange, error);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode json = singleJsonObject(body);
        assertEquals("error", json.path("type").asText());
        assertEquals("rate_limit_error", json.path("error").path("type").asText());
        assertTrue(json.path("error").path("message").asText().contains("dailyRequestLimit"),
                "消息应说明命中的限额: " + body);
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Retry-After"),
                "渲染错误体后配额响应头仍应保留");
    }

    // ==================== 真实 WebFlux 组合（DispatcherHandler + 真实 advice） ====================

    @Test
    @DisplayName("真实 WebFlux 组合：注解控制器内上抛 → /v1 客户端形状、/api 仍 RouterResponse")
    void dispatcherChain_shouldRenderClientShapedBodies() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProbeConfig.class)) {
            final HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();

            final MockServerWebExchange anthropic = dispatchTo(httpHandler, ANTHROPIC_PATH);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, anthropic.getResponse().getStatusCode());
            final JsonNode anthropicJson = singleJsonObject(bodyOf(anthropic));
            assertEquals("error", anthropicJson.path("type").asText());
            assertEquals("rate_limit_error", anthropicJson.path("error").path("type").asText());
            assertTrue(anthropicJson.path("error").path("message").asText().contains("probe quota"),
                    "必须是原错误消息: " + anthropicJson);
            assertFalse(anthropicJson.has("success"), "advice 不应再输出 RouterResponse: " + anthropicJson);

            final MockServerWebExchange openAi = dispatchTo(httpHandler, OPENAI_PATH);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, openAi.getResponse().getStatusCode());
            final JsonNode openAiJson = singleJsonObject(bodyOf(openAi));
            assertEquals("rate_limit_error", openAiJson.path("error").path("type").asText());
            assertEquals("429", openAiJson.path("error").path("code").asText());
            assertFalse(openAiJson.has("success"), "advice 不应再输出 RouterResponse: " + openAiJson);

            final MockServerWebExchange api = dispatchTo(httpHandler, API_PATH);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, api.getResponse().getStatusCode());
            final JsonNode apiJson = singleJsonObject(bodyOf(api));
            assertTrue(apiJson.has("success"), "/api 面必须仍是 RouterResponse: " + apiJson);
            assertEquals("429", apiJson.path("errorCode").asText());
            assertFalse(apiJson.has("error"), "/api 面不得输出 /v1 形状: " + apiJson);
        }
    }

    /**
     * 真实 WebFlux 组合配置（等价 Spring Boot WebFluxAutoConfiguration 的装配方式）.
     */
    @Configuration
    @EnableWebFlux
    public static class ProbeConfig {

        /**
         * 探针控制器.
         *
         * @return 探针控制器
         */
        @Bean
        public ProbeController probeController() {
            return new ProbeController();
        }

        /**
         * 项目真实 {@code @RestControllerAdvice}（WebFlux 中先于 WebExceptionHandler 拿到控制器异常）.
         *
         * @return advice
         */
        @Bean
        public GlobalControllerExceptionHandler globalControllerExceptionHandler() {
            return new GlobalControllerExceptionHandler();
        }

        /**
         * 项目真实 {@link ReactiveGlobalExceptionHandler}.
         *
         * @return 全局异常处理器
         */
        @Bean
        public ReactiveGlobalExceptionHandler reactiveGlobalExceptionHandler() {
            return new ReactiveGlobalExceptionHandler(null);
        }

        /**
         * DispatcherHandler 作为 webHandler（{@code WebHttpHandlerBuilder} 需要该 bean 名）.
         *
         * @param context 应用上下文
         * @return WebHandler
         */
        @Bean(name = "webHandler")
        public WebHandler webHandler(final ApplicationContext context) {
            return new DispatcherHandler(context);
        }
    }

    /**
     * 探针控制器：模拟网关自身错误（配额 429）从注解控制器内上抛.
     */
    @RestController
    public static class ProbeController {

        /**
         * Anthropic 面探针.
         *
         * @return 错误流
         */
        @PostMapping("/v1/messages")
        public Mono<ResponseEntity<String>> anthropicMessages() {
            return Mono.error(quotaError());
        }

        /**
         * OpenAI 面探针.
         *
         * @return 错误流
         */
        @PostMapping("/v1/chat/completions")
        public Mono<ResponseEntity<String>> chatCompletions() {
            return Mono.error(quotaError());
        }

        /**
         * 控制台面探针.
         *
         * @return 错误流
         */
        @PostMapping("/api/v1/chat/completions")
        public Mono<ResponseEntity<String>> apiChatCompletions() {
            return Mono.error(quotaError());
        }

        /**
         * 配额超限异常（与 {@code ServiceRequestHandler} 抛出的一致）.
         *
         * @return 429 状态异常
         */
        private static ResponseStatusException quotaError() {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "probe quota");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 经完整 WebFlux 组合分发请求（真实 DispatcherHandler + 真实异常处理器链）.
     *
     * @param httpHandler 组合后的 HttpHandler
     * @param path        请求路径
     * @return 交换对象（含已提交的响应）
     */
    private MockServerWebExchange dispatchTo(final HttpHandler httpHandler, final String path) {
        final MockServerWebExchange exchange = exchangeOf(path);
        httpHandler.handle(exchange.getRequest(), exchange.getResponse()).block(TIMEOUT);
        return exchange;
    }

    /**
     * 读取交换对象的响应体.
     *
     * @param exchange 交换对象
     * @return 响应体文本
     */
    private String bodyOf(final ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block(TIMEOUT);
    }

    /**
     * 构建真实 WebFlux 交换对象（未提交响应）。
     *
     * @param path 请求路径
     * @return 交换对象
     */
    private MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    /**
     * 用真实异常处理器渲染错误响应并读取响应体.
     *
     * @param exchange 交换对象
     * @param error    待渲染异常
     * @return 响应体文本
     */
    private String render(final ServerWebExchange exchange, final Throwable error) {
        handler.handle(exchange, error).block(TIMEOUT);
        return bodyOf(exchange);
    }

    /**
     * 解析响应体并要求其<b>恰好</b>是一个 JSON 对象（顺带证明响应未被重复写入）.
     *
     * @param body 响应体文本
     * @return JSON 节点
     * @throws Exception 解析失败
     */
    private JsonNode singleJsonObject(final String body) throws Exception {
        assertNotNull(body, "响应体不应为空");
        final JsonParser parser = objectMapper.getFactory().createParser(body);
        final JsonNode json = objectMapper.readTree(parser);
        assertNotNull(json);
        assertNull(parser.nextToken(), "响应体应只包含一个 JSON 对象（未重复写入）: " + body);
        return json;
    }

    /**
     * 构建真实安全上下文（API Key 认证身份）.
     *
     * @return 安全上下文
     */
    private SecurityContextImpl securityContext() {
        final ApiKeyAuthentication authentication =
                new ApiKeyAuthentication(KEY_ID, "sk-" + KEY_ID, List.of("chat"));
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }
}
