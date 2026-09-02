package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.cache.ResponseCacheService;

import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * v2.9.9: NonStreamingRequestProcessor 响应缓存写挂载测试
 *
 * <p>验证写缓存判定：键非空(2xx 成功路径由 processJsonResponse 调用点结构保证)即写；
 * 键为空（流式/非确定性/未启用 → handler 不生成键）不写；数据 null / 服务未装配不写。
 */
@DisplayName("NonStreamingRequestProcessor 响应缓存写测试")
@ExtendWith(MockitoExtension.class)
class NonStreamingRequestProcessorCacheWriteTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RequestBuilder requestBuilder;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    @Mock
    private TokenUsageExtractor tokenUsageExtractor;

    @Mock
    private ResponseCacheService responseCacheService;

    private NonStreamingRequestProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        processor = new NonStreamingRequestProcessor(
                objectMapper, requestBuilder, metricsRecorder, tokenUsageExtractor);
        injectResponseCacheService(responseCacheService);
    }

    @Test
    @DisplayName("2xx 成功路径: 键非空时写入缓存")
    void writesWhenCacheKeyPresent() {
        Map<String, Object> transformedData = Map.of("choices", "cached-answer");

        processor.cacheSuccessfulResponse("cache-key-1", transformedData);

        verify(responseCacheService).store("cache-key-1", transformedData);
    }

    @Test
    @DisplayName("非确定性/流式请求由键为空覆盖: 不写缓存")
    void skipsWhenCacheKeyNull() {
        processor.cacheSuccessfulResponse(null, Map.of("choices", "answer"));

        verify(responseCacheService, never()).store(any(), any());
    }

    @Test
    @DisplayName("数据为 null 时不写缓存")
    void skipsWhenDataNull() {
        processor.cacheSuccessfulResponse("cache-key-1", null);

        verify(responseCacheService, never()).store(any(), any());
    }

    @Test
    @DisplayName("缓存服务未装配时不抛异常且不写")
    void skipsWhenServiceNotInjected() throws Exception {
        injectResponseCacheService(null);

        processor.cacheSuccessfulResponse("cache-key-1", Map.of("choices", "answer"));

        // 无异常即通过
    }

    private void injectResponseCacheService(final ResponseCacheService service) throws Exception {
        Field field = NonStreamingRequestProcessor.class.getDeclaredField("responseCacheService");
        field.setAccessible(true);
        field.set(processor, service);
    }
}
