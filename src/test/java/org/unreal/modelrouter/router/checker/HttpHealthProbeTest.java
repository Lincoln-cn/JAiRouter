package org.unreal.modelrouter.router.checker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 探活的真实服务端用例（v3.5.4，#650）。
 *
 * <p>用 JDK 自带的 {@link HttpServer} 起一个随机端口的假上游，覆盖「200 健康 / 503 不健康 /
 * 连不上不健康」三种判定，避免只断言 mock 调用而漏掉真正要表达的行为。</p>
 */
class HttpHealthProbeTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("HTTP-001: 上游返回 200 时判健康")
    void probeReturnsHealthyFor2xx() throws IOException {
        final String baseUrl = startServer(200);
        final HttpHealthProbe probe = new HttpHealthProbe(2_000);

        final HttpHealthProbe.Result result = probe.probe(baseUrl, "/v1/chat/completions", 2_000);

        assertTrue(result.healthy(), result.detail());
        assertTrue(result.detail().contains("HTTP 200"), result.detail());
    }

    @Test
    @DisplayName("HTTP-002: 上游返回 503 时判不健康")
    void probeReturnsUnhealthyFor5xx() throws IOException {
        final String baseUrl = startServer(503);
        final HttpHealthProbe probe = new HttpHealthProbe(2_000);

        final HttpHealthProbe.Result result = probe.probe(baseUrl, "/v1/chat/completions", 2_000);

        assertFalse(result.healthy(), result.detail());
        assertTrue(result.detail().contains("HTTP 503"), result.detail());
    }

    @Test
    @DisplayName("HTTP-003: 端口不通时判不健康且给出原因")
    void probeReturnsUnhealthyWhenConnectionRefused() throws IOException {
        // 先占用再释放，拿到一个「几乎肯定没人监听」的端口
        final int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final HttpHealthProbe probe = new HttpHealthProbe(500);

        final HttpHealthProbe.Result result = probe.probe("http://127.0.0.1:" + port, "/health", 500);

        assertFalse(result.healthy(), result.detail());
        assertTrue(result.detail().startsWith("HTTP 探活失败"), result.detail());
    }

    @Test
    @DisplayName("HTTP-004: join 处理斜杠拼接")
    void joinNormalizesSlashes() {
        assertTrue(HttpHealthProbe.join("http://host:8080", "/v1/x").toString().endsWith("/v1/x"));
        assertTrue(HttpHealthProbe.join("http://host:8080/", "v1/x").toString().endsWith("/v1/x"));
    }

    private String startServer(final int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            final byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
