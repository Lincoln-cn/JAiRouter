package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.monitor.tracing.interceptor.BackendCallTracingInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * WebClientPool 缓存键测试（#699）。
 *
 * <p>缓存键曾拼接配置器的 {@code hashCode()}，而配置器通常是每次调用新建的 lambda，
 * identity hash 每次都不同 ⇒ tracing 热路径每请求都未命中、缓存条目随请求线性增长。</p>
 *
 * <p>本类钉住三条不变量：同一 baseUrl + 同一变体只应有一个实例；不同变体互不覆盖；
 * 条目数触顶后必须有界。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#699 出站 WebClient 缓存键")
class WebClientPoolCacheKeyTest {

    private static final String BASE_URL = "http://localhost:8080";

    private WebClientPool webClientPool;

    @BeforeEach
    void setUp() {
        webClientPool = new WebClientPool();
    }

    @Test
    @DisplayName("KEY-001 同一 baseUrl 的两个新建 lambda 必须复用同一个 WebClient")
    void differentConfiguratorInstancesShareOneClient() {
        // 两次调用各传一个新 lambda：identity hash 必然不同，修复前会产生两个缓存条目
        final WebClient first = webClientPool.getOrCreate(BASE_URL, builder -> builder.defaultHeader("X-A", "1"));
        final WebClient second = webClientPool.getOrCreate(BASE_URL, builder -> builder.defaultHeader("X-A", "1"));

        assertSame(first, second, "同一 baseUrl 的默认变体只应有一个 WebClient 实例");
        assertEquals(1, webClientPool.getStats().size(), "两次调用后缓存条目必须仍是 1 条");
    }

    @Test
    @DisplayName("KEY-002 不同变体名不得互相覆盖")
    void variantKeyKeepsClientsApart() {
        final WebClient defaultVariant = webClientPool.getOrCreate(BASE_URL, null, builder -> builder.defaultHeader("X-A", "1"));
        final WebClient tracingVariant = webClientPool.getOrCreate(BASE_URL, "tracing", builder -> builder.defaultHeader("X-A", "2"));

        assertNotSame(defaultVariant, tracingVariant, "不同变体应有各自的 WebClient");
        assertEquals(2, webClientPool.getStats().size(), "两个变体应占 2 条缓存");

        // 同变体再取一次仍是同一个实例
        assertSame(tracingVariant, webClientPool.getOrCreate(BASE_URL, "tracing", builder -> builder.defaultHeader("X-A", "3")),
                "同一变体的后续调用必须命中缓存");
        assertEquals(2, webClientPool.getStats().size(), "命中缓存不应新增条目");
    }

    @Test
    @DisplayName("KEY-003 追踪工厂连续调用必须复用同一个 WebClient")
    void tracingFactoryReusesOneClient() {
        final TracingWebClientFactory factory = new TracingWebClientFactory(
                mock(BackendCallTracingInterceptor.class), webClientPool);

        final WebClient first = factory.createTracingWebClient(BASE_URL);
        final WebClient second = factory.createTracingWebClient(BASE_URL);

        assertSame(first, second, "追踪客户端在两次调用间必须复用");
        assertEquals(1, webClientPool.getStats().size(), "追踪路径不应每请求新建缓存条目");
    }

    @Test
    @DisplayName("KEY-004 大量不同 baseUrl 时缓存条目必须有界")
    void cacheStaysBounded() {
        final int attempts = WebClientPool.MAX_CACHED_CLIENTS + 44;
        for (int i = 0; i < attempts; i++) {
            webClientPool.getOrCreate("http://127.0.0.1:" + (20000 + i));
        }

        assertTrue(webClientPool.getStats().size() <= WebClientPool.MAX_CACHED_CLIENTS,
                "缓存条目不得超过上限 " + WebClientPool.MAX_CACHED_CLIENTS
                        + "，实际 " + webClientPool.getStats().size());
    }

    @Test
    @DisplayName("KEY-005 等价的 baseUrl 写法应共享同一条目")
    void equivalentBaseUrlsShareOneEntry() {
        final WebClient withoutSlash = webClientPool.getOrCreate(BASE_URL);
        final WebClient withSlash = webClientPool.getOrCreate(BASE_URL + "/");

        assertSame(withoutSlash, withSlash, "规范化后等价的 baseUrl 不应重复建池");
        assertEquals(1, webClientPool.getStats().size());
    }
}
