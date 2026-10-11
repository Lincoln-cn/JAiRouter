package org.unreal.modelrouter.router.adapter.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #257 的取证夹具：流式失败时客户端究竟看到什么 —— 把基线实测结果固化成断言。
 *
 * <p>用 Reactor Netty 直接复现网关的管线形态（先设 200 + {@code text/event-stream}，再以 Flux 作为响应体），
 * 不加载 Spring 上下文。截至 v3.3.0 的实测基线（本类即证据）：</p>
 *
 * <table>
 *   <caption>四种时序的客户端可见性</caption>
 *   <tr><th>时序</th><th>状态码</th><th>客户端异常</th><th>收到的 body</th></tr>
 *   <tr><td>A 首块到达前失败</td><td>500</td><td>无</td><td>空</td></tr>
 *   <tr><td>B 流中失败（不补帧）</td><td>200</td><td>PrematureCloseException</td><td>已发出的块</td></tr>
 *   <tr><td>C error 帧 + 异常终止（#257 的 (b)）</td><td>200</td><td>PrematureCloseException</td><td><b>完整 error 帧</b></td></tr>
 *   <tr><td>D error 帧 + 正常结束（(a)/(c)）</td><td>200</td><td><b>无</b></td><td>error 帧</td></tr>
 * </table>
 *
 * <p>两条关键结论：</p>
 * <ol>
 *   <li>C 证明 <b>error 帧能在连接中止前送达</b> ⇒ (b) 可行，客户端同时拿到可解析的错误体与失败信号；</li>
 *   <li>D 证明 (a)/(c) 在传输层<b>没有任何失败信号</b>（状态 200、无异常）⇒ 只等 {@code [DONE]} 或只取
 *       {@code choices[].delta} 的客户端会把截断读成成功。因此 #257 <b>不采用</b> error 帧 + {@code [DONE]}。</li>
 * </ol>
 */
@DisplayName("#257 取证：流式失败的客户端可见性基线")
class StreamingPreCommitErrorProbeTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.create();

    private static final String ERROR_FRAME =
            "data: {\"error\":{\"message\":\"downstream failed\",\"type\":\"api_error\"}}\n\n";

    @Test
    @DisplayName("A 首块前失败 → 客户端得到 500（状态码已提交为错误，但没有错误体与真实原因）")
    void bodyErrorsBeforeFirstChunk() {
        final Observation observation = observe(Flux.error(new IllegalStateException("fail-before-first-chunk")));

        assertEquals("500", observation.status);
        assertEquals("(none)", observation.error, "此情形应由响应状态表达失败，无须连接异常");
        assertEquals("(empty)", observation.body);
    }

    @Test
    @DisplayName("B 流中失败（不补 error 帧）→ 200 + 已发出的块 + 连接被中止")
    void bodyErrorsAfterFirstChunk() {
        final Observation observation = observe(Flux.concat(
                Flux.just("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"),
                Flux.error(new IllegalStateException("fail-after-first-chunk"))));

        assertEquals("200", observation.status);
        assertTrue(observation.error.contains("PrematureCloseException"), observation.error);
        assertTrue(observation.body.contains("hi"), observation.body);
    }

    @Test
    @DisplayName("C error 帧 + 异常终止（(b)）→ error 帧完整送达，且客户端仍拿到失败信号")
    void errorFrameThenAbnormalTermination() {
        final Observation observation = observe(Flux.concat(
                Flux.just(ERROR_FRAME),
                Flux.error(new IllegalStateException("fail-after-error-frame"))));

        assertEquals("200", observation.status);
        assertTrue(observation.error.contains("PrematureCloseException"), observation.error);
        assertTrue(observation.body.contains("downstream failed"),
                () -> "error 帧应在连接中止前送达，实际 body=" + observation.body);
    }

    @Test
    @DisplayName("D error 帧 + 正常结束（(a)/(c)）→ 传输层没有任何失败信号")
    void errorFrameThenGracefulCompletion() {
        final Observation observation = observe(Flux.just(ERROR_FRAME));

        assertEquals("200", observation.status);
        assertEquals("(none)", observation.error, "正常结束 ⇒ 客户端拿不到任何失败信号");
        assertNotNull(observation.body);
        assertTrue(observation.body.contains("downstream failed"), observation.body);
    }

    private Observation observe(final Flux<String> body) {
        final DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.status(200)
                        .header("Content-Type", "text/event-stream")
                        .sendString(body, StandardCharsets.UTF_8))
                .bindNow();

        final Observation result = new Observation();
        final StringBuilder received = new StringBuilder();
        try {
            HTTP_CLIENT.get()
                    .uri("http://127.0.0.1:" + server.port() + "/")
                    .response((response, receivedBody) -> {
                        result.status = String.valueOf(response.status().code());
                        return receivedBody
                                .asString(StandardCharsets.UTF_8)
                                .doOnNext(received::append)
                                .then();
                    })
                    .blockLast();
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            server.disposeNow();
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
}
