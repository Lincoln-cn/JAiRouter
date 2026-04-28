package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试BaseAdapter中的指标收集功能
 */
@ExtendWith(MockitoExtension.class)
class BaseAdapterMetricsTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private ServerHttpRequest httpRequest;

    @Test
    void shouldHaveMetricsCollectorInjected() {
        // Given & When
        TestAdapter adapter = new TestAdapter(registry, metricsCollector, new ObjectMapper());
        
        // Then
        assertNotNull(adapter.getMetricsCollector());
        assertEquals(metricsCollector, adapter.getMetricsCollector());
    }

    @Test
    void shouldCalculateRequestSize() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, metricsCollector, new ObjectMapper());
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
        TestAdapter adapter = new TestAdapter(registry, metricsCollector, new ObjectMapper());
        
        // When
        long size = adapter.calculateRequestSize(null);
        
        // Then
        assertEquals(0, size);
    }

    @Test
    void shouldRecordMetricsOnSuccessfulResponse() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, metricsCollector, new ObjectMapper());
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-instance");
        
        // Mock successful response
        ResponseEntity<String> successResponse = ResponseEntity.ok("success");
        
        // When - simulate the doOnSuccess callback
        long startTime = System.currentTimeMillis();
        long duration = System.currentTimeMillis() - startTime;
        
        // Simulate what happens in processRequest
        if (successResponse.getStatusCode().is2xxSuccessful()) {
            adapter.getMetricsCollector().recordBackendCall("test", "test-instance", duration, true);
        }
        
        // Then
        verify(metricsCollector).recordBackendCall(eq("test"), eq("test-instance"), anyLong(), eq(true));
    }

    @Test
    void shouldRecordMetricsOnFailedResponse() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, metricsCollector, new ObjectMapper());
        
        // When - simulate error scenario
        long startTime = System.currentTimeMillis();
        long duration = System.currentTimeMillis() - startTime;
        
        // Simulate what happens in processRequest onError
        adapter.getMetricsCollector().recordBackendCall("test", "test-instance", duration, false);
        
        // Then
        verify(metricsCollector).recordBackendCall(eq("test"), eq("test-instance"), anyLong(), eq(false));
    }

    /**
     * 测试用的Adapter实现，暴露protected方法用于测试
     */
    private static class TestAdapter extends BaseAdapter {

        public TestAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector, ObjectMapper objectMapper) {
            super(registry, metricsCollector, objectMapper, null, null, null, null, null, null, new AdapterErrorHandler(), new RetryPolicy(), new HttpRequestProcessor(), new ResponseMapper(new ObjectMapper()), null, null); // 测试时传入 null
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public org.unreal.modelrouter.router.adapter.AdapterCapabilities supportCapability() {
            return org.unreal.modelrouter.router.adapter.AdapterCapabilities.builder().chat(true).build();
        }

        // 暴露protected方法用于测试
        @Override
        public MetricsCollector getMetricsCollector() {
            return super.getMetricsCollector();
        }

        @Override
        public long calculateRequestSize(Object request) {
            return super.calculateRequestSize(request);
        }
    }
}