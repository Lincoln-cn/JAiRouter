package org.unreal.modelrouter.router.http;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #947：出站请求指标。
 *
 * <p>此前 {@link WebClientPool} 只统计 WebClient 缓存命中率，没有任何「在途请求数 / 首字节延迟」，
 * 上游或连接池饱和时只能看到 HTTP 请求整体变慢，无法定位到出站这一层。</p>
 */
@DisplayName("WebClientPool：出站请求指标")
class WebClientPoolOutboundMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private DisposableServer server;
    private WebClientPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.dispose();
            pool = null;
        }
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    @Test
    @DisplayName("请求进行中在途数为 1，结束后归零，且计时器有记录")
    void recordsMetricsForSuccessfulRequest() throws Exception {
        // 上游故意慢 400ms：让「在途」这个状态可被观察到，否则断言只能看到 0 → 0，
        // 看不出计数到底有没有递增过（空转断言）。
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) ->
                        response.sendString(Mono.just("ok").delayElement(Duration.ofMillis(400))))
                .bindNow();
        final String baseUrl = "http://127.0.0.1:" + server.port();

        pool = new WebClientPool(5000, 10, 8, 3000, 30, 1_048_576, 30,
                SsrfGuardedAddressResolverGroup.instance(), registry);

        final Gauge inflight = registry.find("jairouter.outbound.requests.inflight").gauge();
        assertNotNull(inflight, "在途数 gauge 应已注册");
        assertEquals(0.0, inflight.value(), 0.001, "空闲时应为 0");

        final CompletableFuture<String> future = pool.getOrCreate(baseUrl).get().uri("/ping")
                .retrieve().bodyToMono(String.class)
                .toFuture();

        awaitInflight(inflight, 1.0);
        assertEquals("ok", future.get(10, TimeUnit.SECONDS));

        final Timer timer = registry.find("jairouter.outbound.request.duration").timer();
        assertNotNull(timer, "首字节延迟计时器应已注册");
        assertEquals(1L, timer.count(), "一次请求应记录一条样本");
        // #947：reactor 内建池指标（active / idle / pending / max）注册在 Metrics.globalRegistry，
        // Spring Boot 默认 use-global-registry=true 会桥接到 prometheus。这里断言「确实注册了、且归属本池」，
        // 防止将来有人把 metrics(true) 删掉而指标静默消失。
        final boolean poolMetricsRegistered = io.micrometer.core.instrument.Metrics.globalRegistry
                .find("reactor.netty.connection.provider.pending.connections")
                .tag("name", "jairouter-outbound")
                .meters().stream().findAny().isPresent();
        assertTrue(poolMetricsRegistered, "连接池指标应注册到全局 registry（name=jairouter-outbound）");
        assertEquals(0.0, inflight.value(), 0.001, "请求结束后在途数必须归零");
    }

    /** 轮询等待在途数达到期望值（最多 3 秒），避免用固定 sleep 造成偶发失败。 */
    private static void awaitInflight(final Gauge gauge, final double expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Math.abs(gauge.value() - expected) < 0.001) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, gauge.value(), 0.001, "3 秒内未观察到期望的在途数");
    }

    @Test
    @DisplayName("请求失败时在途数同样归零（doFinally 覆盖错误路径）")
    void decrementsInflightOnError() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.status(500).sendString(Mono.just("boom")))
                .bindNow();
        final String baseUrl = "http://127.0.0.1:" + server.port();

        pool = new WebClientPool(2000, 10, 8, 3000, 30, 1_048_576, 30,
                SsrfGuardedAddressResolverGroup.instance(), registry);

        try {
            pool.getOrCreate(baseUrl).get().uri("/boom")
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
        } catch (RuntimeException expected) {
            // 5xx 由 retrieve() 转成异常，本用例只关心计数与计时器
        }

        final Gauge inflight = registry.find("jairouter.outbound.requests.inflight").gauge();
        assertNotNull(inflight);
        assertEquals(0.0, inflight.value(), 0.001, "失败路径不得让在途数泄漏");

        final Timer timer = registry.find("jairouter.outbound.request.duration").timer();
        assertNotNull(timer, "失败请求也应记录耗时样本");
        assertEquals(1L, timer.count());
    }
}
