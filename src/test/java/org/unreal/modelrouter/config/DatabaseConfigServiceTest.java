package org.unreal.modelrouter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.store.entity.ConfigMainEntity;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import org.unreal.modelrouter.store.entity.ServiceConfigEntity;
import org.unreal.modelrouter.store.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.store.repository.ConfigMainRepository;
import org.unreal.modelrouter.store.repository.ConfigVersionRepository;
import org.unreal.modelrouter.store.repository.ServiceConfigRepository;
import org.unreal.modelrouter.store.repository.ServiceInstanceRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseConfigService 单元测试
 */
@DisplayName("DatabaseConfigService 单元测试")
class DatabaseConfigServiceTest {

    @Mock
    private ConfigMainRepository configMainRepository;

    @Mock
    private ConfigVersionRepository configVersionRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    private ObjectMapper objectMapper;

    private DatabaseConfigService databaseConfigService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        databaseConfigService = new DatabaseConfigService(
                configMainRepository,
                configVersionRepository,
                serviceConfigRepository,
                serviceInstanceRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("获取当前配置 - 成功场景")
    void getCurrentConfig_Success() {
        // 准备测试数据
        Integer testVersion = 5;
        String configJson = "{\"model\":{\"services\":{\"chat\":{\"adapter\":\"gpustack\"}}}}";

        ConfigMainEntity configMain = ConfigMainEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .currentVersion(testVersion)
                .build();

        ConfigVersionEntity versionEntity = ConfigVersionEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .version(testVersion)
                .configData(configJson)
                .isCurrent(true)
                .build();

        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(configMain));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(versionEntity));

        // 执行测试
        Map<String, Object> result = databaseConfigService.getCurrentConfig();

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("model"));
    }

    @Test
    @DisplayName("获取当前配置 - 配置不存在")
    void getCurrentConfig_NotExist() {
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = databaseConfigService.getCurrentConfig();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取所有服务配置 - 成功场景")
    void getAllServiceConfigs_Success() {
        // 准备测试数据
        ServiceConfigEntity chatConfig = ServiceConfigEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .serviceType("chat")
                .adapter("gpustack")
                .loadBalanceType("least-connections")
                .loadBalanceHashAlgorithm("md5")
                .rateLimitEnabled(true)
                .rateLimitAlgorithm("token-bucket")
                .rateLimitCapacity(100)
                .rateLimitRate(10)
                .rateLimitScope("service")
                .rateLimitClientIpEnable(true)
                .circuitBreakerEnabled(true)
                .circuitBreakerFailureThreshold(5)
                .circuitBreakerTimeout(60000)
                .circuitBreakerSuccessThreshold(2)
                .fallbackEnabled(true)
                .fallbackStrategy("default")
                .isLatest(true)
                .build();

        ServiceInstanceEntity instance = ServiceInstanceEntity.builder()
                .id(1L)
                .serviceConfigId(1L)
                .instanceName("qwen3:4b")
                .baseUrl("http://172.16.30.6:9090")
                .path("/v1/chat/completions")
                .weight(1)
                .status("ACTIVE")
                .healthStatus("HEALTHY")
                .build();

        when(serviceConfigRepository.findAllLatestByConfigKey(anyString()))
                .thenReturn(Flux.just(chatConfig));
        when(serviceInstanceRepository.findAllByServiceConfigId(1L))
                .thenReturn(Flux.just(instance));

        // 执行测试
        Map<String, Object> result = databaseConfigService.getAllServiceConfigs();

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("chat"));
        @SuppressWarnings("unchecked")
        Map<String, Object> chatService = (Map<String, Object>) result.get("chat");
        assertEquals("gpustack", chatService.get("adapter"));
        assertTrue(chatService.containsKey("instances"));
    }

    @Test
    @DisplayName("获取指定服务配置 - 成功场景")
    void getServiceConfig_Success() {
        ServiceConfigEntity chatConfig = ServiceConfigEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .serviceType("chat")
                .adapter("gpustack")
                .loadBalanceType("least-connections")
                .isLatest(true)
                .build();

        when(serviceConfigRepository.findLatestByConfigKeyAndServiceType(anyString(), anyString()))
                .thenReturn(Mono.just(chatConfig));
        when(serviceInstanceRepository.findAllByServiceConfigId(1L))
                .thenReturn(Flux.empty());

        Map<String, Object> result = databaseConfigService.getServiceConfig("chat");

        assertNotNull(result);
        assertEquals("gpustack", result.get("adapter"));
        assertTrue(result.containsKey("loadBalance"));
    }

    @Test
    @DisplayName("获取指定服务配置 - 服务不存在")
    void getServiceConfig_NotExist() {
        when(serviceConfigRepository.findLatestByConfigKeyAndServiceType(anyString(), anyString()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = databaseConfigService.getServiceConfig("nonexistent");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取配置元数据 - 成功场景")
    void getConfigMetadata_Success() {
        LocalDateTime now = LocalDateTime.now();
        ConfigMainEntity configMain = ConfigMainEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .currentVersion(5)
                .initialVersion(1)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("system")
                .updatedBy("admin")
                .description("Test config")
                .build();

        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(configMain));

        Map<String, Object> metadata = databaseConfigService.getConfigMetadata();

        assertNotNull(metadata);
        assertEquals(5, metadata.get("currentVersion"));
        assertEquals("admin", metadata.get("updatedBy"));
        assertEquals("Test config", metadata.get("description"));
    }

    @Test
    @DisplayName("获取配置元数据 - 不存在")
    void getConfigMetadata_NotExist() {
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.empty());

        Map<String, Object> metadata = databaseConfigService.getConfigMetadata();

        assertNotNull(metadata);
        assertTrue(metadata.isEmpty());
    }

    @Test
    @DisplayName("获取所有版本号 - 成功场景")
    void getAllVersions_Success() {
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.just(1, 2, 3, 4, 5));

        List<Integer> versions = databaseConfigService.getAllVersions();

        assertNotNull(versions);
        assertEquals(5, versions.size());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), versions);
    }

    @Test
    @DisplayName("获取所有版本号 - 异常场景")
    void getAllVersions_Error() {
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.error(new RuntimeException("Database error")));

        List<Integer> versions = databaseConfigService.getAllVersions();

        assertNotNull(versions);
        assertTrue(versions.isEmpty());
    }

    @Test
    @DisplayName("获取当前版本号 - 成功场景")
    void getCurrentVersion_Success() {
        ConfigMainEntity configMain = ConfigMainEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .currentVersion(10)
                .build();

        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(configMain));

        Integer version = databaseConfigService.getCurrentVersion();

        assertNotNull(version);
        assertEquals(10, version);
    }

    @Test
    @DisplayName("获取当前版本号 - 不存在")
    void getCurrentVersion_NotExist() {
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.empty());

        Integer version = databaseConfigService.getCurrentVersion();

        assertNotNull(version);
        assertEquals(0, version);
    }

    @Test
    @DisplayName("构建服务配置 Map - 包含完整配置")
    void buildServiceConfigMap_Complete() {
        ServiceConfigEntity serviceConfig = ServiceConfigEntity.builder()
                .id(1L)
                .serviceType("embedding")
                .loadBalanceType("round-robin")
                .loadBalanceHashAlgorithm("sha256")
                .adapter("vllm")
                .rateLimitEnabled(true)
                .rateLimitAlgorithm("token-bucket")
                .rateLimitCapacity(200)
                .rateLimitRate(20)
                .rateLimitScope("service")
                .rateLimitClientIpEnable(true)
                .circuitBreakerEnabled(true)
                .circuitBreakerFailureThreshold(10)
                .circuitBreakerTimeout(30000)
                .circuitBreakerSuccessThreshold(3)
                .fallbackEnabled(true)
                .fallbackStrategy("cache")
                .fallbackCacheSize(50)
                .fallbackCacheTtl(600000)
                .isLatest(true)
                .build();

        when(serviceInstanceRepository.findAllByServiceConfigId(1L))
                .thenReturn(Flux.empty());

        Map<String, Object> result = databaseConfigService.buildServiceConfigMapForTest(serviceConfig);

        assertNotNull(result);
        assertEquals("vllm", result.get("adapter"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> loadBalance = (Map<String, Object>) result.get("loadBalance");
        assertEquals("round-robin", loadBalance.get("type"));
        assertEquals("sha256", loadBalance.get("hashAlgorithm"));

        @SuppressWarnings("unchecked")
        Map<String, Object> rateLimit = (Map<String, Object>) result.get("rateLimit");
        assertEquals(200, rateLimit.get("capacity"));
        assertTrue((Boolean) rateLimit.get("enabled"));

        @SuppressWarnings("unchecked")
        Map<String, Object> circuitBreaker = (Map<String, Object>) result.get("circuitBreaker");
        assertEquals(10, circuitBreaker.get("failureThreshold"));

        @SuppressWarnings("unchecked")
        Map<String, Object> fallback = (Map<String, Object>) result.get("fallback");
        assertEquals("cache", fallback.get("strategy"));
        assertEquals(50, fallback.get("cacheSize"));
    }

    @Test
    @DisplayName("构建实例 Map - 包含 headers")
    void buildInstanceMap_WithHeaders() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer test-token");
        headers.put("Content-Type", "application/json");

        String headersJson = objectMapper.writeValueAsString(headers);

        ServiceInstanceEntity instance = ServiceInstanceEntity.builder()
                .id(1L)
                .serviceConfigId(1L)
                .instanceName("test-instance")
                .baseUrl("http://localhost:8080")
                .path("/v1/chat")
                .weight(2)
                .status("ACTIVE")
                .healthStatus("HEALTHY")
                .headers(headersJson)
                .rateLimitEnabled(true)
                .rateLimitAlgorithm("token-bucket")
                .rateLimitCapacity(50)
                .rateLimitRate(5)
                .rateLimitScope("instance")
                .build();

        Map<String, Object> result = databaseConfigService.buildInstanceMapForTest(instance);

        assertNotNull(result);
        assertEquals("test-instance", result.get("name"));
        assertEquals("http://localhost:8080", result.get("baseUrl"));
        assertEquals(2, result.get("weight"));
        assertTrue(result.containsKey("headers"));
        assertTrue(result.containsKey("rateLimit"));
    }
}
