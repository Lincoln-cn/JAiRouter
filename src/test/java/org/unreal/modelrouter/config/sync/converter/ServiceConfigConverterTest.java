package org.unreal.modelrouter.config.sync.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.config.core.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.core.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.core.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.core.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.core.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.config.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.config.dto.ServiceConfigDTO;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceConfigConverterTest {

    private ServiceConfigConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ServiceConfigConverter();
    }

    @Test
    void toDTO_withServiceConfiguration_returnsDTO() {
        ServiceConfiguration config = new ServiceConfiguration(
                "normal",
                List.of(),
                new LoadBalanceConfiguration("round-robin", null),
                null, null, null
        );

        ServiceConfigDTO dto = converter.toDTO(config, "chat");

        assertNotNull(dto);
        assertEquals("chat", dto.getServiceType());
        assertEquals("normal", dto.getAdapter());
        assertEquals("round-robin", dto.getLoadBalanceType());
    }

    @Test
    void toDTO_withNullConfig_returnsNull() {
        ServiceConfigDTO dto = converter.toDTO(null, "chat");
        assertNull(dto);
    }

    @Test
    void toDTO_withNullLoadBalance_returnsNullLoadBalanceType() {
        ServiceConfiguration config = new ServiceConfiguration(
                "normal", List.of(), null, null, null, null
        );

        ServiceConfigDTO dto = converter.toDTO(config, "chat");

        assertNotNull(dto);
        assertNull(dto.getLoadBalanceType());
    }

    @Test
    void toDTO_fromEntity_returnsDTO() {
        ServiceConfigEntity entity = new ServiceConfigEntity();
        entity.setId(1L);
        entity.setConfigKey("chat-config");
        entity.setServiceType("chat");
        entity.setAdapter("normal");
        entity.setLoadBalanceType("round-robin");
        entity.setVersion(1);
        entity.setIsLatest(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        ServiceConfigDTO dto = converter.toDTO(entity);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("chat-config", dto.getConfigKey());
        assertEquals("chat", dto.getServiceType());
        assertEquals("normal", dto.getAdapter());
        assertEquals("round-robin", dto.getLoadBalanceType());
        assertEquals(1, dto.getVersion());
        assertTrue(dto.getIsLatest());
    }

    @Test
    void toDTO_fromNullEntity_returnsNull() {
        ServiceConfigDTO dto = converter.toDTO((ServiceConfigEntity) null);
        assertNull(dto);
    }

    @Test
    void fromCreateRequest_withInstances_convertsInstances() {
        CreateServiceInstanceRequest instanceReq = new CreateServiceInstanceRequest();
        instanceReq.setName("instance-1");
        instanceReq.setBaseUrl("http://localhost:8080");
        instanceReq.setPath("/v1/chat");
        instanceReq.setWeight(1);
        instanceReq.setStatus("active");

        CreateServiceConfigRequest request = new CreateServiceConfigRequest();
        request.setAdapter("normal");
        request.setLoadBalanceType("round-robin");
        request.setInstances(List.of(instanceReq));

        ServiceConfiguration config = converter.fromCreateRequest(request);

        assertNotNull(config);
        assertEquals("normal", config.adapter());
        assertNotNull(config.instances());
        assertEquals(1, config.instances().size());
        assertEquals("instance-1", config.instances().get(0).name());
        assertEquals("http://localhost:8080", config.instances().get(0).baseUrl());
    }

    @Test
    void fromCreateRequest_withNullInstances_returnsNullInstances() {
        CreateServiceConfigRequest request = new CreateServiceConfigRequest();
        request.setAdapter("normal");

        ServiceConfiguration config = converter.fromCreateRequest(request);

        assertNotNull(config);
        assertNull(config.instances());
    }

    @Test
    void fromCreateRequest_withNullRequest_returnsNull() {
        ServiceConfiguration config = converter.fromCreateRequest(null);
        assertNull(config);
    }

    @Test
    void fromUpdateRequest_mergesAdapter() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "old-adapter", List.of(), null, null, null, null
        );

        UpdateServiceConfigRequest request = new UpdateServiceConfigRequest();
        request.setAdapter("new-adapter");

        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        assertNotNull(result);
        assertEquals("new-adapter", result.adapter());
    }

    @Test
    void fromUpdateRequest_preservesExistingAdapter() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "old-adapter", List.of(), null, null, null, null
        );

        UpdateServiceConfigRequest request = new UpdateServiceConfigRequest();

        ServiceConfiguration result = converter.fromUpdateRequest(existing, request);

        assertNotNull(result);
        assertEquals("old-adapter", result.adapter());
    }

    @Test
    void fromUpdateRequest_withNullRequest_returnsExisting() {
        ServiceConfiguration existing = new ServiceConfiguration(
                "adapter", List.of(), null, null, null, null
        );

        ServiceConfiguration result = converter.fromUpdateRequest(existing, null);

        assertEquals(existing, result);
    }

    @Test
    void fromUpdateRequest_withNullExisting_returnsNull() {
        UpdateServiceConfigRequest request = new UpdateServiceConfigRequest();
        request.setAdapter("new-adapter");

        ServiceConfiguration result = converter.fromUpdateRequest(null, request);

        assertNull(result);
    }

    @Test
    void toDTOList_withEmptyMap_returnsEmptyList() {
        Map<String, ServiceConfiguration> configs = new HashMap<>();
        List<ServiceConfigDTO> result = converter.toDTOList(configs);
        assertTrue(result.isEmpty());
    }

    @Test
    void toDTOList_withNullMap_returnsEmptyList() {
        List<ServiceConfigDTO> result = converter.toDTOList(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void toDTOList_withConfigs_returnsList() {
        Map<String, ServiceConfiguration> configs = new HashMap<>();
        configs.put("chat", new ServiceConfiguration("normal", List.of(), null, null, null, null));
        configs.put("embedding", new ServiceConfiguration("normal", List.of(), null, null, null, null));

        List<ServiceConfigDTO> result = converter.toDTOList(configs);

        assertEquals(2, result.size());
    }
}
