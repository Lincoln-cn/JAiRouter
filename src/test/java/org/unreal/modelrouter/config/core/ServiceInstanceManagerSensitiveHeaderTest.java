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
import org.unreal.modelrouter.config.core.helper.SensitiveHeaderMasker;
import org.unreal.modelrouter.config.dto.CreateServiceInstanceRequest;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * issue #1053：实例配置读面不得回显上游鉴权头的明文，且写面不能被回显的掩码覆盖。
 *
 * <p>这条链路的两端必须成对：只做读面脱敏，管理台回填表单再保存就会把 {@code ***abcd}
 * 写进库；只做写面还原，普通用户仍然能读到明文。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceManager 敏感请求头脱敏（issue #1053）")
class ServiceInstanceManagerSensitiveHeaderTest {

    private static final String REAL_AUTH = "Bearer sk-live-0123456789abcdef";

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
        when(rateLimitRepository.findByInstanceId(anyLong())).thenReturn(Optional.empty());
        when(circuitBreakerRepository.findByInstanceId(anyLong())).thenReturn(Optional.empty());
        when(serviceInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ServiceInstanceEntity existingInstance() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", REAL_AUTH);
        headers.put("Content-Type", "application/json");
        return ServiceInstanceEntity.builder()
                .id(7L)
                .instanceId("11111111-2222-3333-4444-555555555555")
                .instanceName("gpu-1")
                .baseUrl("https://api.example.com")
                .path("/v1/images/edits")
                .adapter("gpustack")
                .headers(headers)
                .build();
    }

    @Test
    @DisplayName("读面：Authorization 只回显末 4 位")
    void getInstance_masksCredentialHeaders() {
        ServiceInstanceEntity entity = existingInstance();
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(entity));

        ServiceInstanceDTO dto = manager.getInstance(7L).orElseThrow();

        assertNotEquals(REAL_AUTH, dto.getHeaders().get("Authorization"), "读面不能回显凭证原文");
        assertEquals(SensitiveHeaderMasker.MASK_PREFIX + "cdef", dto.getHeaders().get("Authorization"));
        assertEquals("application/json", dto.getHeaders().get("Content-Type"), "非敏感头不受影响");
    }

    @Test
    @DisplayName("写面：回填掩码时保留库里的真实凭证")
    void updateInstance_restoresCredentialWhenClientEchoesMask() {
        ServiceInstanceEntity entity = existingInstance();
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(entity));

        CreateServiceInstanceRequest request = new CreateServiceInstanceRequest();
        request.setWeight(5);
        Map<String, String> echoed = new HashMap<>();
        echoed.put("Authorization", SensitiveHeaderMasker.maskValue(REAL_AUTH));
        echoed.put("Content-Type", "application/json");
        request.setHeaders(echoed);

        ServiceInstanceDTO updated = manager.updateInstance(7L, request);

        assertEquals(REAL_AUTH, entity.getHeaders().get("Authorization"),
                "被掩码覆盖会让上游立刻 401，必须还原为库里的原值");
        assertEquals(5, updated.getWeight());
        assertNotEquals(REAL_AUTH, updated.getHeaders().get("Authorization"), "响应仍是掩码值");
    }

    @Test
    @DisplayName("写面：用户换成新凭证时按新值保存")
    void updateInstance_acceptsRotatedCredential() {
        ServiceInstanceEntity entity = existingInstance();
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(entity));

        CreateServiceInstanceRequest request = new CreateServiceInstanceRequest();
        Map<String, String> rotated = new HashMap<>();
        rotated.put("Authorization", "Bearer sk-rotated-credential-value");
        request.setHeaders(rotated);

        manager.updateInstance(7L, request);

        assertEquals("Bearer sk-rotated-credential-value", entity.getHeaders().get("Authorization"));
    }
}
