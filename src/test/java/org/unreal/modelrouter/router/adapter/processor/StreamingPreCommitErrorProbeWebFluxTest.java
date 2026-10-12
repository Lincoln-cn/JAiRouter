package org.unreal.modelrouter.router.adapter.processor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #257 A 部分取证：**WebFlux 层**（真实 {@code ResponseEntity} + 流式 body）下，两种失败时序客户端看到什么？
 *
 * <p>{@code StreamingPreCommitErrorProbeTest} 测的是 Reactor Netty 层（结论：首块前失败 ⇒ 500 + 空体）。
 * A 部分要改的正是**状态语义**（下游 4xx/5xx 的保真），因此必须在框架层复核同一问题：
 * 若 WebFlux 层其实会提交 200，则 A 的实现形态与收益都要重算。</p>
 *
 * <p>探针控制器与 {@code StreamingRequestProcessor:381-383} 同形：{@code ResponseEntity.ok()
 * + text/event-stream + Flux 响应体}。本类目前**只记录事实**，待确认后再固化成断言。</p>
 */
@DisplayName("#257 A 部分取证：WebFlux 层的失败可见性")
class StreamingPreCommitErrorProbeWebFluxTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.create();

    private static AnnotationConfigApplicationContext context;
    private static DisposableServer server;

    @BeforeAll
    static void startServer() {
        context = new AnnotationConfigApplicationContext();
        context.register(DelegatingWebFluxConfiguration.class);
        context.register(ProbeController.class);
        context.refresh();

        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(new ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build()))
                .bindNow();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.disposeNow();
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("情形 A：首块到达前失败 ⇒ WebFlux 层同样是 500 + 空体（与 Reactor Netty 层一致）")
    void beforeFirstChunk() {
        final Observation observation = observe("/probe/stream?mode=before");

        assertEquals("500", observation.status,
                "WebFlux 不会先提交 200 ⇒ 「A 部分改成按下游状态保真」是可实现的");
        assertEquals("(none)", observation.error, "失败由状态码表达");
        assertEquals("(empty)", observation.body, "现状缺的是错误体与真实原因");
    }

    @Test
    @DisplayName("情形 B：流中失败 ⇒ 200 + 已发块 + 连接中止（状态码已提交，无法再改）")
    void afterFirstChunk() {
        final Observation observation = observe("/probe/stream?mode=after");

        assertEquals("200", observation.status, "首块之后状态码已提交 ⇒ 只能靠 error 帧 + 异常终止（已由 #299 实现）");
        assertTrue(observation.error.contains("PrematureCloseException"), observation.error);
        assertTrue(observation.body.contains("a"), observation.body);
    }

    private Observation observe(final String uriPath) {
        final Observation result = new Observation();
        final StringBuilder received = new StringBuilder();
        try {
            HTTP_CLIENT.get()
                    .uri("http://127.0.0.1:" + server.port() + uriPath)
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

    /** 与生产同形的探针控制器 */
    @RestController
    static class ProbeController {

        @GetMapping(value = "/probe/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Mono<ResponseEntity<Flux<String>>> stream(@RequestParam("mode") final String mode) {
            final Flux<String> body = "before".equals(mode)
                    ? Flux.error(new IllegalStateException("fail-before-first-chunk"))
                    : Flux.concat(
                            Flux.just("data: {\"a\":1}\n\n"),
                            Flux.error(new IllegalStateException("fail-after-first-chunk")));
            return Mono.just(ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body));
        }
    }
}
