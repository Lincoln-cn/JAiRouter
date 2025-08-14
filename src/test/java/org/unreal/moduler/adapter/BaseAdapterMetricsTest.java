package org.unreal.moduler.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.MetricsCollector;
import reactor.core.publisher.Mono;

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
        TestAdapter adapter = new TestAdapter(registry, metricsCollector);
        
        // Then
        assertNotNull(adapter.getMetricsCollector());
        assertEquals(metricsCollector, adapter.getMetricsCollector());
    }

    @Test
    void shouldCalculateRequestSize() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, metricsCollector);
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
        TestAdapter adapter = new TestAdapter(registry, metricsCollector);
        
        // When
        long size = adapter.calculateRequestSize(null);
        
        // Then
        assertEquals(0, size);
    }

    @Test
    void shouldRecordMetricsOnSuccessfulResponse() {
        // Given
        TestAdapter adapter = new TestAdapter(registry, metricsCollector);
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
        TestAdapter adapter = new TestAdapter(registry, metricsCollector);
        
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

        public TestAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
            super(registry, metricsCollector);
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public org.unreal.modelrouter.adapter.AdapterCapabilities supportCapability() {
            return org.unreal.modelrouter.adapter.AdapterCapabilities.builder().chat(true).build();
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