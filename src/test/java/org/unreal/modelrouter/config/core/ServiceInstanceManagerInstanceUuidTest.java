package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.common.dto.InstanceRateLimitDTO;
import org.unreal.modelrouter.common.dto.ServiceInstanceDTO;
import org.unreal.modelrouter.persistence.jpa.entity.InstanceRateLimitEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceRateLimitRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #1009：实例标识语义统一为 UUID 的守卫。
 *
 * <p>守卫两件事（issue 验证方式）：① {@code ServiceInstanceDTO.instanceId} 必须等于
 * {@code service_instance.instance_id} 列值（UUID），历史数据该列为空时回退数据库主键
 * 字符串（与运行时熔断器键的回退形态一致）；② {@code resolveInstanceRef} 双轨解析：
 * UUID（v3.8.0 规范形态）与纯数字数据库主键（v2.5~v3.7 历史 API 形态）都解析到同一
 * 实例。子 DTO（限流/熔断配置）的 {@code instanceId} 与父 DTO 同源同义，不得回落为
 * Long 主键（否则 JSON 里两个 {@code instanceId} 同名不同义）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceManager：实例标识 UUID 语义（issue #1009）")
class ServiceInstanceManagerInstanceUuidTest {

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private InstanceRateLimitRepository rateLimitRepository;

    @Mock
    private InstanceCircuitBreakerRepository circuitBreakerRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

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
    }

    private ServiceInstanceEntity entity(final Long id, final String instanceUuid) {
        ServiceInstanceEntity e = new ServiceInstanceEntity();
        e.setId(id);
        e.setInstanceId(instanceUuid);
        e.setServiceConfigId(1L);
        e.setInstanceName("inst-1");
        e.setBaseUrl("http://127.0.0.1:18099");
        e.setStatus("ACTIVE");
        return e;
    }

    // ==================== ① DTO.instanceId == service_instance.instance_id ====================

    @Test
    @DisplayName("UID-100: DTO.instanceId == service_instance.instance_id 列值（UUID）")
    void dtoInstanceIdEqualsColumnValue() {
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "uuid-aaaa")));
        when(rateLimitRepository.findByInstanceId(1L)).thenReturn(Optional.empty());
        when(circuitBreakerRepository.findByInstanceId(1L)).thenReturn(Optional.empty());

        ServiceInstanceDTO dto = manager.getInstance(1L).orElseThrow();

        assertEquals("uuid-aaaa", dto.getInstanceId(),
                "instanceId 必须等于 instance_id 列值，而不是数据库主键");
        assertEquals(1L, dto.getId(), "id 仍为数据库主键");
    }

    @Test
    @DisplayName("UID-101: 历史数据 instance_id 为空 ⇒ 回退主键字符串（与熔断器键回退形态一致）")
    void dtoInstanceIdFallsBackToPrimaryKey() {
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(entity(1L, null)));
        when(rateLimitRepository.findByInstanceId(1L)).thenReturn(Optional.empty());
        when(circuitBreakerRepository.findByInstanceId(1L)).thenReturn(Optional.empty());

        ServiceInstanceDTO dto = manager.getInstance(1L).orElseThrow();

        assertEquals("1", dto.getInstanceId(), "空 UUID 回退主键字符串");
    }

    @Test
    @DisplayName("UID-102: 子 DTO（限流配置）的 instanceId 与父 DTO 同源同义")
    void subDtoInheritsParentUuid() {
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "uuid-aaaa")));
        InstanceRateLimitEntity rl = new InstanceRateLimitEntity();
        rl.setId(9L);
        rl.setInstanceId(1L);
        rl.setEnabled(true);
        when(rateLimitRepository.findByInstanceId(1L)).thenReturn(Optional.of(rl));

        InstanceRateLimitDTO rlDto = manager.getRateLimitConfig(1L).orElseThrow();

        assertEquals("uuid-aaaa", rlDto.getInstanceId(),
                "子 DTO 的 instanceId 必须是父实例 UUID，不得是 Long 主键");
    }

    // ==================== ② resolveInstanceRef 双轨解析 ====================

    @Test
    @DisplayName("UID-103: UUID 形态走 findByInstanceId（规范形态）")
    void resolveInstanceRefUuidForm() {
        when(serviceInstanceRepository.findByInstanceId("uuid-aaaa")).thenReturn(Optional.of(entity(1L, "uuid-aaaa")));

        var ref = manager.resolveInstanceRef("uuid-aaaa").orElseThrow();

        assertEquals(1L, ref.entityId());
        assertEquals("uuid-aaaa", ref.instanceUuid());
        verify(serviceInstanceRepository, never()).findById(any(Long.class));
    }

    @Test
    @DisplayName("UID-104: 纯数字数据库主键形态（v2.5~v3.7 历史调用方）仍被接受")
    void resolveInstanceRefPrimaryKeyForm() {
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "uuid-aaaa")));

        var ref = manager.resolveInstanceRef("1").orElseThrow();

        assertEquals(1L, ref.entityId());
        assertEquals("uuid-aaaa", ref.instanceUuid(), "主键形态解析后对外仍给 UUID");
        verify(serviceInstanceRepository, never()).findByInstanceId(any());
    }

    @Test
    @DisplayName("UID-105: 主键形态 + 历史空 UUID ⇒ instanceUuid 回退主键字符串")
    void resolveInstanceRefLegacyEmptyUuidFallsBack() {
        when(serviceInstanceRepository.findById(1L)).thenReturn(Optional.of(entity(1L, null)));

        var ref = manager.resolveInstanceRef(" 1 ").orElseThrow();

        assertEquals(1L, ref.entityId());
        assertEquals("1", ref.instanceUuid());
    }

    @Test
    @DisplayName("UID-106: 空标识 ⇒ Optional.empty，不触库")
    void resolveInstanceRefBlank() {
        assertTrue(manager.resolveInstanceRef(null).isEmpty());
        assertTrue(manager.resolveInstanceRef("   ").isEmpty());
        verify(serviceInstanceRepository, never()).findById(any(Long.class));
        verify(serviceInstanceRepository, never()).findByInstanceId(any());
    }

    @Test
    @DisplayName("UID-107: 标识不存在 ⇒ Optional.empty（controller 转 404）")
    void resolveInstanceRefNotFound() {
        when(serviceInstanceRepository.findByInstanceId("uuid-missing")).thenReturn(Optional.empty());

        assertTrue(manager.resolveInstanceRef("uuid-missing").isEmpty());
    }
}
