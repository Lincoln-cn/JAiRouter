package org.unreal.modelrouter.router.adapter.processor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.DelegatingWebFluxConfiguration;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;

import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #304（流式错误路径）第一步的端到端夹具：<b>让生产代码进链路</b>。
 *
 * <p>与既有两类探针的差别（这正是 #304 申报的缺口）：</p>
 * <ul>
 *   <li>{@code StreamingPreCommitErrorProbeTest} = Reactor Netty 层，<b>手搭管线</b>，不含生产类；</li>
 *   <li>{@code StreamingPreCommitErrorProbeWebFluxTest} = WebFlux 层，但控制器是「与生产<b>同形</b>」的探针，
 *       仍然不是生产类；</li>
 *   <li><b>本类</b> = 真实可控下游（HTTP）+ <b>生产 {@link StreamingRequestProcessor}</b>（直接 {@code new}：
 *       它的可选依赖全是 {@code @Autowired(required = false)}，缺省即走放宽路径）+ 真实 Netty 客户端观测。</li>
 * </ul>
 *
 * <p>因此它回答的问题是：#257 的四种时序基线（见 {@code StreamingPreCommitErrorProbeTest} 的类注释表格），
 * 在<b>生产处理器 + 真实下游失败</b>这条链路上是否仍然成立。</p>
 *
 * <p><b>本轮实测结果（2026-10-14，本类即证据）——与传输层基线逐字一致</b>：</p>
 * <table>
 *   <caption>生产链路上的客户端可见性</caption>
 *   <tr><th>时序</th><th>状态码</th><th>客户端异常</th><th>收到的 body</th><th>与基线对比</th></tr>
 *   <tr><td>正常流</td><td>200</td><td>无</td><td>两块 + {@code [DONE]}</td><td>—（对照）</td></tr>
 *   <tr><td>首块前失败</td><td>500</td><td>无</td><td>空</td><td><b>与基线 A 一致</b></td></tr>
 *   <tr><td>流中失败</td><td>200</td><td>{@code PrematureCloseException}</td><td>仅已发出的首块（无 {@code [DONE]}）</td><td><b>与基线 B 一致</b></td></tr>
 * </table>
 * <p>⇒ 结论：#257 抓到的两条语义<b>不是探针形态的产物</b>；生产 {@code StreamingRequestProcessor}
 * 在真实下游失败下给出同样的客户端可见结果（含「首块前失败只给 500、不带错误体与真实原因」这一现状）。</p>
 *
 * <p><b>覆盖边界（如实申报）</b>：不含完整 Spring 应用（无鉴权层、无实例选择与配额链路）、
 * 不含控制台前端；下游失败形态为「HTTP 500」与「发一块后异常中止」两种。
 * 完整运行态（真起进程 + 真实客户端 SDK）仍属 #304 未完成部分。</p>
 */
@DisplayName("#304 第一步：生产流式处理器 + 可控下游的失败可见性")
class StreamingErrorPathRuntimeTest {

    private static final HttpClient CLIENT = HttpClient.create();

    /** 生产处理器：唯一必需依赖是 ResponseTransformer（无参构造），其余可选依赖留空。 */
    private static final StreamingRequestProcessor PROCESSOR =
            new StreamingRequestProcessor(new ResponseTransformer());

    private static DisposableServer downstream;
    private static AnnotationConfigApplicationContext gatewayContext;
    private static DisposableServer gateway;

    @BeforeAll
    static void startDownstreamAndGateway() {
        downstream = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(StreamingErrorPathRuntimeTest::handleDownstream)
                .bindNow();

        gatewayContext = new AnnotationConfigApplicationContext();
        gatewayContext.register(DelegatingWebFluxConfiguration.class);
        gatewayContext.register(GatewayController.class);
        gatewayContext.refresh();

        gateway = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(new ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(gatewayContext).build()))
                .bindNow();
    }

    @AfterAll
    static void stop() {
        if (gateway != null) {
            gateway.disposeNow();
        }
        if (gatewayContext != null) {
            gatewayContext.close();
        }
        if (downstream != null) {
            downstream.disposeNow();
        }
    }

    /**
     * 可控下游：三种形态，用路径选择（无需额外状态）。
     * <ul>
     *   <li>{@code /ok} —— 正常两块 + {@code [DONE]}；</li>
     *   <li>{@code /fail-before} —— 直接 500（首块之前失败）；</li>
     *   <li>{@code /fail-mid} —— 先发一块，再以异常中止（流中失败）。</li>
     * </ul>
     */
    private static reactor.core.publisher.Mono<Void> handleDownstream(
            final HttpServerRequest request, final HttpServerResponse response) {
        final String uri = request.uri();
        if (uri.startsWith("/fail-before")) {
            return response.status(500)
                    .header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"error\":{\"message\":\"downstream exploded\"}}"))
                    .then();
        }
        response.header("Content-Type", "text/event-stream");
        if (uri.startsWith("/fail-mid")) {
            return response.sendString(Flux.concat(
                    Flux.just("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"),
                    Flux.error(new IllegalStateException("downstream died mid-stream")))).then();
        }
        return response.sendString(Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n",
                "data: [DONE]\n\n")).then();
    }

    @Test
    @DisplayName("对照（正常流）：生产处理器返回 200 + SSE，客户端读到全部块")
    void happyPath() {
        final Observation observation = observe("/gateway/stream?mode=ok");
        System.out.println("[#304 观测] mode=ok    " + observation);

        assertEquals("200", observation.status);
        assertEquals("(none)", observation.error, "正常流不应有客户端异常");
        assertTrue(observation.body.contains("hello"), observation.body);
        assertTrue(observation.body.contains("[DONE]"), observation.body);
    }

    @Test
    @DisplayName("首块前失败：客户端得到 500 + 空体（与 #257 基线 A 逐字一致）")
    void failBeforeFirstChunk() {
        final Observation observation = observe("/gateway/stream?mode=before");
        System.out.println("[#304 观测] mode=before " + observation);

        assertEquals("500", observation.status,
                "首块之前失败时状态码未被提交为 200 ⇐「按下游状态保真」在生产链路上成立：" + observation);
        assertEquals("(none)", observation.error, "失败由状态码表达，而不是连接被中止");
        assertEquals("(empty)", observation.body,
                "现状 = 没有错误体、也没有真实原因（这正是 #257 A 部分想补的东西；改动后本断言会红）");
    }

    @Test
    @DisplayName("流中失败：200 + 已发块 + 连接中止，且没有 [DONE]（与基线 B 逐字一致）")
    void failMidStream() {
        final Observation observation = observe("/gateway/stream?mode=mid");
        System.out.println("[#304 观测] mode=mid    " + observation);

        assertEquals("200", observation.status, "首块之后状态码已提交，无法再改成 5xx：" + observation);
        assertTrue(observation.error.contains("PrematureCloseException"),
                "客户端必须拿到失败信号（连接中止），不能静默结束：" + observation);
        assertTrue(observation.body.contains("partial"),
                "首块已送达（否则与基线 B 不符）：" + observation);
        assertFalse(observation.body.contains("[DONE]"),
                "截断的流里没有 [DONE] ⇒ 只等 [DONE] 或只读 choices[].delta 的客户端会把截断读成成功："
                        + observation);
    }

    // ------------------------------------------------------------------ 观测

    private Observation observe(final String uriPath) {
        final Observation result = new Observation();
        final StringBuilder received = new StringBuilder();
        try {
            CLIENT.get()
                    .uri("http://127.0.0.1:" + gateway.port() + uriPath)
                    .response((response, body) -> {
                        result.status = String.valueOf(response.status().code());
                        return body.asString(StandardCharsets.UTF_8)
                                .doOnNext(received::append)
                                .then();
                    })
                    .blockLast();
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (received.length() > 0) {
            result.body = received.toString().replace("\n", "\\n");
        }
        return result;
    }

    private static final class Observation {
        private String status = "(none)";
        private String error = "(none)";
        private String body = "(empty)";

        @Override
        public String toString() {
            return "status=" + status + " | error=" + error + " | body=" + body;
        }
    }

    // -------------------------------------------------------- 被观测的网关侧

    /**
     * 网关侧控制器：把请求交给 <b>生产</b> {@link StreamingRequestProcessor}，并原样返回它给出的响应实体。
     * 请求经真实 HTTP 转发到可控下游。
     */
    @RestController
    static class GatewayController {

        @GetMapping(value = "/gateway/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Mono<? extends ResponseEntity<?>> stream(@RequestParam("mode") final String mode) {
            final String downstreamPath = switch (mode) {
                case "before" -> "/fail-before";
                case "mid" -> "/fail-mid";
                default -> "/ok";
            };
            final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName("runtime-probe-instance");
            instance.setBaseUrl("http://127.0.0.1:" + downstream.port());
            instance.setPath(downstreamPath);

            final org.springframework.web.reactive.function.client.WebClient webClient =
                    org.springframework.web.reactive.function.client.WebClient.builder()
                            .baseUrl("http://127.0.0.1:" + downstream.port())
                            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                                    reactor.netty.http.client.HttpClient.create()))
                            .build();

            final ServerHttpRequest httpRequest = MockServerHttpRequest.get("/gateway/stream").build();

            return PROCESSOR.processStreamingRequest(
                    "{\"model\":\"probe\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                    null,
                    webClient,
                    downstreamPath,
                    instance,
                    ModelServiceRegistry.ServiceType.chat,
                    "normal",
                    PROCESSOR.getDefaultChunkTransformer("normal"),
                    httpRequest);
        }
    }
}
