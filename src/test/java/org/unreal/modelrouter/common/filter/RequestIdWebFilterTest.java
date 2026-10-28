package org.unreal.modelrouter.common.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.util.SecurityUtils;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RequestIdWebFilter 单元测试（issue #724）
 *
 * <p>守护的事实：响应侧必须出现 {@code X-Request-Id}，且优先级为「入站请求头 → tracing traceId
 * → 新生成 UUID」；写头发生在过滤器链执行之前，因此 401/5xx 之类提前终止的响应同样带该头。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.4
 */
@DisplayName("RequestIdWebFilter 响应请求 ID 回写测试（#724）")
class RequestIdWebFilterTest {

    private static final String HEADER = TracingConstants.Headers.X_REQUEST_ID;

    private RequestIdWebFilter filter;
    private AtomicInteger invocations;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RequestIdWebFilter();
        invocations = new AtomicInteger();
        chain = exchange -> {
            invocations.incrementAndGet();
            return Mono.empty();
        };
    }

    private static MockServerWebExchange jsonExchange(final String... headers) {
        final MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
                .get("/v1/chat/completions")
                .header("Content-Type", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private void filterThrough(final ServerWebExchange exchange) {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    private static String responseHeader(final ServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().getFirst(HEADER);
    }

    @Nested
    @DisplayName("请求 ID 来源优先级")
    class Resolution {

        @Test
        @DisplayName("入站 X-Request-Id 被原样回显（修复前只在自生成时才写头）")
        void echoesIncomingRequestId() {
            final MockServerWebExchange exchange = jsonExchange(HEADER, "client-req-0001");

            filterThrough(exchange);

            assertEquals("client-req-0001", responseHeader(exchange),
                    "客户端已带的请求 ID 必须回显到响应头");
            assertEquals("client-req-0001",
                    exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE),
                    "交换属性必须与响应头是同一个 ID，供下游复用");
            assertEquals(1, invocations.get(), "过滤器必须放行且只放行一次");
        }

        @Test
        @DisplayName("入站请求头优先于 tracing traceId")
        void requestHeaderWinsOverTraceId() {
            final MockServerWebExchange exchange = jsonExchange(HEADER, "client-req-0002");
            exchange.getAttributes().put(TracingConstants.ContextKeys.TRACING_CONTEXT,
                    contextWithTraceId("trace-from-tracing"));

            filterThrough(exchange);

            assertEquals("client-req-0002", responseHeader(exchange));
        }

        @Test
        @DisplayName("缺少入站请求头时复用 tracing 的 traceId")
        void reusesTraceIdWhenHeaderMissing() {
            final MockServerWebExchange exchange = jsonExchange();
            exchange.getAttributes().put(TracingConstants.ContextKeys.TRACING_CONTEXT,
                    contextWithTraceId("trace-9f3c"));

            filterThrough(exchange);

            assertEquals("trace-9f3c", responseHeader(exchange),
                    "tracing 已开启时应复用 traceId，避免一次请求出现两个 ID");
            assertEquals("trace-9f3c",
                    exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE));
        }

        @Test
        @DisplayName("只有 TRACE_ID 属性时也复用（部分链路只透传该属性）")
        void reusesTraceIdAttribute() {
            final MockServerWebExchange exchange = jsonExchange();
            exchange.getAttributes().put(TracingConstants.ContextKeys.TRACE_ID, "trace-attr-77");

            filterThrough(exchange);

            assertEquals("trace-attr-77", responseHeader(exchange));
        }

        @Test
        @DisplayName("无请求头且无 tracing 时生成 UUID，且属性与响应头一致")
        void generatesIdWhenNothingAvailable() {
            final MockServerWebExchange exchange = jsonExchange();

            filterThrough(exchange);

            final String generated = responseHeader(exchange);
            assertNotNull(generated, "必须生成请求 ID 并写响应头");
            assertFalse(generated.isBlank());
            assertEquals(generated, exchange.getAttribute(RequestIdWebFilter.REQUEST_ID_ATTRIBUTE));
            assertNotNull(UUID.fromString(generated), "生成的 ID 应为 UUID 形状");
        }

        @Test
        @DisplayName("空白请求头视为缺失，不会被原样回显")
        void blankHeaderFallsBack() {
            final MockServerWebExchange exchange = jsonExchange(HEADER, "   ");

            filterThrough(exchange);

            final String requestId = responseHeader(exchange);
            assertNotNull(requestId);
            assertFalse(requestId.isBlank(), "空白 ID 回显会让客户端无法用它排障");
            assertNotNull(UUID.fromString(requestId));
        }
    }

    @Nested
    @DisplayName("写头时机与工具方法")
    class TimingAndUtility {

        @Test
        @DisplayName("下游报错时响应头已在链执行前写好")
        void headerWrittenBeforeChainFailure() {
            final MockServerWebExchange exchange = jsonExchange(HEADER, "client-req-0003");
            final WebFilterChain failingChain = e -> Mono.error(new IllegalStateException("boom"));

            StepVerifier.create(filter.filter(exchange, failingChain))
                    .expectErrorMessage("boom")
                    .verify();

            assertEquals("client-req-0003", responseHeader(exchange),
                    "鉴权失败/上游 5xx 等提前终止的响应也必须带请求 ID");
        }

        @Test
        @DisplayName("SecurityUtils.extractRequestId 直接调用也回写响应头（旧实现只在自生成时写）")
        void utilAlwaysEchoesHeader() {
            final MockServerWebExchange exchange = jsonExchange(HEADER, "client-req-0004");

            final String requestId = SecurityUtils.extractRequestId(exchange);

            assertEquals("client-req-0004", requestId);
            assertEquals("client-req-0004", responseHeader(exchange));
        }

        @Test
        @DisplayName("重复调用不产生重名响应头")
        void repeatedCallsDoNotDuplicateHeader() {
            final MockServerWebExchange exchange = jsonExchange();

            final String first = SecurityUtils.extractRequestId(exchange);
            final String second = SecurityUtils.extractRequestId(exchange);

            final List<String> values = exchange.getResponse().getHeaders().get(HEADER);
            assertNotNull(values);
            assertEquals(1, values.size(), "响应头不应重复（旧实现用 add）");
            assertEquals(first, second, "同一请求内多次取用必须稳定");
        }

        @Test
        @DisplayName("过滤器顺序在 body 缓存与可写请求头之后")
        void orderFollowsBodyCacheAndWritableHeaders() {
            assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, filter.getOrder());
            assertTrue(filter.getOrder() > Ordered.HIGHEST_PRECEDENCE,
                    "必须晚于 CachedBodyWebFilter/tracing，才能拿到 tracing 上下文");
        }
    }

    private static TracingContext contextWithTraceId(final String traceId) {
        final TracingContext context = Mockito.mock(TracingContext.class);
        Mockito.when(context.getTraceId()).thenReturn(traceId);
        return context;
    }
}
