package org.unreal.modelrouter.common.filter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #681：超过缓存阈值的请求体必须以 413 终止，而不是「消费掉再放行」.
 *
 * <p>改动前的 {@code CachedBodyWebFilter} 在超限分支里打了 warn 之后直接
 * {@code return chain.filter(exchange)}，但此时 body 已被 {@code DataBufferUtils.join}
 * 读空 ⇒ 下游只能读到空体，客户端拿到的是空体派生的 400/500，而不是可操作的 413。</p>
 *
 * <p>本类断言两件事，缺一不可：</p>
 * <ul>
 *   <li><b>不再放行</b>：超限时下游 chain 一次都不应被调用，异常必须是
 *       {@link HttpStatus#PAYLOAD_TOO_LARGE}（旧实现会调用 chain 且下游读到 0 字节）；</li>
 *   <li><b>客户端真能收到 413</b>：用真实 WebFlux 组合（{@code DispatcherHandler} + 真实
 *       {@link ReactiveGlobalExceptionHandler}）验证 {@code /api/v1/**} 与 {@code /v1/**}
 *       两个面都落 413，且错误体形状各自正确。</li>
 * </ul>
 *
 * <p>另有一条边界用例（body 恰好等于 {@link #MAX_CACHE_SIZE}）守住 {@code >} 而非 {@code >=}：
 * 正常大小的请求体仍须被缓存并原样重放给下游。断言只依赖 ASCII 片段与 JSON 结构，不依赖
 * 中文字面量。</p>
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@DisplayName("issue #681: 超限请求体必须以 413 终止")
class CachedBodyWebFilterPayloadLimitTest {

    /**
     * 缓存阈值（与 {@code CachedBodyWebFilter.MAX_CACHE_SIZE} 同值，10MB）.
     */
    private static final int MAX_CACHE_SIZE = 10 * 1024 * 1024;

    /**
     * 超限请求体大小：比阈值多 1 字节.
     */
    private static final int OVERSIZED = MAX_CACHE_SIZE + 1;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String API_PATH = "/api/v1/chat/completions";

    private static final String V1_PATH = "/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    // ==================== 单元级：不再把已消费的 exchange 放行 ====================

    @Test
    @DisplayName("超限：chain 不被调用，异常是 413（旧实现调用 chain 且下游只读到 0 字节）")
    void oversizedBody_shouldTerminateWith413AndNotInvokeChain() {
        final CachedBodyWebFilter filter = new CachedBodyWebFilter();
        final MockServerWebExchange exchange = exchangeWithBody(API_PATH, OVERSIZED);

        final AtomicInteger chainInvocations = new AtomicInteger();
        // 下游读到多少字节：旧实现放行时为 0，正是 issue 描述的「消费后丢弃」
        final AtomicInteger downstreamBytes = new AtomicInteger(-1);
        final WebFilterChain chain = incoming -> {
            chainInvocations.incrementAndGet();
            return DataBufferUtils.join(incoming.getRequest().getBody())
                    .map(buffer -> {
                        final int size = buffer.readableByteCount();
                        DataBufferUtils.release(buffer);
                        downstreamBytes.set(size);
                        return size;
                    })
                    .then();
        };

        final AtomicReference<Throwable> captured = new AtomicReference<>();
        filter.filter(exchange, chain)
                .onErrorResume(error -> {
                    captured.set(error);
                    return Mono.empty();
                })
                .block(TIMEOUT);

        assertEquals(0, chainInvocations.get(),
                "超限请求体不得再交给后续链路（body 已被 join 消费，放行只会让下游读到空体）");
        assertEquals(-1, downstreamBytes.get(), "下游不应收到任何 body 读取机会");

        final Throwable error = captured.get();
        assertNotNull(error, "超限必须以异常终止，而不是静默完成");
        final ResponseStatusException statusError =
                assertInstanceOf(ResponseStatusException.class, error,
                        "必须是 ResponseStatusException 以便全局异常处理器映射状态码: " + error);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, statusError.getStatusCode(),
                "超限必须是 413，而不是 400/500");
        assertNotNull(statusError.getReason(), "413 必须带可读原因");
        assertTrue(statusError.getReason().contains("10MB"),
                "原因里应给出阈值大小，便于客户端自我修正: " + statusError.getReason());
    }

    // ==================== 端到端：两个 HTTP 面都真能收到 413 ====================

    @Test
    @DisplayName("端到端 /api/v1/chat/completions：413 + RouterResponse 形状（errorCode=413）")
    void oversizedBody_onApiFace_shouldRender413() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProbeConfig.class)) {
            final HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();
            final MockServerWebExchange exchange = dispatch(httpHandler, API_PATH, OVERSIZED);

            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode(),
                    "控制台面超限必须回 413");
            assertEquals(MediaType.APPLICATION_JSON,
                    exchange.getResponse().getHeaders().getContentType(),
                    "413 错误体必须是 JSON，且显式声明 Content-Type");

            final JsonNode json = singleJsonObject(bodyOf(exchange));
            assertTrue(json.has("success"), "/api 面必须仍是 RouterResponse 形状: " + json);
            assertEquals("413", json.path("errorCode").asText(), "errorCode 必须是 413: " + json);
            assertTrue(json.path("message").asText().contains("10MB"),
                    "消息应说明阈值大小: " + json);
            assertFalse(json.has("error"), "/api 面不得输出 /v1 协议形状: " + json);
        }
    }

    @Test
    @DisplayName("端到端 /v1/chat/completions：413 + OpenAI 形状（type=invalid_request_error, code=413）")
    void oversizedBody_onV1Face_shouldRenderOpenAiShaped413() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProbeConfig.class)) {
            final HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();
            final MockServerWebExchange exchange = dispatch(httpHandler, V1_PATH, OVERSIZED);

            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode(),
                    "客户端协议面超限同样必须回 413");
            assertEquals(MediaType.APPLICATION_JSON,
                    exchange.getResponse().getHeaders().getContentType(),
                    "413 错误体必须是 JSON，且显式声明 Content-Type");

            final JsonNode json = singleJsonObject(bodyOf(exchange));
            assertTrue(json.has("error"), "/v1 面必须是协议形状: " + json);
            assertEquals("413", json.path("error").path("code").asText(), "code 必须是 413: " + json);
            assertEquals("invalid_request_error", json.path("error").path("type").asText(),
                    "413 属客户端请求过大，按既有映射表归 invalid_request_error: " + json);
            final String message = json.path("error").path("message").asText();
            assertTrue(message.contains("10MB"), "消息应说明阈值大小: " + json);
            assertFalse(message.startsWith("请求处理失败"),
                    "/v1 面必须剥掉内部前缀，只留可读原因: " + json);
            assertFalse(json.has("success"), "/v1 面不得输出 RouterResponse 形状: " + json);
        }
    }

    // ==================== 边界：恰好等于阈值仍然正常缓存并重放 ====================

    @Test
    @DisplayName("边界：body 恰好等于阈值（10MB）仍被缓存并原样重放，下游读到完整字节数")
    void bodyExactlyAtThreshold_shouldStillBeCachedAndReplayed() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProbeConfig.class)) {
            final HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();
            final MockServerWebExchange exchange = dispatch(httpHandler, API_PATH, MAX_CACHE_SIZE);

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode(),
                    "恰好等于阈值不得被拒（判据是 >，不是 >=）");
            assertEquals("body-bytes=" + MAX_CACHE_SIZE, bodyOf(exchange),
                    "缓存后的 body 必须被完整重放给下游（否则重试/日志链路会读到空体）");
        }
    }

    // ==================== 真实 WebFlux 组合 ====================

    /**
     * 真实 WebFlux 组合配置（等价 Spring Boot WebFluxAutoConfiguration 的装配方式）.
     */
    @Configuration
    @EnableWebFlux
    public static class ProbeConfig {

        /**
         * 被测过滤器（作为 {@code WebFilter} bean 参与真实过滤器链）.
         *
         * @return 过滤器
         */
        @Bean
        public CachedBodyWebFilter cachedBodyWebFilter() {
            return new CachedBodyWebFilter();
        }

        /**
         * 项目真实全局异常处理器（{@code WebFilter} 内抛出的异常由它渲染，advice 不参与）.
         *
         * @return 全局异常处理器
         */
        @Bean
        public ReactiveGlobalExceptionHandler reactiveGlobalExceptionHandler() {
            return new ReactiveGlobalExceptionHandler(null);
        }

        /**
         * 探针控制器（读到多少字节就回多少，用于验证 body 是否被完整重放）.
         *
         * @return 探针控制器
         */
        @Bean
        public ProbeController probeController() {
            return new ProbeController();
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
     * 探针控制器：两个面各一个端点，回显下游实际读到的请求体字节数.
     */
    @RestController
    public static class ProbeController {

        /**
         * 控制台面端点.
         *
         * @param exchange 交换对象
         * @return 回显响应
         */
        @PostMapping(API_PATH)
        public Mono<ResponseEntity<String>> apiChat(final ServerWebExchange exchange) {
            return echoBodySize(exchange);
        }

        /**
         * 客户端协议面端点.
         *
         * @param exchange 交换对象
         * @return 回显响应
         */
        @PostMapping(V1_PATH)
        public Mono<ResponseEntity<String>> v1Chat(final ServerWebExchange exchange) {
            return echoBodySize(exchange);
        }

        /**
         * 读取下游可见的请求体并回显字节数.
         *
         * @param exchange 交换对象
         * @return 回显响应
         */
        private Mono<ResponseEntity<String>> echoBodySize(final ServerWebExchange exchange) {
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .map(buffer -> {
                        final int size = buffer.readableByteCount();
                        DataBufferUtils.release(buffer);
                        return ResponseEntity.ok("body-bytes=" + size);
                    })
                    .defaultIfEmpty(ResponseEntity.ok("body-bytes=0"));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建带指定字节数 body 的交换对象（未提交响应）.
     *
     * @param path 请求路径
     * @param size body 字节数
     * @return 交换对象
     */
    private MockServerWebExchange exchangeWithBody(final String path, final int size) {
        final byte[] bytes = new byte[size];
        final DataBuffer buffer = bufferFactory.wrap(bytes);
        final MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(size)
                .body(Flux.just(buffer));
        return MockServerWebExchange.from(request);
    }

    /**
     * 经完整 WebFlux 组合分发请求（真实 DispatcherHandler + 真实异常处理器链）.
     *
     * @param httpHandler 组合后的 HttpHandler
     * @param path        请求路径
     * @param size        body 字节数
     * @return 交换对象（响应已提交）
     */
    private MockServerWebExchange dispatch(final HttpHandler httpHandler, final String path, final int size) {
        final MockServerWebExchange exchange = exchangeWithBody(path, size);
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
}
