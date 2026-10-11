package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #266 的端到端还原：URL 校验通过的主机名，在连接期解析到危险地址时必须建连失败。
 *
 * <p>用可控解析器扮演「同一主机名先后解析出不同地址」的 DNS，其余路径（WebClientPool → HttpClient →
 * 解析守卫 → 建连）全部是生产代码，因此这条用例同时验证了守卫确实被接进池中。</p>
 */
@DisplayName("WebClientPool 解析守卫：DNS 重绑定在连接期被拒")
class WebClientPoolDnsRebindingTest {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 5;
    private static final int MAX_CONNECTIONS = 8;
    private static final long PENDING_ACQUIRE_TIMEOUT_MS = 2_000L;
    private static final int MAX_IDLE_TIME_SECONDS = 5;

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    @Test
    @DisplayName("主机名在连接期解析到 169.254.169.254 → 建连被拒")
    void resolvesToMetadataAddress_isRejectedAtConnectTime() throws UnknownHostException {
        final WebClientPool pool = poolResolvingTo(address("169.254.169.254", 80));
        // URL 层校验通过：主机名既非字面量 IP，也不在拦截名单里
        final WebClient client = pool.getOrCreate("http://rebind-test.invalid:8080");

        final Throwable failure = assertThrows(Throwable.class, () -> client.get()
                .uri("/")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10)));

        assertTrue(hasCause(failure, SecurityException.class), () -> "应因解析守卫拒绝建连，实际: " + failure);
    }

    @Test
    @DisplayName("解析到回环地址时经守卫仍可正常出站（不误伤正常流量）")
    void resolvesToLoopback_stillReachesLocalServer() throws UnknownHostException {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.sendString(Mono.just("ok")))
                .bindNow();
        final int port = server.port();

        final WebClientPool pool = poolResolvingTo(address("127.0.0.1", port));
        final WebClient client = pool.getOrCreate("http://guarded-outbound.invalid:" + port);

        final String body = client.get()
                .uri("/")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        assertEquals("ok", body);
    }

    private static WebClientPool poolResolvingTo(final InetSocketAddress target) {
        return new WebClientPool(CONNECT_TIMEOUT_MS, RESPONSE_TIMEOUT_SECONDS, MAX_CONNECTIONS,
                PENDING_ACQUIRE_TIMEOUT_MS, MAX_IDLE_TIME_SECONDS,
                SsrfGuardedAddressResolverGroup.withDelegate(
                        executor -> StubAddressResolvers.fixed(List.of(target))));
    }

    private static InetSocketAddress address(final String literal, final int port) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(literal), port);
    }

    private static boolean hasCause(final Throwable failure, final Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
