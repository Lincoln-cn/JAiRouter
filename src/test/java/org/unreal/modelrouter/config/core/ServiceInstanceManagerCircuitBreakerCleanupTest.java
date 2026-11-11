/*
 * Copyright (c) 2024-2026 JAI Router
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceCircuitBreakerRepository;
import org.unreal.modelrouter.persistence.jpa.repository.InstanceRateLimitRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #1086：删除实例后，内存里的熔断器注册表必须同步清理。
 *
 * <p>实测现象：删掉两个实例后再查 {@code /api/config/instance/circuit-breaker/states}，
 * 仍返回 5 条（其中 2 条属于已删除实例）。原因是删除只清了数据库里的
 * {@code instance_circuit_breaker} 配置，没有动 {@link CircuitBreakerManager} 的内存注册表，
 * 运维会据此误判「这些实例还在」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceInstanceManager 删除实例时清理熔断器（issue #1086）")
class ServiceInstanceManagerCircuitBreakerCleanupTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final String BASE_URL = "https://api.example.com";

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
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    private ServiceInstanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceInstanceManager(
                serviceInstanceRepository, rateLimitRepository, circuitBreakerRepository,
                serviceConfigRepository, configurationService, configVersionManager,
                modelServiceRegistry, circuitBreakerManager);
    }

    private ServiceInstanceEntity existingInstance() {
        return ServiceInstanceEntity.builder()
                .id(7L)
                .instanceId(UUID)
                .instanceName("gpu-1")
                .baseUrl(BASE_URL)
                .path("/v1/chat/completions")
                .adapter("gpustack")
                .build();
    }

    @Test
    @DisplayName("删除实例：数据库配置与内存熔断器一起清理")
    void deleteInstance_alsoRemovesInMemoryCircuitBreaker() {
        when(serviceInstanceRepository.findById(7L)).thenReturn(Optional.of(existingInstance()));

        manager.deleteInstance(7L);

        verify(circuitBreakerRepository).deleteByInstanceId(7L);
        verify(serviceInstanceRepository).deleteById(7L);
        // 关键断言：修复前这里从未被调用
        verify(circuitBreakerManager).resetCircuitBreaker(UUID, BASE_URL);
    }

    @Test
    @DisplayName("删除不存在的实例：不触碰熔断器注册表")
    void deleteMissingInstance_doesNotTouchCircuitBreaker() {
        when(serviceInstanceRepository.findById(99L)).thenReturn(Optional.empty());

        manager.deleteInstance(99L);

        verify(serviceInstanceRepository).deleteById(99L);
        verify(circuitBreakerManager, never()).resetCircuitBreaker(any(), any());
    }

    @Test
    @DisplayName("实例 UUID 为空时仍以 baseUrl 兜底移除，不留残余")
    void deleteInstanceWithoutUuid_fallsBackToUrl() {
        ServiceInstanceEntity entity = ServiceInstanceEntity.builder()
                .id(8L)
                .instanceName("legacy")
                .baseUrl(BASE_URL)
                .path("/v1/chat/completions")
                .build();
        when(serviceInstanceRepository.findById(8L)).thenReturn(Optional.of(entity));

        manager.deleteInstance(8L);

        verify(circuitBreakerManager).resetCircuitBreaker(null, BASE_URL);
    }

    @Test
    @DisplayName("删除时读取实体失败不应中断删除流程")
    void deleteInstanceWhenLookupFails_stillDeletes() {
        when(serviceInstanceRepository.findById(anyLong())).thenReturn(Optional.empty());

        manager.deleteInstance(10L);

        verify(serviceInstanceRepository).deleteById(10L);
        verify(rateLimitRepository).deleteByInstanceId(10L);
        verify(circuitBreakerRepository).deleteByInstanceId(10L);
    }
}
