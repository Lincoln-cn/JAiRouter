package org.unreal.modelrouter.router.adapter.request;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.cache.SemanticCacheService;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * issue #1056：语义缓存写不能落在事件循环线程上。
 *
 * <p>{@code cacheSuccessfulResponse} 由响应转换链调用，调用时线程就是 {@code reactor-http-nio} 的事件循环。
 * 缓存后端可能是 Redis，{@code put} 内部会阻塞等待 I/O ⇒ 必须移交 {@code boundedElastic}，
 * 否则一次缓存写会卡住整个事件循环上的其它请求。</p>
 */
@DisplayName("NonStreamingRequestProcessor 语义缓存写线程（issue #1056）")
@ExtendWith(MockitoExtension.class)
class NonStreamingRequestProcessorSemanticCacheOffloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RequestBuilder requestBuilder;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    @Mock
    private TokenUsageExtractor tokenUsageExtractor;

    @Mock
    private SemanticCacheService semanticCacheService;

    @Mock
    private org.unreal.modelrouter.pii.PiiDetector piiDetector;

    private NonStreamingRequestProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        processor = new NonStreamingRequestProcessor(
                objectMapper, requestBuilder, metricsRecorder, tokenUsageExtractor);
        inject("semanticCacheService", semanticCacheService);
        // 探测器缺失时处理器按「疑似敏感」处理、直接放弃写缓存（安全默认），
        // 所以这里必须显式装配一个判定为「不敏感」的探测器，否则走不到写入路径。
        inject("piiDetector", piiDetector);
        when(piiDetector.containsSensitive(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("语义缓存写入在 boundedElastic 线程上执行")
    void semanticCacheWriteRunsOffEventLoop() throws Exception {
        when(semanticCacheService.isEnabled()).thenReturn(true);
        AtomicReference<String> executingThread = new AtomicReference<>();
        CountDownLatch written = new CountDownLatch(1);
        doAnswer(invocation -> {
            executingThread.set(Thread.currentThread().getName());
            written.countDown();
            return null;
        }).when(semanticCacheService).put(anyString(), anyString(), any());

        processor.cacheSuccessfulResponse(null, Map.of("choices", "answer"), "tenant-scope", "what is 1+1");

        assertTrue(written.await(5, TimeUnit.SECONDS), "语义缓存写入应在响应返回后异步完成");
        assertTrue(executingThread.get().contains("boundedElastic"),
                "语义缓存写必须离开事件循环，实际执行线程=" + executingThread.get());
    }

    private void inject(final String fieldName, final Object value) throws Exception {
        Field field = NonStreamingRequestProcessor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(processor, value);
    }
}
