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
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * v2.9.6: 测试 BaseAdapter 请求级故障转移逻辑。
 *
 * 覆盖场景:
 * 1. A 失败 → 重选 B → B 成功（验证 selectInstance 被再次调用、新实例被使用）
 * 2. A 失败 → B 失败 → 重选均在 failedKeys 中 → 回退到当前实例 → 错误传播
 * 3. 单实例服务 → 无可用替代 → 同实例重试 → 错误传播
 * 4. 不可重试错误(4xx) → 不重试、不重选 → 错误立即传播
 * 5. 重选始终返回已失败实例 → 达到 cap → 回退到当前实例（无无限循环）
 *
 * 注意: failover 默认启用，由 RetryPolicy 重试次数控制上限，无需额外开关。
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterFailoverTest {

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
    private ModelRouterProperties.ModelInstance instanceC;

    private MockedStatic<ApplicationContextProvider> acpMock;
    private TestAdapter adapter;

    @BeforeEach
    void setUp() {
        instanceA = createInstance("instA", "http://host-a:8080", "/v1/chat/completions");
        instanceB = createInstance("instB", "http://host-b:8080", "/v1/chat/completions");
        instanceC = createInstance("instC", "http://host-c:8080", "/v1/chat/completions");

        // Mock static ApplicationContextProvider to control WebClient creation
        acpMock = mockStatic(ApplicationContextProvider.class);
        acpMock.when(() -> ApplicationContextProvider.getBean(TracingWebClientFactory.class))
                .thenReturn(tracingFactory);
        lenient().when(tracingFactory.createTracingWebClient(anyString())).thenReturn(webClient);

        // Avoid NPE in IpUtils.getClientIp when reading request headers
        lenient().when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());

        // Build aggregated support objects (matching real constructor signatures)
        AdapterContext context = new AdapterContext(registry, new ObjectMapper(), null);
        RequestProcessingSupport requestSupport = new RequestProcessingSupport(
                null, null, instanceSelector, null, null, null, null, null);
        ResilienceSupport resilienceSupport = new ResilienceSupport(
                capabilityChecker, errorHandler, retryPolicy, metricsRecorder,
                tracingManager, errorResponseBuilder);

        adapter = new TestAdapter(context, requestSupport, resilienceSupport);

        // Common lenient stubs (not exercised in every test)
        lenient().when(errorHandler.classifyError(any())).thenReturn("503");
        lenient().when(retryPolicy.calculateRetryDelay(anyInt())).thenReturn(0L);
        lenient().when(errorResponseBuilder.buildErrorResponse(any(Throwable.class)))
                .thenAnswer(inv -> Mono.error((Throwable) inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        acpMock.close();
    }

    // ================================================================
    // Test 1: retryable failure on A -> reselect B -> success on B
    // ================================================================
    @Test
    void shouldFailoverToInstanceBOnRetryableFailure() {
        // Arrange: instanceSelector always returns A initially
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        // RetryPolicy: allow 1 retry (retryCount 0)
        when(retryPolicy.canRetry(eq(0), any())).thenReturn(true);
        when(retryPolicy.isRetryable(any(ConnectException.class))).thenReturn(true);

        // tryReselectInstance calls registry.selectInstance(st, mn, null) -> B
        when(registry.selectInstance(any(), anyString(), isNull()))
                .thenReturn(instanceB);

        // Processor: fails on A, succeeds on B
        AtomicReference<ModelRouterProperties.ModelInstance> lastInstance = new AtomicReference<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            lastInstance.set(instance);
            if (instance == instanceA) {
                return Mono.error(new ConnectException("Connection refused to A"));
            }
            return Mono.just(ResponseEntity.ok("{\"result\":\"ok\"}"));
        };

        // Act
        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "test-model", processor);

        // Assert
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // The final successful call used instanceB (failover target)
        assertEquals(instanceB, lastInstance.get(),
                "Failover should route the retry to instance B");
        // registry.selectInstance was called from tryReselectInstance
        verify(registry, atLeastOnce()).selectInstance(any(), anyString(), isNull());
    }

    // ================================================================
    // Test 2: A fails -> B fails -> all candidates in failedKeys ->
    //         fallback to current instance -> error propagates
    // ================================================================
    @Test
    void shouldFallbackWhenAllReselectCandidatesAlreadyFailed() {
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        // canRetry: allow retryCount 0 and 1, reject 2
        when(retryPolicy.canRetry(anyInt(), any()))
                .thenAnswer(inv -> (int) inv.getArgument(0) < 2);
        when(retryPolicy.isRetryable(any(ConnectException.class))).thenReturn(true);

        // tryReselectInstance call sequence:
        // Call 1 (after A fails, failedKeys={A}): returns B (not in failedKeys) -> B selected
        // Calls 2-5 (after B fails, failedKeys={A,B}):
        //   i=0: A (in failedKeys), i=1: B (in failedKeys),
        //   i=2: A (in failedKeys), i=3: B (in failedKeys) -> cap reached, returns currentInstance (B)
        when(registry.selectInstance(any(), anyString(), isNull()))
                .thenReturn(instanceB, instanceA, instanceB, instanceA, instanceB);

        List<ModelRouterProperties.ModelInstance> attemptedInstances = new ArrayList<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            attemptedInstances.add(instance);
            return Mono.error(new ConnectException("Connection refused"));
        };

        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "test-model", processor);

        StepVerifier.create(result)
                .expectError(ConnectException.class)
                .verify();

        // Attempt sequence: A (initial), B (failover), B (same-instance retry after cap)
        assertEquals(List.of(instanceA, instanceB, instanceB), attemptedInstances,
                "Should try A, failover to B, then same-instance retry on B after cap");
        // 5 total registry.selectInstance calls (1 from first reselect + 4 from second reselect cap)
        verify(registry, times(5)).selectInstance(any(), anyString(), isNull());
    }

    // ================================================================
    // Test 3: single instance service -> no real reselection possible
    // ================================================================
    @Test
    void shouldRetrySameInstanceWhenNoAlternativeAvailable() {
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        // Allow retryCount 0 and 1, reject 2
        when(retryPolicy.canRetry(anyInt(), any()))
                .thenAnswer(inv -> (int) inv.getArgument(0) < 2);
        when(retryPolicy.isRetryable(any(ConnectException.class))).thenReturn(true);

        // Single instance: registry always returns A (which is already in failedKeys)
        // Each tryReselectInstance loops failedKeys.size()+2 times, all returning A (in failedKeys)
        when(registry.selectInstance(any(), anyString(), isNull()))
                .thenReturn(instanceA);

        List<ModelRouterProperties.ModelInstance> attemptedInstances = new ArrayList<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            attemptedInstances.add(instance);
            return Mono.error(new ConnectException("Connection refused"));
        };

        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "test-model", processor);

        StepVerifier.create(result)
                .expectError(ConnectException.class)
                .verify();

        // All attempts use the same instance (no failover possible)
        assertTrue(attemptedInstances.stream().allMatch(i -> i == instanceA),
                "All attempts should use the same single instance A");
        assertEquals(3, attemptedInstances.size(),
                "Initial attempt + 2 retries (maxRetries=2 for chat)");

        // Each tryReselectInstance loops failedKeys.size()+2 times:
        // After retry 0: failedKeys={A}, loops 3 times
        // After retry 1: failedKeys={A}, loops 3 times
        // Total: 6 registry.selectInstance calls
        verify(registry, times(6)).selectInstance(any(), anyString(), isNull());
    }

    // ================================================================
    // Test 4: non-retryable error (isRetryable=false) ->
    //         no retry, no reselect, error propagates
    // ================================================================
    @Test
    void shouldNotRetryOrReselectOnNonRetryableError() {
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        // canRetry returns true, but isRetryable returns false
        when(retryPolicy.canRetry(eq(0), any())).thenReturn(true);
        when(retryPolicy.isRetryable(any(IllegalArgumentException.class))).thenReturn(false);

        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) ->
                Mono.error(new IllegalArgumentException("Bad request: 4xx"));

        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "test-model", processor);

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

        // No reselection should have been attempted
        verify(registry, never()).selectInstance(any(), anyString(), isNull());
    }

    // ================================================================
    // Test 5: reselect repeatedly returns already-failed instance ->
    //         cap reached -> fallback to current instance (no infinite loop)
    // ================================================================
    @Test
    void shouldStopReselectAfterCapAndFallbackToCurrentInstance() {
        when(instanceSelector.selectInstance(any(), anyString(), anyString()))
                .thenReturn(instanceA);
        when(instanceSelector.getModelPath(any(), anyString()))
                .thenReturn("/v1/chat/completions");

        // Allow retryCount 0 and 1, reject 2
        when(retryPolicy.canRetry(anyInt(), any()))
                .thenAnswer(inv -> (int) inv.getArgument(0) < 2);
        when(retryPolicy.isRetryable(any(ConnectException.class))).thenReturn(true);

        // Scenario: A fails. tryReselectInstance with failedKeys={A}, maxAttempts=3.
        // Registry returns C (not in failedKeys) on 3rd attempt.
        // Then C fails. tryReselectInstance with failedKeys={A,C}, maxAttempts=4.
        // Registry returns A (failed), C (failed), A (failed), C (failed) -> cap reached.
        when(registry.selectInstance(any(), anyString(), isNull()))
                // First tryReselect (after A fails, failedKeys={A}, maxAttempts=3):
                //   i=0: A (in failedKeys), i=1: A (in failedKeys), i=2: C (not in failedKeys) -> C
                .thenReturn(instanceA, instanceA, instanceC)
                // Second tryReselect (after C fails, failedKeys={A,C}, maxAttempts=4):
                //   i=0: A (failed), i=1: C (failed), i=2: A (failed), i=3: C (failed) -> cap
                .thenReturn(instanceA, instanceC, instanceA, instanceC);

        List<ModelRouterProperties.ModelInstance> attemptedInstances = new ArrayList<>();
        BaseAdapter.RequestProcessor<Object> processor = (req, auth, client, path, instance, st) -> {
            attemptedInstances.add(instance);
            return Mono.error(new ConnectException("Connection refused"));
        };

        Mono<?> result = adapter.doProcessRequest(
                new Object(), "Bearer test", httpRequest,
                ModelServiceRegistry.ServiceType.chat, "test-model", processor);

        StepVerifier.create(result)
                .expectError(ConnectException.class)
                .verify();

        // Attempt sequence: A (initial), C (failover), C (same-instance retry after cap)
        assertEquals(List.of(instanceA, instanceC, instanceC), attemptedInstances,
                "Should try A, failover to C, then same-instance retry on C after cap reached");
        // 3 + 4 = 7 total registry.selectInstance calls
        verify(registry, times(7)).selectInstance(any(), anyString(), isNull());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static ModelRouterProperties.ModelInstance createInstance(
            String name, String baseUrl, String path) {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setName(name);
        inst.setBaseUrl(baseUrl);
        inst.setPath(path);
        return inst;
    }

    /**
     * TestAdapter: exposes processRequest for failover testing.
     */
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

        /**
         * Public entry point that delegates to the protected processRequest template method.
         */
        @SuppressWarnings("unchecked")
        Mono<?> doProcessRequest(Object request, String auth, ServerHttpRequest httpRequest,
                                 ModelServiceRegistry.ServiceType serviceType, String modelName,
                                 BaseAdapter.RequestProcessor<?> processor) {
            return processRequest(request, auth, httpRequest, serviceType, modelName,
                    (BaseAdapter.RequestProcessor<Object>) processor);
        }
    }
}
