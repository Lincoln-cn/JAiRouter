package org.unreal.modelrouter.persistence.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceInstanceEntity 单元测试
 */
@DisplayName("ServiceInstanceEntity 测试")
class ServiceInstanceEntityTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        ServiceInstanceEntity entity = new ServiceInstanceEntity();

        assertNull(entity.getId());
        assertNull(entity.getServiceConfigId());
        assertNull(entity.getInstanceName());
        assertNull(entity.getInstanceId());
        assertNull(entity.getBaseUrl());
        assertNull(entity.getAdapter());
        assertNull(entity.getHeaders());
    }

    @Test
    @DisplayName("测试全参数构造函数")
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> headers = Map.of("X-Custom-Header", "value");

        ServiceInstanceEntity entity = new ServiceInstanceEntity(
                1L,
                100L,
                "instance1",
                "uuid-1234",
                "http://localhost:8080",
                "/v1/chat",
                10,
                "ACTIVE",
                "HEALTHY",
                null,
                "ollama",
                headers,
                now,
                now
        );

        assertEquals(1L, entity.getId());
        assertEquals(100L, entity.getServiceConfigId());
        assertEquals("instance1", entity.getInstanceName());
        assertEquals("uuid-1234", entity.getInstanceId());
        assertEquals("http://localhost:8080", entity.getBaseUrl());
        assertEquals("/v1/chat", entity.getPath());
        assertEquals(10, entity.getWeight());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("HEALTHY", entity.getHealthStatus());
        assertEquals("ollama", entity.getAdapter());
        assertEquals(headers, entity.getHeaders());
    }

    @Test
    @DisplayName("测试Builder模式")
    void testBuilder() {
        Map<String, String> headers = Map.of("Authorization", "Bearer token");

        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .id(2L)
                .serviceConfigId(200L)
                .instanceName("builder-instance")
                .instanceId("builder-uuid")
                .baseUrl("http://builder:8080")
                .path("/api/v1")
                .weight(5)
                .status("INACTIVE")
                .healthStatus("UNHEALTHY")
                .errorMessage("Connection refused")
                .adapter("vllm")
                .headers(headers)
                .build();

        assertEquals(2L, entity.getId());
        assertEquals(200L, entity.getServiceConfigId());
        assertEquals("builder-instance", entity.getInstanceName());
        assertEquals("builder-uuid", entity.getInstanceId());
        assertEquals("http://builder:8080", entity.getBaseUrl());
        assertEquals("/api/v1", entity.getPath());
        assertEquals(5, entity.getWeight());
        assertEquals("INACTIVE", entity.getStatus());
        assertEquals("UNHEALTHY", entity.getHealthStatus());
        assertEquals("Connection refused", entity.getErrorMessage());
        assertEquals("vllm", entity.getAdapter());
    }

    @Test
    @DisplayName("测试Setter和Getter")
    void testSetterGetter() {
        ServiceInstanceEntity entity = new ServiceInstanceEntity();
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> headers = Map.of("X-Api-Key", "key123");

        entity.setId(3L);
        entity.setServiceConfigId(300L);
        entity.setInstanceName("setter-instance");
        entity.setInstanceId("setter-uuid");
        entity.setBaseUrl("http://setter:8080");
        entity.setPath("/setter/path");
        entity.setWeight(15);
        entity.setStatus("ERROR");
        entity.setHealthStatus("UNKNOWN");
        entity.setErrorMessage("Timeout");
        entity.setAdapter("gpustack");
        entity.setHeaders(headers);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertEquals(3L, entity.getId());
        assertEquals(300L, entity.getServiceConfigId());
        assertEquals("setter-instance", entity.getInstanceName());
        assertEquals("setter-uuid", entity.getInstanceId());
        assertEquals("http://setter:8080", entity.getBaseUrl());
        assertEquals("/setter/path", entity.getPath());
        assertEquals(15, entity.getWeight());
        assertEquals("ERROR", entity.getStatus());
        assertEquals("UNKNOWN", entity.getHealthStatus());
        assertEquals("Timeout", entity.getErrorMessage());
        assertEquals("gpustack", entity.getAdapter());
        assertEquals(headers, entity.getHeaders());
    }

    @Test
    @DisplayName("测试不同状态的实例")
    void testDifferentStatuses() {
        ServiceInstanceEntity activeEntity = ServiceInstanceEntity.builder()
                .status("ACTIVE")
                .healthStatus("HEALTHY")
                .build();

        ServiceInstanceEntity inactiveEntity = ServiceInstanceEntity.builder()
                .status("INACTIVE")
                .healthStatus("UNHEALTHY")
                .build();

        ServiceInstanceEntity errorEntity = ServiceInstanceEntity.builder()
                .status("ERROR")
                .healthStatus("UNKNOWN")
                .errorMessage("Connection failed")
                .build();

        assertEquals("ACTIVE", activeEntity.getStatus());
        assertEquals("HEALTHY", activeEntity.getHealthStatus());
        assertEquals("INACTIVE", inactiveEntity.getStatus());
        assertEquals("UNHEALTHY", inactiveEntity.getHealthStatus());
        assertEquals("ERROR", errorEntity.getStatus());
        assertEquals("Connection failed", errorEntity.getErrorMessage());
    }

    @Test
    @DisplayName("测试适配器配置")
    void testAdapterConfig() {
        ServiceInstanceEntity ollamaInstance = ServiceInstanceEntity.builder()
                .adapter("ollama")
                .build();

        ServiceInstanceEntity vllmInstance = ServiceInstanceEntity.builder()
                .adapter("vllm")
                .build();

        ServiceInstanceEntity gpustackInstance = ServiceInstanceEntity.builder()
                .adapter("gpustack")
                .build();

        assertEquals("ollama", ollamaInstance.getAdapter());
        assertEquals("vllm", vllmInstance.getAdapter());
        assertEquals("gpustack", gpustackInstance.getAdapter());
    }

    @Test
    @DisplayName("测试自定义请求头")
    void testCustomHeaders() {
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer token123",
                "X-Custom-Header", "custom-value",
                "X-Request-Id", "req-12345"
        );

        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .headers(headers)
                .build();

        assertEquals(3, entity.getHeaders().size());
        assertEquals("Bearer token123", entity.getHeaders().get("Authorization"));
        assertEquals("custom-value", entity.getHeaders().get("X-Custom-Header"));
        assertEquals("req-12345", entity.getHeaders().get("X-Request-Id"));
    }

    @Test
    @DisplayName("测试equals和hashCode")
    void testEqualsAndHashCode() {
        ServiceInstanceEntity entity1 = ServiceInstanceEntity.builder()
                .id(1L)
                .instanceName("instance1")
                .baseUrl("http://localhost:8080")
                .build();

        ServiceInstanceEntity entity2 = ServiceInstanceEntity.builder()
                .id(1L)
                .instanceName("instance1")
                .baseUrl("http://localhost:8080")
                .build();

        ServiceInstanceEntity entity3 = ServiceInstanceEntity.builder()
                .id(2L)
                .instanceName("instance2")
                .baseUrl("http://localhost:8081")
                .build();

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
        assertNotEquals(entity1, entity3);
    }
}
