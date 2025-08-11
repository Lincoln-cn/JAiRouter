package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.controller.ConfigurationVersionController;
import org.unreal.modelrouter.controller.response.RouterResponse;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConfigurationVersionTests {

    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private ConfigurationVersionController configurationVersionController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetConfigVersions_Success() {
        // Given
        List<Integer> versions = Arrays.asList(1, 2, 3);
        when(configurationService.getAllVersions()).thenReturn(versions);

        // When
        ResponseEntity<RouterResponse<List<Integer>>> response = configurationVersionController.getConfigVersions();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(versions, response.getBody().getData());
        verify(configurationService, times(1)).getAllVersions();
    }

    @Test
    void testGetConfigVersions_Exception() {
        // Given
        when(configurationService.getAllVersions()).thenThrow(new RuntimeException("Service error"));

        // When
        ResponseEntity<RouterResponse<List<Integer>>> response = configurationVersionController.getConfigVersions();

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("获取配置版本列表失败"));
        verify(configurationService, times(1)).getAllVersions();
    }

    @Test
    void testGetConfigByVersion_Success() {
        // Given
        int version = 1;
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");
        when(configurationService.getVersionConfig(version)).thenReturn(config);

        // When
        ResponseEntity<RouterResponse<Map<String, Object>>> response = configurationVersionController.getConfigByVersion(version);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(config, response.getBody().getData());
        verify(configurationService, times(1)).getVersionConfig(version);
    }

    @Test
    void testGetConfigByVersion_NotFound() {
        // Given
        int version = 99;
        when(configurationService.getVersionConfig(version)).thenReturn(null);

        // When
        ResponseEntity<RouterResponse<Map<String, Object>>> response = configurationVersionController.getConfigByVersion(version);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("指定版本的配置不存在"));
        verify(configurationService, times(1)).getVersionConfig(version);
    }

    @Test
    void testRollbackToVersion_Success() {
        // Given
        int version = 2;
        doNothing().when(configurationService).applyVersion(version);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.rollbackToVersion(version);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("配置已成功回滚到版本"));
        verify(configurationService, times(1)).applyVersion(version);
    }

    @Test
    void testRollbackToVersion_IllegalArgumentException() {
        // Given
        int version = 99;
        doThrow(new IllegalArgumentException("Version not found")).when(configurationService).applyVersion(version);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.rollbackToVersion(version);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("回滚配置失败"));
        verify(configurationService, times(1)).applyVersion(version);
    }

    @Test
    void testDeleteConfigVersion_CurrentVersion() {
        // Given
        int version = 3;
        when(configurationService.getCurrentVersion()).thenReturn(version);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.deleteConfigVersion(version);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("不能删除当前版本"));
        verify(configurationService, times(1)).getCurrentVersion();
    }

    @Test
    void testDeleteConfigVersion_NotImplemented() {
        // Given
        int version = 2;
        when(configurationService.getCurrentVersion()).thenReturn(3);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.deleteConfigVersion(version);

        // Then
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("删除配置版本功能暂未实现"));
        verify(configurationService, times(1)).getCurrentVersion();
    }

    @Test
    void testGetCurrentVersion_Success() {
        // Given
        int currentVersion = 3;
        when(configurationService.getCurrentVersion()).thenReturn(currentVersion);

        // When
        ResponseEntity<RouterResponse<Integer>> response = configurationVersionController.getCurrentVersion();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(currentVersion, response.getBody().getData().intValue());
        verify(configurationService, times(1)).getCurrentVersion();
    }

    @Test
    void testApplyVersion_Success() {
        // Given
        int version = 2;
        doNothing().when(configurationService).applyVersion(version);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.applyVersion(version);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("配置应用成功"));
        verify(configurationService, times(1)).applyVersion(version);
    }

    @Test
    void testApplyVersion_IllegalArgumentException() {
        // Given
        int version = 99;
        doThrow(new IllegalArgumentException("Invalid configuration")).when(configurationService).applyVersion(version);

        // When
        ResponseEntity<RouterResponse<Void>> response = configurationVersionController.applyVersion(version);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("配置内容不合法"));
        verify(configurationService, times(1)).applyVersion(version);
    }
    @Test
    void testConfigurationVersionWorkflow() {
        // 01 创建服务（触发新版本）
        // 模拟创建服务的请求参数
        Map<String, Object> serviceConfig = new HashMap<>();
        List<Map<String, Object>> instances = new ArrayList<>();
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "gpt-4-postman");
        instance.put("baseUrl", "http://gpt4.postman.test");
        instance.put("weight", 10);
        instances.add(instance);
        serviceConfig.put("instances", instances);
        
        // 由于创建服务不是当前Controller的方法，这里仅验证版本管理流程
        // 假设服务创建后，会生成版本1
        int newVersion = 1;
        when(configurationService.saveAsNewVersion(anyMap())).thenReturn(newVersion);
        
        // 02 获取版本列表
        List<Integer> versions = Arrays.asList(1);
        when(configurationService.getAllVersions()).thenReturn(versions);
        ResponseEntity<RouterResponse<List<Integer>>> versionsResponse = configurationVersionController.getConfigVersions();
        assertEquals(HttpStatus.OK, versionsResponse.getStatusCode());
        assertTrue(versionsResponse.getBody().isSuccess());
        assertEquals(versions, versionsResponse.getBody().getData());
        
        // 03 查看版本详情（指定版本）
        Map<String, Object> versionConfig = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        services.put("chat", serviceConfig);
        versionConfig.put("services", services);
        when(configurationService.getVersionConfig(1)).thenReturn(versionConfig);
        ResponseEntity<RouterResponse<Map<String, Object>>> versionDetailResponse = configurationVersionController.getConfigByVersion(1);
        assertEquals(HttpStatus.OK, versionDetailResponse.getStatusCode());
        assertTrue(versionDetailResponse.getBody().isSuccess());
        assertEquals(versionConfig, versionDetailResponse.getBody().getData());
        
        // 04 应用版本（热修改）
        doNothing().when(configurationService).applyVersion(1);
        ResponseEntity<RouterResponse<Void>> applyResponse = configurationVersionController.applyVersion(1);
        assertEquals(HttpStatus.OK, applyResponse.getStatusCode());
        assertTrue(applyResponse.getBody().isSuccess());
        assertTrue(applyResponse.getBody().getMessage().contains("配置应用成功"));
        
        // 05 验证热修改已生效
        // 这部分需要ConfigurationService返回更新后的配置
        when(configurationService.getAllConfigurations()).thenReturn(versionConfig);
        // 注意：getServiceConfig方法在ConfigurationService中，不在当前Controller中
        // 所以我们只验证版本应用是否成功调用
        verify(configurationService, times(1)).applyVersion(1);
    }
}
