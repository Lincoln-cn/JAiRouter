package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BaseAdapter 扩展测试
 *
 * 目标覆盖率：从 7% 提升到 30%
 * 测试重点：核心方法的正常/异常/边界情况
 *
 * @since v2.9.x
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterExtendedTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ModelCallStatsRepository statsRepository;

    @Mock
    private ServerHttpRequest httpRequest;

    @Mock
    private InstanceSelector instanceSelector;

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private CapabilityChecker capabilityChecker;

    @Mock
    private FallbackStrategy<ResponseEntity<?>> fallbackStrategy;

    @Mock
    private CacheFallbackStrategy cacheFallbackStrategy;

    private ObjectMapper objectMapper;
    private AdapterErrorHandler errorHandler;
    private RetryPolicy retryPolicy;
    private TestAdapter testAdapter;
    private ModelRouterProperties.ModelInstance testInstance;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new AdapterErrorHandler();
        retryPolicy = new RetryPolicy();

        // 创建测试实例
        testInstance = new ModelRouterProperties.ModelInstance();
        testInstance.setName("test-instance");
        testInstance.setInstanceId("test-id-001");
        testInstance.setBaseUrl("http://localhost:8081");
        testInstance.setWeight(100);

        // 创建测试适配器
        testAdapter = new TestAdapter(
                registry,
                metricsCollector,
                objectMapper,
                statsRepository,
                instanceSelector,
                responseTransformer,
                capabilityChecker,
                errorHandler,
                retryPolicy
        );
    }

    // ========== 实例选择相关测试 ==========

    @Nested
    @DisplayName("selectInstance 测试")
    class SelectInstanceTests {

        @Test
        @DisplayName("正常选择实例")
        void shouldSelectInstanceSuccessfully() {
            // Given
            ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
            String modelName = "gpt-4";
            String clientIp = "192.168.1.1";

            when(instanceSelector.selectInstance(serviceType, modelName, clientIp))
                    .thenReturn(testInstance);

            // When
            ModelRouterProperties.ModelInstance result = testAdapter.selectInstancePublic(serviceType, modelName, clientIp);

            // Then
            assertNotNull(result);
            assertEquals("test-instance", result.getName());
            verify(instanceSelector).selectInstance(serviceType, modelName, clientIp);
        }

        @Test
        @DisplayName("无可用实例返回 null")
        void shouldReturnNullWhenNoInstanceAvailable() {
            // Given
            when(instanceSelector.selectInstance(any(), any(), any()))
                    .thenReturn(null);

            // When
            ModelRouterProperties.ModelInstance result = testAdapter.selectInstancePublic(
                    ModelServiceRegistry.ServiceType.chat, "model", "ip");

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getModelPath 测试")
    class GetModelPathTests {

        @Test
        @DisplayName("获取模型路径")
        void shouldGetModelPathSuccessfully() {
            // Given
            String expectedPath = "/v1/chat/completions";
            when(instanceSelector.getModelPath(ModelServiceRegistry.ServiceType.chat, "gpt-4"))
                    .thenReturn(expectedPath);

            // When
            String path = testAdapter.getModelPathPublic(ModelServiceRegistry.ServiceType.chat, "gpt-4");

            // Then
            assertEquals(expectedPath, path);
        }
    }

    // ========== 模型名称提取测试 ==========

    @Nested
    @DisplayName("getModelNameFromRequest 测试")
    class GetModelNameFromRequestTests {

        @Test
        @DisplayName("从 ChatDTO.Request 提取模型名称")
        void shouldExtractModelNameFromChatRequest() {
            // Given
            ChatDTO.Request request = createChatRequest("gpt-4", false);

            // When
            String modelName = testAdapter.getModelNameFromRequestPublic(request);

            // Then
            assertEquals("gpt-4", modelName);
        }

        @Test
        @DisplayName("从 EmbeddingDTO.Request 提取模型名称")
        void shouldExtractModelNameFromEmbeddingRequest() {
            // Given
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "text-embedding-ada-002",
                    "test input",
                    null,
                    null,
                    null,
                    null
            );

            // When
            String modelName = testAdapter.getModelNameFromRequestPublic(request);

            // Then
            assertEquals("text-embedding-ada-002", modelName);
        }

        @Test
        @DisplayName("null 请求返回 unknown")
        void shouldReturnUnknownForNullRequest() {
            // When
            String modelName = testAdapter.getModelNameFromRequestPublic(null);

            // Then
            assertEquals("unknown", modelName);
        }

        @Test
        @DisplayName("无 model 字段的请求返回 unknown")
        void shouldReturnUnknownForRequestWithoutModel() {
            // Given - 使用一个没有 model 方法的对象
            Object request = new Object();

            // When
            String modelName = testAdapter.getModelNameFromRequestPublic(request);

            // Then
            assertEquals("unknown", modelName);
        }
    }

    // ========== 服务类型提取测试 ==========

    @Nested
    @DisplayName("getServiceTypeFromRequest 测试")
    class GetServiceTypeFromRequestTests {

        @Test
        @DisplayName("从 ChatDTO.Request 提取服务类型")
        void shouldExtractServiceTypeFromChatRequest() {
            // Given
            ChatDTO.Request request = createChatRequest("gpt-4", false);

            // When
            String serviceType = testAdapter.getServiceTypeFromRequestPublic(request);

            // Then
            assertEquals("chat", serviceType);
        }

        @Test
        @DisplayName("从 EmbeddingDTO.Request 提取服务类型")
        void shouldExtractServiceTypeFromEmbeddingRequest() {
            // Given
            EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                    "embedding-model", "input", null, null, null, null
            );

            // When
            String serviceType = testAdapter.getServiceTypeFromRequestPublic(request);

            // Then
            assertEquals("embedding", serviceType);
        }

        @Test
        @DisplayName("从 RerankDTO.Request 提取服务类型")
        void shouldExtractServiceTypeFromRerankRequest() {
            // Given
            RerankDTO.Request request = new RerankDTO.Request(
                    "rerank-model",
                    "query",
                    Arrays.asList("doc1", "doc2"),
                    null,
                    null,
                    null
            );

            // When
            String serviceType = testAdapter.getServiceTypeFromRequestPublic(request);

            // Then
            assertEquals("rerank", serviceType);
        }

        @Test
        @DisplayName("从 TtsDTO.Request 提取服务类型")
        void shouldExtractServiceTypeFromTtsRequest() {
            // Given
            TtsDTO.Request request = new TtsDTO.Request(
                    "tts-model", "text", null, null, null
            );

            // When
            String serviceType = testAdapter.getServiceTypeFromRequestPublic(request);

            // Then
            assertEquals("tts", serviceType);
        }

        @Test
        @DisplayName("null 请求返回 unknown")
        void shouldReturnUnknownForNullRequest() {
            // When
            String serviceType = testAdapter.getServiceTypeFromRequestPublic(null);

            // Then
            assertEquals("unknown", serviceType);
        }
    }

    // ========== 重试相关测试 ==========

    @Nested
    @DisplayName("getMaxRetries 测试")
    class GetMaxRetriesTests {

        @Test
        @DisplayName("聊天服务重试次数为 2")
        void shouldReturnTwoForChatService() {
            // When
            int maxRetries = testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.chat);

            // Then
            assertEquals(2, maxRetries);
        }

        @Test
        @DisplayName("嵌入服务重试次数为 2")
        void shouldReturnTwoForEmbeddingService() {
            // When
            int maxRetries = testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.embedding);

            // Then
            assertEquals(2, maxRetries);
        }

        @Test
        @DisplayName("重排序服务重试次数为 1")
        void shouldReturnOneForRerankService() {
            // When
            int maxRetries = testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.rerank);

            // Then
            assertEquals(1, maxRetries);
        }

        @Test
        @DisplayName("TTS/STT 服务重试次数为 1")
        void shouldReturnOneForTtsAndSttServices() {
            assertEquals(1, testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.tts));
            assertEquals(1, testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.stt));
        }

        @Test
        @DisplayName("图像服务重试次数为 1")
        void shouldReturnOneForImageServices() {
            assertEquals(1, testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.imgGen));
            assertEquals(1, testAdapter.getMaxRetriesPublic(ModelServiceRegistry.ServiceType.imgEdit));
        }
    }

    @Nested
    @DisplayName("calculateRetryDelay 测试")
    class CalculateRetryDelayTests {

        @Test
        @DisplayName("第一次重试延迟为 1000ms")
        void shouldCalculateFirstRetryDelay() {
            // When
            long delay = testAdapter.calculateRetryDelayPublic(0);

            // Then
            assertEquals(1000, delay);
        }

        @Test
        @DisplayName("第二次重试延迟为 2000ms（指数退避）")
        void shouldCalculateSecondRetryDelay() {
            // When
            long delay = testAdapter.calculateRetryDelayPublic(1);

            // Then
            assertEquals(2000, delay);
        }

        @Test
        @DisplayName("第三次重试延迟为 4000ms")
        void shouldCalculateThirdRetryDelay() {
            // When
            long delay = testAdapter.calculateRetryDelayPublic(2);

            // Then
            assertEquals(4000, delay);
        }

        @Test
        @DisplayName("最大延迟不超过 10000ms")
        void shouldCapDelayAtMaximum() {
            // When
            long delay = testAdapter.calculateRetryDelayPublic(10);

            // Then
            assertEquals(10000, delay);
        }
    }

    // ========== 请求大小计算测试 ==========

    @Nested
    @DisplayName("calculateRequestSize 测试")
    class CalculateRequestSizeTests {

        @Test
        @DisplayName("计算字符串请求大小")
        void shouldCalculateStringRequestSize() {
            // Given
            String request = "test request content";

            // When
            long size = testAdapter.calculateRequestSizePublic(request);

            // Then
            assertEquals(request.getBytes().length, size);
        }

        @Test
        @DisplayName("null 请求返回 0")
        void shouldReturnZeroForNullRequest() {
            // When
            long size = testAdapter.calculateRequestSizePublic(null);

            // Then
            assertEquals(0, size);
        }

        @Test
        @DisplayName("空字符串请求返回 0")
        void shouldReturnZeroForEmptyString() {
            // When
            long size = testAdapter.calculateRequestSizePublic("");

            // Then
            assertEquals(0, size);
        }

        @Test
        @DisplayName("计算对象请求大小")
        void shouldCalculateObjectRequestSize() {
            // Given
            Object request = new HashMap<String, String>();

            // When
            long size = testAdapter.calculateRequestSizePublic(request);

            // Then
            assertTrue(size >= 0);
        }
    }

    // ========== 请求转换测试 ==========

    @Nested
    @DisplayName("transformRequest 测试")
    class TransformRequestTests {

        @Test
        @DisplayName("委托给 ResponseTransformer")
        void shouldDelegateToResponseTransformer() {
            // Given
            Object originalRequest = new Object();
            Object transformedRequest = new Object();
            when(responseTransformer.transformRequest(originalRequest, "test"))
                    .thenReturn(transformedRequest);

            // When
            Object result = testAdapter.transformRequestPublic(originalRequest, "test");

            // Then
            assertEquals(transformedRequest, result);
            verify(responseTransformer).transformRequest(originalRequest, "test");
        }
    }

    @Nested
    @DisplayName("transformResponse 测试")
    class TransformResponseTests {

        @Test
        @DisplayName("委托给 ResponseTransformer")
        void shouldDelegateToResponseTransformer() {
            // Given
            Object originalResponse = Map.of("key", "value");
            Object transformedResponse = Map.of("transformed", "data");
            when(responseTransformer.transformResponse(originalResponse, "test"))
                    .thenReturn(transformedResponse);

            // When
            Object result = testAdapter.transformResponsePublic(originalResponse, "test");

            // Then
            assertEquals(transformedResponse, result);
            verify(responseTransformer).transformResponse(originalResponse, "test");
        }
    }

    @Nested
    @DisplayName("adaptModelName 测试")
    class AdaptModelNameTests {

        @Test
        @DisplayName("委托给 ResponseTransformer")
        void shouldDelegateToResponseTransformer() {
            // Given
            String originalName = "gpt-4";
            String adaptedName = "gpt-4-turbo";
            when(responseTransformer.adaptModelName(originalName))
                    .thenReturn(adaptedName);

            // When
            String result = testAdapter.adaptModelNamePublic(originalName);

            // Then
            assertEquals(adaptedName, result);
            verify(responseTransformer).adaptModelName(originalName);
        }

        @Test
        @DisplayName("null 模型名称处理")
        void shouldHandleNullModelName() {
            // Given
            when(responseTransformer.adaptModelName(null))
                    .thenReturn(null);

            // When
            String result = testAdapter.adaptModelNamePublic(null);

            // Then
            assertNull(result);
        }
    }

    // ========== 授权头处理测试 ==========

    @Nested
    @DisplayName("getAuthorizationHeader 测试")
    class GetAuthorizationHeaderTests {

        @Test
        @DisplayName("默认返回原始授权头")
        void shouldReturnOriginalAuthorization() {
            // Given
            String authorization = "Bearer token123";

            // When
            String result = testAdapter.getAuthorizationHeaderPublic(authorization, "test");

            // Then
            assertEquals(authorization, result);
        }

        @Test
        @DisplayName("null 授权头返回 null")
        void shouldReturnNullForNullAuthorization() {
            // When
            String result = testAdapter.getAuthorizationHeaderPublic(null, "test");

            // Then
            assertNull(result);
        }
    }

    // ========== 流式响应块转换测试 ==========

    @Nested
    @DisplayName("transformStreamChunk 测试")
    class TransformStreamChunkTests {

        @Test
        @DisplayName("委托给 ResponseTransformer")
        void shouldDelegateToResponseTransformer() {
            // Given
            String chunk = "data: {\"text\": \"hello\"}";
            String transformed = "data: {\"content\": \"hello\"}";
            when(responseTransformer.transformStreamChunk(chunk))
                    .thenReturn(transformed);

            // When
            String result = testAdapter.transformStreamChunkPublic(chunk);

            // Then
            assertEquals(transformed, result);
            verify(responseTransformer).transformStreamChunk(chunk);
        }

        @Test
        @DisplayName("空块处理")
        void shouldHandleEmptyChunk() {
            // Given
            when(responseTransformer.transformStreamChunk(""))
                    .thenReturn("");

            // When
            String result = testAdapter.transformStreamChunkPublic("");

            // Then
            assertEquals("", result);
        }
    }

    // ========== Registry 获取测试 ==========

    @Nested
    @DisplayName("getRegistry 测试")
    class GetRegistryTests {

        @Test
        @DisplayName("返回注入的 Registry")
        void shouldReturnInjectedRegistry() {
            // When
            ModelServiceRegistry result = testAdapter.getRegistry();

            // Then
            assertNotNull(result);
            assertEquals(registry, result);
        }
    }

    @Nested
    @DisplayName("getMetricsCollector 测试")
    class GetMetricsCollectorTests {

        @Test
        @DisplayName("返回注入的 MetricsCollector")
        void shouldReturnInjectedMetricsCollector() {
            // When
            MetricsCollector result = testAdapter.getMetricsCollectorPublic();

            // Then
            assertNotNull(result);
            assertEquals(metricsCollector, result);
        }
    }

    // ========== 错误分类测试 ==========

    @Nested
    @DisplayName("classifyError 测试（通过 ErrorHandler）")
    class ClassifyErrorTests {

        @Test
        @DisplayName("ResponseStatusException 分类")
        void shouldClassifyResponseStatusException() {
            // Given
            org.springframework.web.server.ResponseStatusException ex =
                    new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");

            // When
            String errorCode = errorHandler.classifyError(ex);

            // Then
            assertEquals("404", errorCode);
        }

        @Test
        @DisplayName("DownstreamServiceException 分类为 503")
        void shouldClassifyDownstreamServiceException() {
            // Given
            DownstreamServiceException ex = new DownstreamServiceException("Service unavailable", HttpStatus.SERVICE_UNAVAILABLE);

            // When
            String errorCode = errorHandler.classifyError(ex);

            // Then
            assertEquals("503", errorCode);
        }

        @Test
        @DisplayName("TimeoutException 分类为 504")
        void shouldClassifyTimeoutException() {
            // Given
            TimeoutException ex = new TimeoutException("Timeout");

            // When
            String errorCode = errorHandler.classifyError(ex);

            // Then
            assertEquals("504", errorCode);
        }

        @Test
        @DisplayName("ConnectException 分类为 503")
        void shouldClassifyConnectException() {
            // Given
            java.net.ConnectException ex = new java.net.ConnectException("Connection refused");

            // When
            String errorCode = errorHandler.classifyError(ex);

            // Then
            assertEquals("503", errorCode);
        }

        @Test
        @DisplayName("null 异常分类为 500")
        void shouldClassifyNullException() {
            // When
            String errorCode = errorHandler.classifyError(null);

            // Then
            assertEquals("500", errorCode);
        }

        @Test
        @DisplayName("未知异常分类为 500")
        void shouldClassifyUnknownException() {
            // Given
            RuntimeException ex = new RuntimeException("Unknown error");

            // When
            String errorCode = errorHandler.classifyError(ex);

            // Then
            assertEquals("500", errorCode);
        }
    }

    // ========== 重试判断测试 ==========

    @Nested
    @DisplayName("shouldRetry 测试（通过 ErrorHandler）")
    class ShouldRetryTests {

        @Test
        @DisplayName("达到最大重试次数不应重试")
        void shouldNotRetryWhenMaxRetriesReached() {
            // When
            boolean shouldRetry = errorHandler.shouldRetry(new TimeoutException(), 3, 3);

            // Then
            assertFalse(shouldRetry);
        }

        @Test
        @DisplayName("客户端错误不应重试")
        void shouldNotRetryForClientError() {
            // Given
            org.springframework.web.client.HttpClientErrorException ex =
                    new org.springframework.web.client.HttpClientErrorException(HttpStatus.BAD_REQUEST);

            // When
            boolean shouldRetry = errorHandler.shouldRetry(ex, 0, 3);

            // Then
            assertFalse(shouldRetry);
        }

        @Test
        @DisplayName("TimeoutException 应该重试")
        void shouldRetryForTimeoutException() {
            // When
            boolean shouldRetry = errorHandler.shouldRetry(new TimeoutException(), 0, 3);

            // Then
            assertTrue(shouldRetry);
        }

        @Test
        @DisplayName("ConnectException 应该重试")
        void shouldRetryForConnectException() {
            // When
            boolean shouldRetry = errorHandler.shouldRetry(new java.net.ConnectException("Connection refused"), 0, 3);

            // Then
            assertTrue(shouldRetry);
        }

        @Test
        @DisplayName("DownstreamServiceException 应该重试")
        void shouldRetryForDownstreamServiceException() {
            // Given
            DownstreamServiceException ex = new DownstreamServiceException("Service unavailable", HttpStatus.SERVICE_UNAVAILABLE);

            // When
            boolean shouldRetry = errorHandler.shouldRetry(ex, 0, 3);

            // Then
            assertTrue(shouldRetry);
        }

        @Test
        @DisplayName("5xx 服务器错误应该重试")
        void shouldRetryForServerError() {
            // Given
            org.springframework.web.server.ResponseStatusException ex =
                    new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");

            // When
            boolean shouldRetry = errorHandler.shouldRetry(ex, 0, 3);

            // Then
            assertTrue(shouldRetry);
        }
    }

    // ========== RetryPolicy 测试 ==========

    @Nested
    @DisplayName("RetryPolicy 测试")
    class RetryPolicyTests {

        @Test
        @DisplayName("canRetry 在未达到最大次数时返回 true")
        void shouldReturnTrueWhenBelowMaxRetries() {
            // When
            boolean canRetry = retryPolicy.canRetry(0, new RuntimeException());

            // Then
            assertTrue(canRetry);
        }

        @Test
        @DisplayName("canRetry 达到最大次数时返回 false")
        void shouldReturnFalseWhenMaxRetriesReached() {
            // When
            boolean canRetry = retryPolicy.canRetry(3, new RuntimeException());

            // Then
            assertFalse(canRetry);
        }

        @Test
        @DisplayName("isRetryable 对 TimeoutException 返回 true")
        void shouldBeRetryableForTimeoutException() {
            // When
            boolean retryable = retryPolicy.isRetryable(new TimeoutException());

            // Then
            assertTrue(retryable);
        }

        @Test
        @DisplayName("isRetryable 对 HttpClientErrorException 返回 false")
        void shouldNotBeRetryableForClientError() {
            // Given
            org.springframework.web.client.HttpClientErrorException ex =
                    new org.springframework.web.client.HttpClientErrorException(HttpStatus.BAD_REQUEST);

            // When
            boolean retryable = retryPolicy.isRetryable(ex);

            // Then
            assertFalse(retryable);
        }

        @Test
        @DisplayName("getNextDelay 计算指数退避")
        void shouldCalculateExponentialBackoff() {
            // When
            java.time.Duration delay0 = retryPolicy.getNextDelay(0);
            java.time.Duration delay1 = retryPolicy.getNextDelay(1);
            java.time.Duration delay2 = retryPolicy.getNextDelay(2);

            // Then
            assertTrue(delay0.toMillis() > 0);
            assertTrue(delay1.toMillis() > delay0.toMillis());
            assertTrue(delay2.toMillis() > delay1.toMillis());
        }
    }

    // ========== AdapterCapabilities 测试 ==========

    @Nested
    @DisplayName("AdapterCapabilities 测试")
    class AdapterCapabilitiesTests {

        @Test
        @DisplayName("构建器创建正确的能力配置")
        void shouldBuildCapabilitiesCorrectly() {
            // Given & When
            AdapterCapabilities capabilities = AdapterCapabilities.builder()
                    .chat(true)
                    .embedding(true)
                    .rerank(false)
                    .tts(true)
                    .build();

            // Then
            assertTrue(capabilities.isSupportChat());
            assertTrue(capabilities.isSupportEmbedding());
            assertFalse(capabilities.isSupportRerank());
            assertTrue(capabilities.isSupportTts());
        }

        @Test
        @DisplayName("contains 方法正确判断服务类型支持")
        void shouldCheckContainsCorrectly() {
            // Given
            AdapterCapabilities capabilities = AdapterCapabilities.builder()
                    .chat(true)
                    .embedding(false)
                    .build();

            // When & Then
            assertTrue(capabilities.contains(ModelServiceRegistry.ServiceType.chat));
            assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.embedding));
            assertFalse(capabilities.contains(ModelServiceRegistry.ServiceType.rerank));
        }

        @Test
        @DisplayName("all() 创建支持所有能力")
        void shouldCreateAllCapabilities() {
            // Given & When
            AdapterCapabilities capabilities = AdapterCapabilities.all();

            // Then
            assertTrue(capabilities.isSupportChat());
            assertTrue(capabilities.isSupportEmbedding());
            assertTrue(capabilities.isSupportRerank());
            assertTrue(capabilities.isSupportTts());
            assertTrue(capabilities.isSupportStt());
            assertTrue(capabilities.isSupportImageGenerate());
            assertTrue(capabilities.isSupportImageEdit());
            assertTrue(capabilities.isSupportStreaming());
        }
    }

    // ========== CapabilityChecker 测试 ==========

    @Nested
    @DisplayName("CapabilityChecker 测试")
    class CapabilityCheckerTests {

        private CapabilityChecker checker;

        @BeforeEach
        void setUpChecker() {
            checker = new CapabilityChecker();
        }

        @Test
        @DisplayName("支持的服务类型返回 null")
        void shouldReturnNullForSupportedService() {
            // Given
            AdapterCapabilities capabilities = AdapterCapabilities.builder()
                    .chat(true)
                    .build();

            // When
            org.reactivestreams.Publisher<ResponseEntity<String>> result =
                    checker.checkCapability(capabilities, ModelServiceRegistry.ServiceType.chat);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("不支持的服务类型返回错误响应")
        void shouldReturnErrorForUnsupportedService() {
            // Given
            AdapterCapabilities capabilities = AdapterCapabilities.builder()
                    .chat(true)
                    .build();

            // When
            reactor.core.publisher.Mono<ResponseEntity<String>> result =
                    checker.checkCapability(capabilities, ModelServiceRegistry.ServiceType.embedding);

            // Then
            assertNotNull(result);
            ResponseEntity<String> response = result.block();
            assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        }

        @Test
        @DisplayName("null capabilities 返回内部错误")
        void shouldReturnErrorForNullCapabilities() {
            // When
            reactor.core.publisher.Mono<ResponseEntity<String>> result =
                    checker.checkCapability(null, ModelServiceRegistry.ServiceType.chat);

            // Then
            assertNotNull(result);
            ResponseEntity<String> response = result.block();
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        @Test
        @DisplayName("supportsChat 正确判断")
        void shouldCheckSupportsChatCorrectly() {
            AdapterCapabilities capabilities = AdapterCapabilities.builder().chat(true).build();
            assertTrue(checker.supportsChat(capabilities));
            assertFalse(checker.supportsChat(AdapterCapabilities.builder().chat(false).build()));
        }
    }

    // ========== 辅助方法 ==========

    private ChatDTO.Request createChatRequest(String model, boolean stream) {
        List<ChatDTO.Message> messages = Arrays.asList(
                new ChatDTO.Message("user", "Hello", null)
        );
        return new ChatDTO.Request(
                model,
                messages,
                stream,
                100,
                0.7,
                1.0,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    // ========== 测试用的 Adapter 实现 ==========

    /**
     * 测试用的 Adapter 实现，暴露 protected 方法用于测试
     */
    private static class TestAdapter extends BaseAdapter {

        public TestAdapter(
                ModelServiceRegistry registry,
                MetricsCollector metricsCollector,
                ObjectMapper objectMapper,
                ModelCallStatsRepository statsRepository,
                InstanceSelector instanceSelector,
                ResponseTransformer responseTransformer,
                CapabilityChecker capabilityChecker,
                AdapterErrorHandler errorHandler,
                RetryPolicy retryPolicy) {
            super(
                    registry,
                    metricsCollector,
                    objectMapper,
                    statsRepository,
                    new RequestBuilder(),
                    new ResponseHandler(objectMapper),
                    instanceSelector,
                    responseTransformer,
                    capabilityChecker,
                    errorHandler,
                    retryPolicy,
                    new HttpRequestProcessor(),
                    new ResponseMapper(objectMapper),
                    null,
                    null,
                    new ErrorResponseBuilder(),
                    null
            );
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public AdapterCapabilities supportCapability() {
            return AdapterCapabilities.builder()
                    .chat(true)
                    .embedding(true)
                    .rerank(true)
                    .tts(true)
                    .stt(true)
                    .build();
        }

        // 暴露 protected 方法用于测试

        public ModelRouterProperties.ModelInstance selectInstancePublic(
                ModelServiceRegistry.ServiceType serviceType,
                String modelName,
                String clientIp) {
            return super.selectInstance(serviceType, modelName, clientIp);
        }

        public String getModelPathPublic(
                ModelServiceRegistry.ServiceType serviceType,
                String modelName) {
            return super.getModelPath(serviceType, modelName);
        }

        public String getModelNameFromRequestPublic(Object request) {
            return super.getModelNameFromRequest(request);
        }

        public String getServiceTypeFromRequestPublic(Object request) {
            return super.getServiceTypeFromRequest(request);
        }

        public int getMaxRetriesPublic(ModelServiceRegistry.ServiceType serviceType) {
            return super.getMaxRetries(serviceType);
        }

        public long calculateRetryDelayPublic(int retryCount) {
            return super.calculateRetryDelay(retryCount);
        }

        public long calculateRequestSizePublic(Object request) {
            return super.calculateRequestSize(request);
        }

        public Object transformRequestPublic(Object request, String adapterType) {
            return super.transformRequest(request, adapterType);
        }

        public Object transformResponsePublic(Object response, String adapterType) {
            return super.transformResponse(response, adapterType);
        }

        public String adaptModelNamePublic(String modelName) {
            return super.adaptModelName(modelName);
        }

        public String getAuthorizationHeaderPublic(String authorization, String adapterType) {
            return super.getAuthorizationHeader(authorization, adapterType);
        }

        public String transformStreamChunkPublic(String chunk) {
            return super.transformStreamChunk(chunk);
        }

        public MetricsCollector getMetricsCollectorPublic() {
            return super.getMetricsCollector();
        }
    }
}