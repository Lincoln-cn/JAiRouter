package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.controller.AutoMergeController;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.store.AutoMergeService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 自动合并控制器测试
 */
@ExtendWith(MockitoExtension.class)
class AutoMergeControllerTest {

    @Mock
    private AutoMergeService autoMergeService;

    private AutoMergeController autoMergeController;

    @BeforeEach
    void setUp() {
        autoMergeController = new AutoMergeController(autoMergeService);
    }

    @Test
    void testScanVersionFiles_Success() {
        // 准备测试数据
        Map<Integer, String> mockVersionFiles = new TreeMap<>();
        mockVersionFiles.put(1, "config/model-router-config@1.json");
        mockVersionFiles.put(2, "config/model-router-config@2.json");
        
        when(autoMergeService.scanVersionFiles()).thenReturn(mockVersionFiles);

        // 执行测试
        ResponseEntity<RouterResponse<Map<Integer, String>>> response = autoMergeController.scanVersionFiles();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().size());
        assertTrue(response.getBody().getMessage().contains("2 个版本配置文件"));
        
        verify(autoMergeService, times(1)).scanVersionFiles();
    }

    @Test
    void testScanVersionFiles_Exception() {
        // 模拟异常
        when(autoMergeService.scanVersionFiles()).thenThrow(new RuntimeException("扫描失败"));

        // 执行测试
        ResponseEntity<RouterResponse<Map<Integer, String>>> response = autoMergeController.scanVersionFiles();

        // 验证结果
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("扫描失败"));
        
        verify(autoMergeService, times(1)).scanVersionFiles();
    }

    @Test
    void testGetMergePreview_Success() {
        // 准备测试数据
        Map<String, Object> mockPreview = new HashMap<>();
        mockPreview.put("mergedConfig", Map.of("services", Map.of()));
        mockPreview.put("totalFiles", 2);
        
        when(autoMergeService.getMergePreview()).thenReturn(mockPreview);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = autoMergeController.getMergePreview();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("预览生成成功", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).getMergePreview();
    }

    @Test
    void testGetMergePreview_WithError() {
        // 准备测试数据 - 包含错误的预览
        Map<String, Object> mockPreview = new HashMap<>();
        mockPreview.put("error", "没有找到配置文件");
        
        when(autoMergeService.getMergePreview()).thenReturn(mockPreview);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = autoMergeController.getMergePreview();

        // 验证结果
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("没有找到配置文件"));
        
        verify(autoMergeService, times(1)).getMergePreview();
    }

    @Test
    void testPerformAutoMerge_Success() {
        // 准备测试数据
        AutoMergeService.MergeResult mockResult = new AutoMergeService.MergeResult(
                true, "合并成功", 2, 1, 
                Arrays.asList("file1.json", "file2.json"), 
                new ArrayList<>());
        
        when(autoMergeService.performAutoMerge()).thenReturn(mockResult);

        // 执行测试
        ResponseEntity<RouterResponse<AutoMergeService.MergeResult>> response = autoMergeController.performAutoMerge();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("合并成功", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).performAutoMerge();
    }

    @Test
    void testPerformAutoMerge_Failure() {
        // 准备测试数据
        AutoMergeService.MergeResult mockResult = new AutoMergeService.MergeResult(
                false, "合并失败", 0, 0, 
                new ArrayList<>(), 
                Arrays.asList("错误信息"));
        
        when(autoMergeService.performAutoMerge()).thenReturn(mockResult);

        // 执行测试
        ResponseEntity<RouterResponse<AutoMergeService.MergeResult>> response = autoMergeController.performAutoMerge();

        // 验证结果
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("合并失败", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).performAutoMerge();
    }

    @Test
    void testBackupConfigFiles_Success() {
        // 准备测试数据
        AutoMergeService.MergeResult mockResult = new AutoMergeService.MergeResult(
                true, "备份成功", 2, 0, 
                Arrays.asList("backup/file1.json", "backup/file2.json"), 
                new ArrayList<>());
        
        when(autoMergeService.backupConfigFiles()).thenReturn(mockResult);

        // 执行测试
        ResponseEntity<RouterResponse<AutoMergeService.MergeResult>> response = autoMergeController.backupConfigFiles();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("备份成功", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).backupConfigFiles();
    }

    @Test
    void testCleanupConfigFiles_Success() {
        // 准备测试数据
        AutoMergeService.MergeResult mockResult = new AutoMergeService.MergeResult(
                true, "清理成功", 2, 0, 
                Arrays.asList("file1.json", "file2.json"), 
                new ArrayList<>());
        
        when(autoMergeService.cleanupConfigFiles(true)).thenReturn(mockResult);

        // 执行测试
        ResponseEntity<RouterResponse<AutoMergeService.MergeResult>> response = 
                autoMergeController.cleanupConfigFiles(true);

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("清理成功", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).cleanupConfigFiles(true);
    }

    @Test
    void testGetServiceStatus_Success() {
        // 准备测试数据
        Map<Integer, String> mockVersionFiles = new TreeMap<>();
        mockVersionFiles.put(1, "config/model-router-config@1.json");
        
        when(autoMergeService.scanVersionFiles()).thenReturn(mockVersionFiles);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = autoMergeController.getServiceStatus();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("状态获取成功", response.getBody().getMessage());
        
        Map<String, Object> statusData = response.getBody().getData();
        assertEquals(1, statusData.get("availableVersionFiles"));
        assertEquals("config", statusData.get("configDirectory"));
        assertEquals(true, statusData.get("serviceReady"));
        
        verify(autoMergeService, times(1)).scanVersionFiles();
    }

    @Test
    void testPerformBatchOperation_Success() {
        // 准备测试数据
        AutoMergeService.MergeResult backupResult = new AutoMergeService.MergeResult(
                true, "备份成功", 2, 0, Arrays.asList("backup1", "backup2"), new ArrayList<>());
        AutoMergeService.MergeResult mergeResult = new AutoMergeService.MergeResult(
                true, "合并成功", 2, 1, Arrays.asList("file1", "file2"), new ArrayList<>());
        AutoMergeService.MergeResult cleanupResult = new AutoMergeService.MergeResult(
                true, "清理成功", 2, 0, Arrays.asList("file1", "file2"), new ArrayList<>());
        
        when(autoMergeService.backupConfigFiles()).thenReturn(backupResult);
        when(autoMergeService.performAutoMerge()).thenReturn(mergeResult);
        when(autoMergeService.cleanupConfigFiles(true)).thenReturn(cleanupResult);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = 
                autoMergeController.performBatchOperation(true);

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("批量操作完成", response.getBody().getMessage());
        
        verify(autoMergeService, times(1)).backupConfigFiles();
        verify(autoMergeService, times(1)).performAutoMerge();
        verify(autoMergeService, times(1)).cleanupConfigFiles(true);
    }

    @Test
    void testValidateConfigFiles_Success() {
        // 准备测试数据
        Map<Integer, String> mockVersionFiles = new TreeMap<>();
        mockVersionFiles.put(1, "config/model-router-config@1.json");
        mockVersionFiles.put(2, "config/model-router-config@2.json");
        
        when(autoMergeService.scanVersionFiles()).thenReturn(mockVersionFiles);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = autoMergeController.validateConfigFiles();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("2 个文件"));
        
        Map<String, Object> validationResult = response.getBody().getData();
        assertEquals(2, validationResult.get("totalFiles"));
        assertEquals(2, validationResult.get("validFiles"));
        assertEquals(0, validationResult.get("invalidFiles"));
        
        verify(autoMergeService, times(1)).scanVersionFiles();
    }

    @Test
    void testGetConfigStatistics_Success() {
        // 准备测试数据
        Map<Integer, String> mockVersionFiles = new TreeMap<>();
        mockVersionFiles.put(1, "config/model-router-config@1.json");
        mockVersionFiles.put(3, "config/model-router-config@3.json");
        
        Map<String, Object> mockPreview = new HashMap<>();
        mockPreview.put("mergedConfig", Map.of());
        
        when(autoMergeService.scanVersionFiles()).thenReturn(mockVersionFiles);
        when(autoMergeService.getMergePreview()).thenReturn(mockPreview);

        // 执行测试
        ResponseEntity<RouterResponse<Map<String, Object>>> response = autoMergeController.getConfigStatistics();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("统计信息获取成功", response.getBody().getMessage());
        
        Map<String, Object> statistics = response.getBody().getData();
        assertEquals(2, statistics.get("totalVersionFiles"));
        assertEquals(1, statistics.get("oldestVersion"));
        assertEquals(3, statistics.get("newestVersion"));
        assertEquals(true, statistics.get("previewAvailable"));
        assertEquals("config", statistics.get("configDirectory"));
        
        verify(autoMergeService, times(1)).scanVersionFiles();
        verify(autoMergeService, times(1)).getMergePreview();
    }
}