package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #700：出站响应体的内存上限。
 *
 * <p>此前 {@code WebClientPool} 只设了 baseUrl 与 connector，没有 {@code ExchangeStrategies}，
 * 于是落到 WebFlux 默认的 256KB —— 大响应（长输出、含 base64 图片、TTS 音频）会以
 * {@code DataBufferLimitException} 失败，而 {@code webclient.yml} 里那行 10MB 落在自承「无消费方」的区段。</p>
 *
 * <p>本类用真实的 reactor-netty 服务端回一段 300KB 文本，断言默认配置下能读、把上限调到 1KB 时
 * 确实是这个异常类型（保证兜底映射（502）匹配得到）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#700 出站响应体内存上限")
class WebClientPoolMemoryLimitTest {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 10;
    private static final int MAX_CONNECTIONS = 8;
    private static final long PENDING_ACQUIRE_TIMEOUT_MS = 2_000L;
    private static final int MAX_IDLE_TIME_SECONDS = 5;

    /** 300KB：高于 WebFlux 默认的 256KB，低于本仓新的 10MB 默认值。 */
    private static final int BODY_BYTES = 300 * 1024;

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    @Test
    @DisplayName("MEM-001 默认上限可读 300KB 响应（修复前 256KB 会抛 DataBufferLimitException）")
    void defaultLimitAllowsLargeBody() {
        final String baseUrl = startServer();
        final WebClientPool pool = new WebClientPool();

        final String body = pool.getOrCreate(baseUrl)
                .get().uri("/large")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        assertEquals(BODY_BYTES, body == null ? -1 : body.length(),
                "默认上限（10MB）应能读完 300KB 响应");
        assertEquals(WebClientPool.DEFAULT_MAX_IN_MEMORY_SIZE_BYTES, 10 * 1024 * 1024,
                "默认值应为 10MB");
    }

    @Test
    @DisplayName("MEM-002 把上限调到 1KB 时抛出的是 DataBufferLimitException")
    void tinyLimitRaisesDataBufferLimitException() {
        final String baseUrl = startServer();
        final WebClientPool pool = new WebClientPool(CONNECT_TIMEOUT_MS, RESPONSE_TIMEOUT_SECONDS, MAX_CONNECTIONS,
                PENDING_ACQUIRE_TIMEOUT_MS, MAX_IDLE_TIME_SECONDS, 1024,
                SsrfGuardedAddressResolverGroup.instance());

        final WebClient client = pool.getOrCreate(baseUrl);
        final Throwable failure = assertThrows(Throwable.class, () -> client.get().uri("/large")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20)));

        assertTrue(hasCause(failure, DataBufferLimitException.class),
                () -> "超上限应是 DataBufferLimitException（全局处理器据此回 502），实际: " + failure);
    }

    private String startServer() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.sendString(
                        Mono.just("x".repeat(BODY_BYTES))))
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
