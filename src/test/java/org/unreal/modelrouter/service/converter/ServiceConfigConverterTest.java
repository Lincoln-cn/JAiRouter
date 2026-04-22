package org.unreal.modelrouter.service.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;
import org.unreal.modelrouter.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.dto.LoadBalanceConfig;
import org.unreal.modelrouter.dto.RateLimitConfig;
import org.unreal.modelrouter.dto.ServiceConfigDTO;
import org.unreal.modelrouter.dto.UpdateServiceConfigRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceConfigConverter 单元测试
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
class ServiceConfigConverterTest {

    private ServiceConfigConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ServiceConfigConverter();
    }

    @Test
    void testToDTO_ServiceConfiguration() {
        // Given
        ServiceConfiguration config = new ServiceConfiguration(
                "normal",
                List.of(),
                new LoadBalanceConfiguration("round_robin", "murmur3"),
                null, null, null
        );
        String serviceType = "chat";

        // When
        ServiceConfigDTO dto = converter.toDTO(config, serviceType);

        // Then
        assertNotNull(dto);
        assertEquals("chat", dto.getServiceType());
        assertEquals("normal", dto.getAdapter());
        assertEquals("round_robin", dto.getLoadBalanceType());
    }

    @Test
    void testToDTO_ServiceConfiguration_Null() {
        // Given
        ServiceConfiguration config = null;

        // When
        ServiceConfigDTO dto = converter.toDTO(config, "chat");

        // Then
        assertNull(dto);
    }

    @Test
    void testToDTO_ServiceConfiguration_NullLoadBalance() {
        // Given
        ServiceConfiguration config = new ServiceConfiguration(
                "normal",
                List.of(),
                null, null, null, null
        );

        // When
        ServiceConfigDTO dto = converter.toDTO(config, "chat");

        // Then
        assertNotNull(dto);
        assertNull(dto.getLoadBalanceType());
    }

    @Test
    void testFromCreateRequest_Valid() {
        // Given
        CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("normal")
                .loadBalanceType("round_robin")
                .instances(List.of(
                        CreateServiceInstanceRequest.builder()
                                .name("instance1")
                                .baseUrl("http://localhost:8080")
                                .weight(1)
                                .status("active")
                                .build()
                ))
                .build();

        // When
        ServiceConfiguration config = converter.fromCreateRequest(request);

        // Then
        assertNotNull(config);
        assertEquals("normal", config.adapter());
        assertNotNull(config.instances());
        assertEquals(1, config.instances().size());
        assertEquals("instance1", config.instances().get(0).name());
    }

    @Test
    void testFromCreateRequest_NullInstances() {
        // Given
        CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("normal")
                .loadBalanceType("round_robin")
                .instances(null)
                .build();

        // When
        ServiceConfiguration config = converter.fromCreateRequest(request);

        // Then
        assertNotNull(config);
        assertNull(config.instances());
    }

    @Test
    void testFromUpdateRequest_Valid() {
        // Given
        ServiceConfiguration existing = new ServiceConfiguration(
                "existing-adapter",
                List.of(),
                new LoadBalanceConfiguration("round_robin", "murmur3"),
                new RateLimitConfiguration(100, 6000, 360000, 8640000, 50, true),
                new CircuitBreakerConfiguration(5, 60000L, 2, true),
                new FallbackConfiguration(true, "http://fallback.com", 3, 1000L, true)
        );

        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("updated-adapter")
                .loadBalance(LoadBalanceConfig.builder()
                        .type("weighted")
                        .hashAlgorithm("sha256")
                        .build())
                .build();

        // When
        ServiceConfiguration updated = converter.fromUpdateRequest(existing, request);

        // Then
        assertNotNull(updated);
        assertEquals("updated-adapter", updated.adapter());
        assertEquals("weighted", updated.loadBalance().type());
        assertEquals("sha256", updated.loadBalance().hashAlgorithm());
        // 保留原有配置
        assertEquals(existing.rateLimit(), updated.rateLimit());
        assertEquals(existing.circuitBreaker(), updated.circuitBreaker());
        assertEquals(existing.fallback(), updated.fallback());
    }

    @Test
    void testFromUpdateRequest_NullRequest() {
        // Given
        ServiceConfiguration existing = createTestConfig();
        UpdateServiceConfigRequest request = null;

        // When
        ServiceConfiguration updated = converter.fromUpdateRequest(existing, request);

        // Then
        assertEquals(existing, updated);
    }

    @Test
    void testFromUpdateRequest_NullExisting() {
        // Given
        ServiceConfiguration existing = null;
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("new-adapter")
                .build();

        // When
        ServiceConfiguration updated = converter.fromUpdateRequest(existing, request);

        // Then
        assertNull(updated);
    }

    @Test
    void testToDTOList() {
        // Given
        Map<String, ServiceConfiguration> configurations = new HashMap<>();
        configurations.put("chat", new ServiceConfiguration(
                "normal", List.of(),
                new LoadBalanceConfiguration("round_robin", null),
                null, null, null
        ));
        configurations.put("embedding", new ServiceConfiguration(
                "normal", List.of(),
                new LoadBalanceConfiguration("round_robin", null),
                null, null, null
        ));

        // When
        List<ServiceConfigDTO> dtoList = converter.toDTOList(configurations);

        // Then
        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
    }

    @Test
    void testToDTOList_Empty() {
        // Given
        Map<String, ServiceConfiguration> configurations = Map.of();

        // When
        List<ServiceConfigDTO> dtoList = converter.toDTOList(configurations);

        // Then
        assertNotNull(dtoList);
        assertTrue(dtoList.isEmpty());
    }

    @Test
    void testToDTOList_Null() {
        // Given
        Map<String, ServiceConfiguration> configurations = null;

        // When
        List<ServiceConfigDTO> dtoList = converter.toDTOList(configurations);

        // Then
        assertNotNull(dtoList);
        assertTrue(dtoList.isEmpty());
    }

    @Test
    void testToRateLimit() {
        // Given
        RateLimitConfig config = RateLimitConfig.builder()
                .rate(100)
                .capacity(50)
                .enabled(true)
                .build();

        // When
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .rateLimit(config)
                .build();
        ServiceConfiguration existing = new ServiceConfiguration(
                "test", List.of(), null, null, null, null
        );
        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        // Then
        assertNotNull(result.rateLimit());
        assertEquals(100, result.rateLimit().requestsPerSecond());
        assertEquals(50, result.rateLimit().burstSize());
        assertEquals(true, result.rateLimit().enabled());
    }

    @Test
    void testToRateLimit_Null() {
        // Given
        RateLimitConfig config = null;

        // When
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .rateLimit(config)
                .build();
        ServiceConfiguration existing = new ServiceConfiguration(
                "test", List.of(), null, null, null, null
        );
        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        // Then
        assertNull(result.rateLimit());
    }

    @Test
    void testToCircuitBreaker() {
        // Given
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureThreshold(10)
                .timeout(30000)
                .successThreshold(3)
                .enabled(true)
                .build();

        // When
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .circuitBreaker(config)
                .build();
        ServiceConfiguration existing = new ServiceConfiguration(
                "test", List.of(), null, null, null, null
        );
        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        // Then
        assertNotNull(result.circuitBreaker());
        assertEquals(10, result.circuitBreaker().failureThreshold());
        assertEquals(30000L, result.circuitBreaker().timeout());
        assertEquals(3, result.circuitBreaker().successThreshold());
        assertEquals(true, result.circuitBreaker().enabled());
    }

    @Test
    void testToCircuitBreaker_Null() {
        // Given
        CircuitBreakerConfig config = null;

        // When
        UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .circuitBreaker(config)
                .build();
        ServiceConfiguration existing = new ServiceConfiguration(
                "test", List.of(), null, null, null, null
        );
        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        // Then
        assertNull(result.circuitBreaker());
    }

    @Test
    void testToModelInstance() {
        // Given
        CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
                .name("test-instance")
                .baseUrl("http://localhost:8080")
                .path("/v1/chat")
                .adapter("normal")
                .weight(5)
                .status("active")
                .headers(Map.of("X-Custom-Header", "value"))
                .build();

        // When
        CreateServiceConfigRequest createRequest = CreateServiceConfigRequest.builder()
                .serviceType("chat")
                .adapter("normal")
                .instances(List.of(request))
                .build();
        ServiceConfiguration result = converter.fromCreateRequest(createRequest);

        // Then
        assertNotNull(result.instances());
        assertEquals(1, result.instances().size());
        ModelInstanceConfiguration instance = result.instances().get(0);
        assertEquals("test-instance", instance.name());
        assertEquals("http://localhost:8080", instance.baseUrl());
        assertEquals("/v1/chat", instance.path());
        assertEquals("normal", instance.adapter());
        assertEquals(5, instance.weight());
        assertEquals("active", instance.status());
        assertNotNull(instance.headers());
        assertEquals("value", instance.headers().get("X-Custom-Header"));
    }

    // 辅助方法
    private ServiceConfiguration createTestConfig() {
        return new ServiceConfiguration(
                "test-adapter",
                List.of(),
                LoadBalanceConfiguration.defaultConfig(),
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig()
        );
    }
}
