package org.unreal.modelrouter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * #874：首次初始化按存储各自判断，失败时回滚，避免「半初始化 → 永久跳过」。
 */
@DisplayName("#874 首启初始化的自愈与回滚")
class JpaDatabaseInitializerPartialBootstrapTest {

    private ConfigRepository configRepository;
    private ServiceConfigRepository serviceConfigRepository;
    private ServiceInstanceRepository serviceInstanceRepository;
    private JpaDatabaseInitializer initializer;

    @BeforeEach
    void setUp() {
        configRepository = mock(ConfigRepository.class);
        serviceConfigRepository = mock(ServiceConfigRepository.class);
        serviceInstanceRepository = mock(ServiceInstanceRepository.class);
        final ConfigConverterHelper configConverterHelper = mock(ConfigConverterHelper.class);
        final ModelRouterProperties properties = new ModelRouterProperties();
        properties.setServices(Map.of("chat", serviceConfigWithOneInstance()));
        when(configConverterHelper.convertModelRouterPropertiesToMap(any()))
                .thenReturn(new HashMap<>(Map.of("services", Map.of())));
        initializer = new JpaDatabaseInitializer(
                configRepository,
                serviceConfigRepository,
                serviceInstanceRepository,
                mock(JwtAccountRepository.class),
                properties,
                configConverterHelper,
                mock(ApiKeyService.class),
                new SecurityProperties(),
                mock(PasswordEncoder.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("874-001 两个存储都空时，配置与服务配置都会写入")
    void emptyDatabaseInitializesBothStores() {
        when(configRepository.count()).thenReturn(0L);
        when(serviceConfigRepository.count()).thenReturn(0L);
        saveReturnsId(7L);

        invokeInitializeModelConfig();

        verify(configRepository, times(1)).save(any());
        verify(serviceConfigRepository, times(1)).save(any(ServiceConfigEntity.class));
        verify(serviceInstanceRepository, times(1)).save(any(ServiceInstanceEntity.class));
    }

    @Test
    @DisplayName("874-002 配置已存在但服务配置为空时，仍然补写服务配置（不再永久跳过）")
    void partiallyInitializedDatabaseIsHealed() {
        when(configRepository.count()).thenReturn(1L);
        when(serviceConfigRepository.count()).thenReturn(0L);
        saveReturnsId(11L);

        invokeInitializeModelConfig();

        verify(configRepository, never()).save(any());
        verify(serviceConfigRepository, times(1)).save(any(ServiceConfigEntity.class));
        verify(serviceInstanceRepository, times(1)).save(any(ServiceInstanceEntity.class));
    }

    @Test
    @DisplayName("874-003 服务配置中途写失败时，回滚本次写入的配置与实例并抛错")
    void failureDuringServiceConfigsRollsBack() {
        when(configRepository.count()).thenReturn(1L);
        when(serviceConfigRepository.count()).thenReturn(0L);
        saveReturnsId(21L);
        when(serviceInstanceRepository.save(any(ServiceInstanceEntity.class)))
                .thenThrow(new IllegalStateException("数据库连接中断"));

        final RuntimeException failure = assertThrows(RuntimeException.class, this::invokeInitializeModelConfig);

        assertTrue(failure.getMessage().contains("Database initialization failed"),
                "异常应指明初始化失败，实际: " + failure.getMessage());
        verify(serviceInstanceRepository).findByServiceConfigId(21L);
        verify(serviceConfigRepository).deleteById(21L);
        verify(configRepository, never()).save(any());
    }

    private void saveReturnsId(final long configId) {
        when(serviceConfigRepository.save(any(ServiceConfigEntity.class))).thenAnswer(invocation -> {
            final ServiceConfigEntity entity = invocation.getArgument(0);
            return ServiceConfigEntity.builder()
                    .id(configId)
                    .configKey(entity.getConfigKey())
                    .serviceType(entity.getServiceType())
                    .adapter(entity.getAdapter())
                    .loadBalanceType(entity.getLoadBalanceType())
                    .version(1)
                    .isLatest(true)
                    .build();
        });
    }

    private void invokeInitializeModelConfig() {
        try {
            final Method method = JpaDatabaseInitializer.class.getDeclaredMethod("initializeModelConfig");
            method.setAccessible(true);
            method.invoke(initializer);
        } catch (ReflectiveOperationException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("调用 initializeModelConfig 失败", e);
        }
    }

    private static ModelRouterProperties.ServiceConfig serviceConfigWithOneInstance() {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("qwen3:4b");
        instance.setBaseUrl("http://localhost:11434");
        instance.setPath("/v1/chat/completions");
        final ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        serviceConfig.setInstances(List.of(instance));
        return serviceConfig;
    }
}
