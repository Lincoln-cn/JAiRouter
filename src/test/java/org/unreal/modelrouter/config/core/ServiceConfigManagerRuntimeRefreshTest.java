package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;
import org.unreal.modelrouter.config.sync.merger.ConfigMerger;
import org.unreal.modelrouter.config.sync.repository.StoreConfigRepository;
import org.unreal.modelrouter.config.dto.CreateServiceConfigRequest;
import org.unreal.modelrouter.config.dto.ServiceConfigDTO;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.config.sync.converter.ServiceConfigConverter;
import org.unreal.modelrouter.config.sync.validator.ServiceConfigValidator;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #776 回归：配置写入接口返回成功后，必须在同一进程内刷新运行时缓存。
 *
 * <p>缺陷形态：{@code ServiceConfigManager} 只把配置写进 StoreManager 与数据库，
 * 从不通知 {@link ModelServiceRegistry}；于是控制台「保存成功」而实际路由仍按启动时的旧配置工作，
 * 直到重启。</p>
 *
 * <p>用 A/B 反证：删掉 {@code ServiceConfigManager} 里的 {@code refreshRuntimeConfig(...)} 调用，
 * 本类 6 条「必须刷新」用例立即转红。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServiceConfigManagerRuntimeRefreshTest {

    private static final String SERVICE_TYPE = "chat";

    @Mock
    private ServiceConfigValidator validator;

    @Mock
    private ConfigMerger merger;

    @Mock
    private ServiceConfigConverter converter;

    @Mock
    private StoreConfigRepository configRepository;

    @Mock
    private ServiceConfigRepository jpaRepository;

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    private ServiceConfigManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceConfigManager(
                validator,
                merger,
                converter,
                configRepository,
                jpaRepository,
                modelServiceRegistry);
    }

    @Test
    @DisplayName("#776: API 保存配置后刷新运行时缓存")
    void saveServiceConfigRefreshesRuntimeConfig() {
        final CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType(SERVICE_TYPE)
                .adapter("normal")
                .loadBalanceType("roundRobin")
                .build();
        final ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        final ServiceConfigEntity saved = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(SERVICE_TYPE)
                .adapter("normal")
                .version(1)
                .isLatest(true)
                .build();

        when(converter.fromCreateRequest(request)).thenReturn(config);
        when(jpaRepository.save(any(ServiceConfigEntity.class))).thenReturn(saved);
        when(converter.toDTO(saved)).thenReturn(ServiceConfigDTO.builder().serviceType(SERVICE_TYPE).build());
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(true);

        final ServiceConfigDTO result = manager.saveServiceConfig(SERVICE_TYPE, request);

        assertNotNull(result);
        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: API 删除配置后刷新运行时缓存（已删除的服务不得继续参与路由）")
    void deleteServiceConfigRefreshesRuntimeConfig() {
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(false);

        manager.deleteServiceConfig(SERVICE_TYPE);

        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
        verify(jpaRepository).deleteAllByServiceType(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: API 更新配置后刷新运行时缓存")
    void updateServiceConfigRefreshesRuntimeConfig() {
        final UpdateServiceConfigRequest request = UpdateServiceConfigRequest.builder()
                .adapter("openai")
                .build();
        final ServiceConfiguration existing = ServiceConfiguration.defaultConfig();
        final ServiceConfiguration updated = ServiceConfiguration.defaultConfig();

        when(configRepository.findById(SERVICE_TYPE)).thenReturn(Optional.of(existing));
        when(converter.fromUpdateRequest(existing, request)).thenReturn(updated);
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(true);

        manager.updateServiceConfig(SERVICE_TYPE, request);

        verify(configRepository).save(SERVICE_TYPE, updated);
        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: 业务层 createService 后刷新运行时缓存")
    void createServiceRefreshesRuntimeConfig() {
        final ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(true);

        manager.createService(SERVICE_TYPE, config);

        verify(configRepository).save(SERVICE_TYPE, config);
        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: 业务层 updateServiceConfig 后刷新运行时缓存")
    void updateDomainConfigRefreshesRuntimeConfig() {
        final ServiceConfiguration existing = ServiceConfiguration.defaultConfig();
        final ServiceConfiguration updates = ServiceConfiguration.defaultConfig();
        final ServiceConfiguration merged = ServiceConfiguration.defaultConfig();

        when(configRepository.findById(SERVICE_TYPE)).thenReturn(Optional.of(existing));
        when(merger.merge(existing, updates)).thenReturn(merged);
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(true);

        final ServiceConfiguration result = manager.updateServiceConfig(SERVICE_TYPE, updates);

        assertEquals(merged, result);
        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: 业务层 deleteService 后刷新运行时缓存")
    void deleteServiceRefreshesRuntimeConfig() {
        when(configRepository.exists(SERVICE_TYPE)).thenReturn(true);
        when(modelServiceRegistry.refreshServiceConfig(SERVICE_TYPE)).thenReturn(false);

        manager.deleteService(SERVICE_TYPE);

        verify(configRepository).delete(SERVICE_TYPE);
        verify(modelServiceRegistry).refreshServiceConfig(SERVICE_TYPE);
    }

    @Test
    @DisplayName("#776: 校验失败时不刷新（写入没有发生，不该污染运行时缓存）")
    void validationFailureDoesNotRefreshRuntimeConfig() {
        final CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType(SERVICE_TYPE)
                .adapter("normal")
                .build();

        doThrow(new IllegalArgumentException("非法服务类型")).when(validator).validateServiceType(anyString());

        assertThrows(IllegalArgumentException.class,
                () -> manager.saveServiceConfig(SERVICE_TYPE, request));
        verifyNoInteractions(modelServiceRegistry);
        verify(jpaRepository, never()).save(any(ServiceConfigEntity.class));
    }

    @Test
    @DisplayName("#776: 刷新失败不改变写入结果（配置已落库，不把成功写入包装成失败）")
    void refreshFailureKeepsWriteResult() {
        final CreateServiceConfigRequest request = CreateServiceConfigRequest.builder()
                .serviceType(SERVICE_TYPE)
                .adapter("normal")
                .build();
        final ServiceConfiguration config = ServiceConfiguration.defaultConfig();
        final ServiceConfigEntity saved = ServiceConfigEntity.builder()
                .configKey("model-router-config")
                .serviceType(SERVICE_TYPE)
                .version(1)
                .isLatest(true)
                .build();

        when(converter.fromCreateRequest(request)).thenReturn(config);
        when(jpaRepository.save(any(ServiceConfigEntity.class))).thenReturn(saved);
        when(converter.toDTO(saved)).thenReturn(ServiceConfigDTO.builder().serviceType(SERVICE_TYPE).build());
        when(modelServiceRegistry.refreshServiceConfig(eq(SERVICE_TYPE)))
                .thenThrow(new IllegalStateException("registry unavailable"));

        final ServiceConfigDTO result = manager.saveServiceConfig(SERVICE_TYPE, request);

        assertNotNull(result);
        verify(configRepository).save(SERVICE_TYPE, config);
    }
}
