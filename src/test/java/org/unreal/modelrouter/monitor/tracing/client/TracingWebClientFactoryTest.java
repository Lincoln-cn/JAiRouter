package org.unreal.modelrouter.monitor.tracing.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.monitor.tracing.interceptor.BackendCallTracingInterceptor;
import org.unreal.modelrouter.router.http.WebClientPool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TracingWebClientFactory 单元测试 - v2.7.0
 * 
 * 测试目标：
 * - 验证 WebClientPool 集成
 * - 验证追踪拦截器注入
 */
@DisplayName("TracingWebClientFactory v2.7.0 测试")
@ExtendWith(MockitoExtension.class)
class TracingWebClientFactoryTest {

    @Mock
    private BackendCallTracingInterceptor tracingInterceptor;

    @Mock
    private WebClientPool webClientPool;

    @Mock
    private WebClient mockWebClient;

    private TracingWebClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TracingWebClientFactory(tracingInterceptor, webClientPool);
    }

    @Test
    @DisplayName("测试 1: createTracingWebClient 应使用 WebClientPool")
    void createTracingWebClient_shouldUsePool() {
        String baseUrl = "http://localhost:8080";
        when(webClientPool.getOrCreate(eq(baseUrl), any())).thenReturn(mockWebClient);

        WebClient result = factory.createTracingWebClient(baseUrl);

        verify(webClientPool).getOrCreate(eq(baseUrl), any());
        assertSame(mockWebClient, result);
    }

    @Test
    @DisplayName("测试 2: 相同 baseUrl 多次调用应返回缓存实例")
    void createTracingWebClient_sameUrl_shouldReturnCachedInstance() {
        String baseUrl = "http://localhost:8080";
        when(webClientPool.getOrCreate(eq(baseUrl), any())).thenReturn(mockWebClient);

        WebClient result1 = factory.createTracingWebClient(baseUrl);
        WebClient result2 = factory.createTracingWebClient(baseUrl);

        assertSame(result1, result2);
        verify(webClientPool, times(2)).getOrCreate(eq(baseUrl), any());
    }

    @Test
    @DisplayName("测试 3: addTracingToWebClient 应添加过滤器")
    void addTracingToWebClient_shouldAddFilter() {
        WebClient mockInputClient = mock(WebClient.class);
        WebClient.Builder mockBuilder = mock(WebClient.Builder.class);
        WebClient mutatedClient = mock(WebClient.class);

        when(mockInputClient.mutate()).thenReturn(mockBuilder);
        when(mockBuilder.filter(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mutatedClient);

        WebClient result = factory.addTracingToWebClient(mockInputClient);

        verify(mockBuilder).filter(tracingInterceptor);
        assertSame(mutatedClient, result);
    }
}
