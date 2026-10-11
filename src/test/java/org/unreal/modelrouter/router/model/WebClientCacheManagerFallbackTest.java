package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.router.http.WebClientPool;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #266：解析守卫挂在 WebClientPool 创建的客户端上，因此降级分支也必须经池创建，
 * 否则「追踪关闭」的部署形态会退回到没有解析守卫的路径。
 */
@DisplayName("WebClientCacheManager：降级分支仍在出站门闩内")
class WebClientCacheManagerFallbackTest {

    private static final String BASE_URL = "http://10.0.0.5:8000";

    private ApplicationContext originalContext;
    private GenericApplicationContext context;

    @BeforeEach
    void captureContext() {
        originalContext = ApplicationContextProvider.getApplicationContext();
    }

    @AfterEach
    void restoreContext() {
        if (context != null) {
            context.close();
            context = null;
        }
        new ApplicationContextProvider().setApplicationContext(originalContext);
    }

    @Test
    @DisplayName("追踪不可用时降级为 WebClientPool（不自行 new WebClient）")
    void fallback_goesThroughWebClientPool() {
        final WebClientPool pool = mock(WebClientPool.class);
        final WebClient pooled = mock(WebClient.class);
        when(pool.getOrCreate(BASE_URL)).thenReturn(pooled);
        useContext(pool, null);

        final WebClient result = new WebClientCacheManager().getOrCreate(BASE_URL);

        assertSame(pooled, result, "降级分支应返回 WebClientPool 创建的客户端");
        verify(pool).getOrCreate(BASE_URL);
    }

    @Test
    @DisplayName("追踪可用时仍走追踪工厂（原路径不变）")
    void tracingAvailable_stillUsesTracingFactory() {
        final WebClientPool pool = mock(WebClientPool.class);
        final TracingWebClientFactory tracingFactory = mock(TracingWebClientFactory.class);
        final WebClient traced = mock(WebClient.class);
        when(tracingFactory.createTracingWebClient(BASE_URL)).thenReturn(traced);
        useContext(pool, tracingFactory);

        final WebClient result = new WebClientCacheManager().getOrCreate(BASE_URL);

        assertSame(traced, result);
        verify(tracingFactory).createTracingWebClient(BASE_URL);
        verify(pool, never()).getOrCreate(BASE_URL);
    }

    private void useContext(final WebClientPool pool, final TracingWebClientFactory tracingFactory) {
        context = new GenericApplicationContext();
        context.registerBean("webClientPool", WebClientPool.class, () -> pool);
        if (tracingFactory != null) {
            context.registerBean("tracingWebClientFactory", TracingWebClientFactory.class, () -> tracingFactory);
        }
        context.refresh();
        new ApplicationContextProvider().setApplicationContext(context);
    }
}
