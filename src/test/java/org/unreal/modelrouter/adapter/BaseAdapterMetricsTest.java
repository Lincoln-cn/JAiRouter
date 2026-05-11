package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试BaseAdapter中的指标收集功能
 * v2.26.x: MetricsCollector已从BaseAdapter移除，指标记录委托给AdapterMetricsRecorder
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterMetricsTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    @Test
    void shouldCalculateRequestSize() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, new ObjectMapper());
        String testRequest = "test request content";

        // When
        long size = adapter.calculateRequestSize(testRequest);

        // Then
        assertTrue(size > 0);
        assertEquals(testRequest.getBytes().length, size);
    }

    @Test
    void shouldHandleNullRequestInSizeCalculation() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, new ObjectMapper());

        // When
        long size = adapter.calculateRequestSize(null);

        // Then
        assertEquals(0, size);
    }

    /**
     * 测试用的Adapter实现，暴露protected方法用于测试
     */
    private static class TestAdapter extends BaseAdapter {

        public TestAdapter(ModelServiceRegistry registry, ObjectMapper objectMapper) {
            super(registry, objectMapper, null, null, null, null, null, null, new AdapterErrorHandler(), new RetryPolicy(), new HttpRequestProcessor(), new ResponseMapper(new ObjectMapper()), null, null, new ErrorResponseBuilder(), null);
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public org.unreal.modelrouter.router.adapter.AdapterCapabilities supportCapability() {
            return org.unreal.modelrouter.router.adapter.AdapterCapabilities.builder().chat(true).build();
        }

        @Override
        public long calculateRequestSize(Object request) {
            return super.calculateRequestSize(request);
        }
    }
}