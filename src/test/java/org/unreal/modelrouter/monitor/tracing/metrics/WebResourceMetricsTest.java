package org.unreal.modelrouter.monitor.tracing.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态资源指标与「只做异常」的行为约束（#1156）。
 *
 * <p>守两件事：常规请求不能产生链路；同一客户端在同一路径上不能刷出一条链路（扫描防护）。</p>
 */
class WebResourceMetricsTest {

    private SimpleMeterRegistry registry;
    private WebResourceMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WebResourceMetrics(registry);
    }

    @Test
    void normalRequestOnlyCountsMetric() {
        boolean keep = metrics.record(exchange("/admin/assets/index-a1b2c3.js", HttpStatus.OK, "10.0.0.9"));

        assertFalse(keep, "2xx 不应建链路");
        assertEquals(1.0, registry.get("jairouter_web_requests_total")
            .tag("status_class", "2xx").counter().count());
        assertTrue(registry.find("jairouter_web_errors_total").counters().isEmpty());
    }

    @Test
    void errorRequestIsTraceableOnce() {
        boolean first = metrics.record(exchange("/wp-admin.php", HttpStatus.NOT_FOUND, "203.0.113.7"));

        assertTrue(first, "4xx 首次应建链路");
        assertEquals(1.0, registry.get("jairouter_web_errors_total")
            .tag("status", "404").tag("path_class", "/wp-admin.php").counter().count());
        assertEquals(1.0, registry.get("jairouter_web_errors_by_client_total")
            .counter().count());
    }

    @Test
    void sameClientSamePathWithinWindowIsNotTraceable() {
        ServerWebExchange first = exchange("/wp-admin.php", HttpStatus.NOT_FOUND, "203.0.113.7");
        ServerWebExchange second = exchange("/wp-admin.php", HttpStatus.NOT_FOUND, "203.0.113.7");

        assertTrue(metrics.record(first));
        assertFalse(metrics.record(second), "一分钟内同一路径只留一条链路");
        // 但指标要照常累加——否则扫描量就被隐藏了
        assertEquals(2.0, registry.get("jairouter_web_errors_total")
            .tag("status", "404").tag("path_class", "/wp-admin.php").counter().count());
    }

    @Test
    void staticFileNameIsNormalized() {
        metrics.record(exchange("/admin/assets/index-a1b2c3.js", HttpStatus.NOT_FOUND, "10.0.0.9"));

        assertEquals(1.0, registry.get("jairouter_web_errors_total")
            .tag("path_class", "/admin/assets/:file").counter().count());
    }

    private static ServerWebExchange exchange(String path, HttpStatus status, String clientIp) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path).remoteAddress(new InetSocketAddress(clientIp, 12345)).build());
        exchange.getResponse().setStatusCode(status);
        return exchange;
    }
}
