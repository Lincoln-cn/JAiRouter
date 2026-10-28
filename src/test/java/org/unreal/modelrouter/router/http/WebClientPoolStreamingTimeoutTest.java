package org.unreal.modelrouter.router.http;

import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #701：SSE 的块间静默不得被传输层 {@code responseTimeout} 提前掐断。
 *
 * <p>{@code reactor-netty} 的 {@code responseTimeout} 约束的是「相邻两次网络读」的间隔，
 * SSE 每个数据块都算一次读 —— 于是 60 秒的默认值会让「块间静默 60 秒以上」的流在传输层直接
 * {@code ReadTimeoutException}，应用层 5 分钟的 idle 看门狗永远等不到。</p>
 *
 * <p>本类用真实 reactor-netty 服务端「延迟 2 秒才发首块」复现：默认变体（1 秒超时）必须失败，
 * 流式变体（30 秒超时）必须成功。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#701 流式变体的传输层超时")
class WebClientPoolStreamingTimeoutTest {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int NORMAL_RESPONSE_TIMEOUT_SECONDS = 1;
    private static final int STREAMING_RESPONSE_TIMEOUT_SECONDS = 30;
    private static final int MAX_CONNECTIONS = 8;
    private static final long PENDING_ACQUIRE_TIMEOUT_MS = 2_000L;
    private static final int MAX_IDLE_TIME_SECONDS = 5;
    private static final Duration FIRST_CHUNK_DELAY = Duration.ofSeconds(2);

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    @Test
    @DisplayName("SSE-001 默认变体在块间静默超过 60 秒等价场景下会被传输层掐断（复现旧行为）")
    void defaultVariantIsKilledByResponseTimeout() {
        final String baseUrl = startSlowSseServer();
        final WebClient client = newPool().getOrCreate(baseUrl);

        final Throwable failure = assertThrows(Throwable.class, () -> readFirstChunk(client));

        assertTrue(hasCause(failure, io.netty.handler.timeout.ReadTimeoutException.class)
                        || failure.toString().contains("ReadTimeout"),
                () -> "默认变体的传输层超时应先触发，实际: " + failure);
    }

    @Test
    @DisplayName("SSE-002 流式变体在同样的静默下正常读到第一个块")
    void streamingVariantSurvivesIdleGap() {
        final String baseUrl = startSlowSseServer();
        final WebClient client = newPool().getOrCreate(baseUrl, WebClientPool.STREAMING_VARIANT, null);

        final String body = readFirstChunk(client);

        assertTrue(body.contains("data:"), "流式变体应读到首块，实际: " + body);
        assertTrue(WebClientPool.isStreamingVariant(WebClientPool.STREAMING_VARIANT));
        assertTrue(WebClientPool.isStreamingVariant(WebClientPool.STREAMING_TRACING_VARIANT));
        assertTrue(!WebClientPool.isStreamingVariant("tracing"), "普通追踪变体不得走流式超时");
    }

    private WebClientPool newPool() {
        return new WebClientPool(CONNECT_TIMEOUT_MS, NORMAL_RESPONSE_TIMEOUT_SECONDS, MAX_CONNECTIONS,
                PENDING_ACQUIRE_TIMEOUT_MS, MAX_IDLE_TIME_SECONDS,
                WebClientPool.DEFAULT_MAX_IN_MEMORY_SIZE_BYTES, STREAMING_RESPONSE_TIMEOUT_SECONDS,
                SsrfGuardedAddressResolverGroup.instance());
    }

    private static String readFirstChunk(final WebClient client) {
        return client.get().uri("/sse")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));
    }

    private String startSlowSseServer() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response
                        .header("Content-Type", "text/event-stream")
                        .sendString(Mono.just("data: {\"delta\":\"hi\"}\n\n")
                                .delaySubscription(FIRST_CHUNK_DELAY)))
                .bindNow();
        return "http://127.0.0.1:" + server.port();
    }

    private static boolean hasCause(final Throwable throwable, final Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
