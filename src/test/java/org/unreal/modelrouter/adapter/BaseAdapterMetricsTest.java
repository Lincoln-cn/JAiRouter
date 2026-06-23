package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试BaseAdapter中的指标收集功能
 * v2.28.x: 使用新的聚合组件构造函数
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterMetricsTest {

    @Mock
    private ModelServiceRegistry registry;

    @Test
    void shouldCalculateRequestSize() {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        AdapterContext context = new AdapterContext(registry, objectMapper, null);
        RequestProcessingSupport requestSupport = new RequestProcessingSupport(
                null, null, null, null, null, null, null, null);
        ResilienceSupport resilienceSupport = new ResilienceSupport(
                null, null, null, null, null, null);
        TestAdapter adapter = new TestAdapter(context, requestSupport, resilienceSupport);

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
        ObjectMapper objectMapper = new ObjectMapper();
        AdapterContext context = new AdapterContext(registry, objectMapper, null);
        RequestProcessingSupport requestSupport = new RequestProcessingSupport(
                null, null, null, null, null, null, null, null);
        ResilienceSupport resilienceSupport = new ResilienceSupport(
                null, null, null, null, null, null);
        TestAdapter adapter = new TestAdapter(context, requestSupport, resilienceSupport);

        // When
        long size = adapter.calculateRequestSize(null);

        // Then
        assertEquals(0, size);
    }

    /**
     * 测试用的Adapter实现，暴露protected方法用于测试
     */
    private static class TestAdapter extends BaseAdapter {

        public TestAdapter(AdapterContext context, RequestProcessingSupport requestSupport, 
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

        @Override
        public long calculateRequestSize(Object request) {
            return super.calculateRequestSize(request);
        }
    }
}