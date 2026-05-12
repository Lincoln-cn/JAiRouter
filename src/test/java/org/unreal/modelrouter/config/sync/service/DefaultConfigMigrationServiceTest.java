package org.unreal.modelrouter.config.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DefaultConfigMigrationService 单元测试.
 *
 * @since v2.6.12
 */
@DisplayName("DefaultConfigMigrationService 测试")
@ExtendWith(MockitoExtension.class)
class DefaultConfigMigrationServiceTest {

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @InjectMocks
    private DefaultConfigMigrationService migrationService;

    private ServiceConfigEntity testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new ServiceConfigEntity();
        testConfig.setId(1L);
        testConfig.setServiceType("chat");
        testConfig.setAdapter("ollama");
        testConfig.setLoadBalanceType("round-robin");
        testConfig.setIsLatest(true);
    }

    @Test
    @DisplayName("导出空配置应返回失败")
    void testExportEmptyConfig() {
        when(serviceConfigRepository.findAll())
            .thenReturn(Collections.emptyList());

        var result = migrationService.exportConfig("dev", "prod", null)
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("No configs found"));
    }

    @Test
    @DisplayName("导出配置成功")
    void testExportConfigSuccess() {
        when(serviceConfigRepository.findAll())
            .thenReturn(List.of(testConfig));

        var result = migrationService.exportConfig("dev", "prod", new String[]{"chat"})
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(1, result.migratedCount());
    }

    @Test
    @DisplayName("导出配置过滤服务类型")
    void testExportConfigWithServiceTypeFilter() {
        ServiceConfigEntity embeddingConfig = new ServiceConfigEntity();
        embeddingConfig.setId(2L);
        embeddingConfig.setServiceType("embedding");

        when(serviceConfigRepository.findAll())
            .thenReturn(List.of(testConfig, embeddingConfig));

        var result = migrationService.exportConfig("dev", "prod", new String[]{"chat"})
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(1, result.migratedCount());
    }

    @Test
    @DisplayName("导入空数据应返回失败")
    void testImportEmptyData() {
        var result = migrationService.importConfig(Collections.emptyMap(), "prod")
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("Empty backup data"));
    }

    @Test
    @DisplayName("导入配置成功")
    void testImportConfigSuccess() {
        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
            .thenReturn(Optional.empty());
        when(serviceConfigRepository.save(any(ServiceConfigEntity.class)))
            .thenReturn(testConfig);

        Map<String, Object> backupData = new HashMap<>();
        backupData.put("chat", Map.of("adapter", "ollama"));

        var result = migrationService.importConfig(backupData, "prod")
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(1, result.migratedCount());
    }

    @Test
    @DisplayName("导入配置更新现有服务")
    void testImportConfigUpdateExisting() {
        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
            .thenReturn(Optional.of(testConfig));
        when(serviceConfigRepository.save(any(ServiceConfigEntity.class)))
            .thenReturn(testConfig);

        Map<String, Object> backupData = new HashMap<>();
        backupData.put("chat", Map.of("adapter", "new-adapter"));

        var result = migrationService.importConfig(backupData, "prod")
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        verify(serviceConfigRepository).save(any(ServiceConfigEntity.class));
    }
}
