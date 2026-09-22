package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.loadbalancer.SelectedInstanceHolder;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #83 复现：单次请求内实例被重复选择。
 *
 * <p>症状：负载均衡监控页「实例分布」数字一次请求跳 +3。根因是同一个请求内
 * {@code ModelServiceRegistry.selectInstance} 被调用多次，而每次调用都会
 * {@code recordSelection} 使计数 +1。</p>
 *
 * <p>本用例聚焦 adapter 内部那两次重复选择：{@code processRequest} 先选择一次实例，
 * 随后 {@code getWebClient} 又选择一次来拼 baseUrl。除了多计一次，这还会让
 * 「实际发请求的 WebClient 目标」与「被记入指标/负载均衡回调的实例」不一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterInstanceSelectionTest {

    @Mock
    private ModelServiceRegistry registry;
    @Mock
    private InstanceSelector instanceSelector;
    @Mock
    private RetryPolicy retryPolicy;
    @Mock
    private AdapterErrorHandler errorHandler;
    @Mock
    private AdapterMetricsRecorder metricsRecorder;
    @Mock
    private AdapterTracingManager tracingManager;
    @Mock
    private ErrorResponseBuilder errorResponseBuilder;
    @Mock
    private CapabilityChecker capabilityChecker;
    @Mock
    private TracingWebClientFactory tracingFactory;
    @Mock
    private WebClient webClient;
    @Mock
    private ServerHttpRequest httpRequest;

    private ModelRouterProperties.ModelInstance instanceA;
    private ModelRouterProperties.ModelInstance instanceB;

    private MockedStatic<ApplicationContextProvider> acpMock;
    private TestAdapter adapter;

    @BeforeEach
    void setUp() {
        instanceA = createInstance("instA", "http://host-a:8080", "/v1/chat/completions");
        instanceB = createInstance("instB", "http://host-b:8080", "/v1/chat/completions");

        acpMock = mockStatic(ApplicationContextProvider.class);
        acpMock.when(() -> ApplicationContextProvider.getBean(TracingWebClientFactory.class))
                .thenReturn(tracingFactory);
        lenient().when(tracingFactory.createTracingWebClient(anyString())).thenReturn(webClient);
        lenient().when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());

        AdapterContext context = new AdapterContext(registry, new ObjectMapper(), null);
        RequestProcessingSupport requestSupport = new RequestProcessingSupport(
                null, null, instanceSelector, null, null, null, null, null);
        ResilienceSupport resilienceSupport = new ResilienceSupport(
                capabilityChecker, errorHandler, retryPolicy, metricsRecorder,
                tracingManager, errorResponseBuilder);

        adapter = new TestAdapter(context, requestSupport, resilienceSupport);

        lenient().when(errorHandler.classifyError(any())).thenReturn("503");
        lenient().when(errorResponseBuilder.buildErrorResponse(any(Throwable.class)))
                .thenAnswer(inv -> Mono.error((Throwable) inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        acpMock.close();
    }

    @Test
    void singleRequestShouldSelectInstanceExactlyOnceAndReuseItForWebClient() {
        // 连续两次选择返回不同实例：用来同时暴露「重复选择」与「WebClient 目标漂移」
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA, instanceB);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        AtomicReference<ModelRouterProperties.ModelInstance> usedInstance = new AtomicReference<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            usedInstance.set(instance);
            return Mono.just(ResponseEntity.ok("{\"ok\":true}"));
        };

        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "qwen3.8-flash", processor);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // 1) 一次请求只应产生一次实例选择（每次选择都会被路由监控计一次数）
        verify(instanceSelector, times(1))
                .selectInstance(any(), anyString(), anyString());

        // 2) WebClient 必须指向真正被使用的那个实例，而不是再选出来的另一个
        assertEquals(instanceA, usedInstance.get(), "处理器应使用首次选中的实例 A");
        verify(tracingFactory, times(1)).createTracingWebClient(instanceA.getBaseUrl());
    }

    @Test
    void shouldReuseGatewaySelectedInstanceWithoutSelectingAgain() {
        // 模拟网关已选好实例：适配器不得再选，否则同一请求会重复计数/重复推进 LB 状态
        SelectedInstanceHolder.set(instanceB);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        AtomicReference<ModelRouterProperties.ModelInstance> usedInstance = new AtomicReference<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            usedInstance.set(instance);
            return Mono.just(ResponseEntity.ok("{\"ok\":true}"));
        };

        try {
            Mono<?> result = adapter.doProcessRequest(
                    new Object(), "Bearer test", httpRequest,
                    ModelServiceRegistry.ServiceType.chat, "qwen3.8-flash", processor);

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();

            verify(instanceSelector, never()).selectInstance(any(), anyString(), anyString());
            assertEquals(instanceB, usedInstance.get(), "处理器应复用网关已选中的实例 B");
            verify(tracingFactory, times(1)).createTracingWebClient(instanceB.getBaseUrl());
        } finally {
            SelectedInstanceHolder.clear();
        }
    }

    private static ModelRouterProperties.ModelInstance createInstance(
            String name, String baseUrl, String path) {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setName(name);
        inst.setBaseUrl(baseUrl);
        inst.setPath(path);
        return inst;
    }

    private static class TestAdapter extends BaseAdapter {

        TestAdapter(AdapterContext context, RequestProcessingSupport requestSupport,
                    ResilienceSupport resilienceSupport) {
            super(context, requestSupport, resilienceSupport);
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public AdapterCapabilities supportCapability() {
            return AdapterCapabilities.builder().chat(true).build();
        }

        @SuppressWarnings("unchecked")
        Mono<?> doProcessRequest(Object request, String auth, ServerHttpRequest httpRequest,
                                 ModelServiceRegistry.ServiceType serviceType, String modelName,
                                 BaseAdapter.RequestProcessor<?> processor) {
            return processRequest(request, auth, httpRequest, serviceType, modelName,
                    (BaseAdapter.RequestProcessor<Object>) processor);
        }
    }
}
