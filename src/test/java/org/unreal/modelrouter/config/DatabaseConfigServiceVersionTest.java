package org.unreal.modelrouter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.store.entity.ConfigMainEntity;
import org.unreal.modelrouter.store.entity.ConfigVersionEntity;
import org.unreal.modelrouter.store.repository.ConfigMainRepository;
import org.unreal.modelrouter.store.repository.ConfigVersionRepository;
import org.unreal.modelrouter.store.repository.ServiceConfigRepository;
import org.unreal.modelrouter.store.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.store.repository.ConfigArchiveRepository;
import org.unreal.modelrouter.store.repository.ConfigChangeHistoryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseConfigService 版本管理功能单元测试
 */
@DisplayName("DatabaseConfigService 版本管理功能单元测试")
class DatabaseConfigServiceVersionTest {

    @Mock
    private ConfigMainRepository configMainRepository;

    @Mock
    private ConfigVersionRepository configVersionRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @Mock
    private ConfigArchiveRepository configArchiveRepository;

    @Mock
    private ConfigChangeHistoryRepository configChangeHistoryRepository;

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
                objectMapper,
                configArchiveRepository,
                configChangeHistoryRepository
        );
    }

    @Test
    @DisplayName("获取版本配置 - 成功场景")
    void getVersionConfig_Success() {
        // 准备测试数据
        Integer testVersion = 3;
        String configJson = "{\"model\":{\"services\":{\"chat\":{\"adapter\":\"gpustack\"}}}}";

        ConfigVersionEntity versionEntity = ConfigVersionEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .version(testVersion)
                .configData(configJson)
                .isCurrent(false)
                .build();

        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(versionEntity));

        // 执行测试
        Map<String, Object> result = databaseConfigService.getVersionConfig(testVersion);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("model"));
    }

    @Test
    @DisplayName("获取版本配置 - 版本不存在")
    void getVersionConfig_NotExist() {
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.empty());

        Map<String, Object> result = databaseConfigService.getVersionConfig(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("保存新版本 - 成功场景")
    void saveAsNewVersion_Success() throws Exception {
        // 准备测试数据
        Map<String, Object> config = new HashMap<>();
        config.put("model", new HashMap<>());
        
        ConfigMainEntity existingMain = ConfigMainEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .currentVersion(5)
                .build();

        ConfigVersionEntity savedVersion = ConfigVersionEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .version(6)
                .configData(objectMapper.writeValueAsString(config))
                .isCurrent(true)
                .build();

        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(existingMain));
        when(configVersionRepository.markAllAsNotCurrent(anyString()))
                .thenReturn(Mono.just(1));
        when(configVersionRepository.save(any()))
                .thenReturn(Mono.just(savedVersion));
        when(configMainRepository.updateCurrentVersion(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(1));

        // 执行测试
        Integer newVersion = databaseConfigService.saveAsNewVersion(config, "测试版本", "admin");

        // 验证结果
        assertNotNull(newVersion);
        assertEquals(6, newVersion.intValue());
    }

    @Test
    @DisplayName("保存新版本 - 首次创建")
    void saveAsNewVersion_FirstCreate() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("model", new HashMap<>());

        ConfigMainEntity newMain = ConfigMainEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .initialVersion(1)
                .currentVersion(1)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .description("初始版本")
                .build();

        ConfigVersionEntity savedVersion = ConfigVersionEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .version(1)
                .configData(objectMapper.writeValueAsString(config))
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .description("初始版本")
                .changeType("UPDATE")
                .isCurrent(true)
                .isArchived(false)
                .build();

        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.empty());
        when(configMainRepository.save(any()))
                .thenReturn(Mono.just(newMain));
        when(configVersionRepository.markAllAsNotCurrent(anyString()))
                .thenReturn(Mono.just(0));
        when(configVersionRepository.save(any()))
                .thenReturn(Mono.just(savedVersion));
        when(configMainRepository.updateCurrentVersion(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(1));

        Integer newVersion = databaseConfigService.saveAsNewVersion(config, "初始版本", "system");

        assertNotNull(newVersion, "新版本号不应为 null");
        assertEquals(1, newVersion.intValue(), "首次创建版本号应为 1");
    }

    @Test
    @DisplayName("应用版本 - 成功场景")
    void applyVersion_Success() throws Exception {
        Integer testVersion = 3;

        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(ConfigVersionEntity.builder()
                        .version(testVersion)
                        .configData("{\"model\":{}}")
                        .build()));
        when(configVersionRepository.markAllAsNotCurrent(anyString()))
                .thenReturn(Mono.just(1));
        when(configVersionRepository.markAsCurrent(anyString(), anyInt()))
                .thenReturn(Mono.just(1));
        when(configMainRepository.updateCurrentVersion(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(1));

        // 执行测试（不应该抛出异常）
        assertDoesNotThrow(() -> 
            databaseConfigService.applyVersion(testVersion, "admin")
        );
    }

    @Test
    @DisplayName("应用版本 - 版本不存在")
    void applyVersion_VersionNotFound() {
        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(false));

        // 异常会被包装成 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            databaseConfigService.applyVersion(999, "admin")
        );
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("应用版本 - 配置数据为空")
    void applyVersion_EmptyConfig() {
        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(ConfigVersionEntity.builder()
                        .version(3)
                        .configData(null)
                        .build()));

        // 异常会被包装成 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            databaseConfigService.applyVersion(3, "admin")
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("删除版本 - 成功场景")
    void deleteVersion_Success() throws Exception {
        Integer testVersion = 3;

        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(ConfigMainEntity.builder()
                        .currentVersion(5)
                        .build()));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(ConfigVersionEntity.builder()
                        .version(testVersion)
                        .isArchived(false)
                        .build()));
        when(configVersionRepository.deleteByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(1));
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.just(1, 2, 3, 4, 5));

        // 执行测试（不应该抛出异常）
        assertDoesNotThrow(() -> 
            databaseConfigService.deleteVersion(testVersion, "admin")
        );
    }

    @Test
    @DisplayName("删除版本 - 当前版本不能删除")
    void deleteVersion_CannotDeleteCurrent() {
        Integer testVersion = 5;

        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(ConfigMainEntity.builder()
                        .currentVersion(testVersion)
                        .build()));
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.just(1, 2, 3, 4, 5));

        // 异常会被包装成 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            databaseConfigService.deleteVersion(testVersion, "admin")
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("删除版本 - 不能删除最后一个版本")
    void deleteVersion_CannotDeleteLast() {
        Integer testVersion = 1;

        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(ConfigMainEntity.builder()
                        .currentVersion(2)
                        .build()));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(ConfigVersionEntity.builder()
                        .version(testVersion)
                        .isArchived(false)
                        .build()));
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.just(1));

        // 异常会被包装成 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            databaseConfigService.deleteVersion(testVersion, "admin")
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("删除版本 - 已归档版本不能删除")
    void deleteVersion_ArchivedCannotDelete() {
        Integer testVersion = 2;

        when(configVersionRepository.existsByVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(true));
        when(configMainRepository.findByConfigKey(anyString()))
                .thenReturn(Mono.just(ConfigMainEntity.builder()
                        .currentVersion(5)
                        .build()));
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(ConfigVersionEntity.builder()
                        .version(testVersion)
                        .isArchived(true)
                        .build()));
        when(configVersionRepository.findAllVersionNumbers(anyString()))
                .thenReturn(Flux.just(1, 2, 3, 4, 5));

        // 异常会被包装成 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            databaseConfigService.deleteVersion(testVersion, "admin")
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("获取版本信息 - 成功场景")
    void getVersionInfo_Success() {
        Integer testVersion = 3;
        LocalDateTime now = LocalDateTime.now();

        ConfigVersionEntity versionEntity = ConfigVersionEntity.builder()
                .id(1L)
                .configKey("model-router-config")
                .version(testVersion)
                .createdAt(now)
                .createdBy("admin")
                .description("测试版本")
                .changeType("UPDATE")
                .isCurrent(true)
                .isArchived(false)
                .build();

        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.just(versionEntity));

        Map<String, Object> info = databaseConfigService.getVersionInfo(testVersion);

        assertNotNull(info);
        assertEquals(testVersion, info.get("version"));
        assertEquals("admin", info.get("createdBy"));
        assertEquals("测试版本", info.get("description"));
        assertEquals("UPDATE", info.get("changeType"));
        assertTrue((Boolean) info.get("isCurrent"));
        assertFalse((Boolean) info.get("isArchived"));
    }

    @Test
    @DisplayName("获取版本信息 - 版本不存在")
    void getVersionInfo_NotExist() {
        when(configVersionRepository.findByConfigKeyAndVersion(anyString(), anyInt()))
                .thenReturn(Mono.empty());

        Map<String, Object> info = databaseConfigService.getVersionInfo(999);

        assertNotNull(info);
        assertTrue(info.isEmpty());
    }
}
