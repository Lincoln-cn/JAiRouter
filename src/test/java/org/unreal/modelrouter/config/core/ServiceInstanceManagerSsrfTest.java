package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.common.util.UrlSecurityValidator;
import org.unreal.modelrouter.config.core.helper.SsrfGuard;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R3-P0：实例 baseUrl 落库前必须过 SSRF 校验（拦截云元数据等危险目标）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceManager SSRF baseUrl 校验")
class ServiceInstanceManagerSsrfTest {

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.InstanceRateLimitRepository rateLimitRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.InstanceCircuitBreakerRepository circuitBreakerRepository;

    @Mock
    private org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private org.unreal.modelrouter.config.core.manager.ConfigVersionManager configVersionManager;

    @Mock
    private org.unreal.modelrouter.router.model.ModelServiceRegistry modelServiceRegistry;

    @Mock
    private org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager circuitBreakerManager;

    private ServiceInstanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceInstanceManager(
                serviceInstanceRepository, rateLimitRepository, circuitBreakerRepository,
                serviceConfigRepository, configurationService, configVersionManager,
                modelServiceRegistry, circuitBreakerManager);
        when(serviceInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateServiceInstanceRequest req(final String baseUrl) {
        CreateServiceInstanceRequest request = new CreateServiceInstanceRequest();
        request.setName("inst-1");
        request.setBaseUrl(baseUrl);
        request.setPath("/v1/chat/completions");
        return request;
    }

    @Test
    @DisplayName("云元数据地址 169.254.169.254 → SecurityException，且不落库")
    void createInstance_blocksMetadataIp() {
        CreateServiceInstanceRequest request = req("http://169.254.169.254/latest/meta-data/");
        SecurityException ex = assertThrows(SecurityException.class,
                () -> manager.createInstance(1L, request));
        assertTrue(ex.getMessage().contains("not allowed") || ex.getMessage().contains("blocked")
                || ex.getMessage().contains("host") || ex.getMessage().contains("Base URL"));
        verify(serviceInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("非 http/https 协议 → SecurityException")
    void createInstance_blocksFileScheme() {
        assertThrows(SecurityException.class,
                () -> manager.createInstance(1L, req("file:///etc/passwd")));
        verify(serviceInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("合法 https baseUrl → 正常创建")
    void createInstance_allowsPublicHttps() {
        when(serviceInstanceRepository.save(any())).thenAnswer(inv -> {
            ServiceInstanceEntity entity = inv.getArgument(0);
            entity.setId(9L);
            return entity;
        });
        CreateServiceInstanceRequest request = req("https://api.openai.com");
        ServiceInstanceDTO dto = manager.createInstance(1L, request);
        assertEquals("https://api.openai.com", dto.getBaseUrl());
    }

    @Test
    @DisplayName("更新实例为元数据地址 → SecurityException")
    void updateInstance_blocksMetadataIp() {
        ServiceInstanceEntity existing = ServiceInstanceEntity.builder()
                .id(5L).instanceName("ok").baseUrl("https://api.openai.com").build();
        when(serviceInstanceRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
        assertThrows(SecurityException.class,
                () -> manager.updateInstance(5L, req("http://169.254.169.254/")));
    }

    @Test
    @DisplayName("SsrfGuard 工具：validate 静态入口委托 UrlSecurityValidator")
    void ssrfGuard_delegates() {
        assertDoesNotThrow(() -> SsrfGuard.validateOutboundBaseUrl("https://api.example.com"));
        assertThrows(SecurityException.class,
                () -> SsrfGuard.validateOutboundBaseUrl("http://metadata.google.internal/computeMetadata/v1/"));
        assertDoesNotThrow(() -> UrlSecurityValidator.validateOutboundBaseUrl("http://10.0.0.5:8000"));
    }
}
