package org.unreal.moduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.store.AutoMergeService;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 自动合并服务测试
 */
@ExtendWith(MockitoExtension.class)
class AutoMergeServiceTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ConfigurationService configurationService;

    private ObjectMapper objectMapper;
    private AutoMergeService autoMergeService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        autoMergeService = new AutoMergeService(objectMapper, storeManager, configurationService);
    }

    @Test
    void testScanVersionFiles() {
        // 测试扫描版本文件功能
        Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
        
        // 验证返回的是有序的Map
        assertNotNull(versionFiles);
        assertTrue(versionFiles instanceof TreeMap);
    }

    @Test
    void testGetMergePreview() {
        // 测试获取合并预览功能
        Map<String, Object> preview = autoMergeService.getMergePreview();
        
        assertNotNull(preview);
        // 由于没有实际的配置文件，预览可能为空或包含错误信息
    }

    @Test
    void testBackupConfigFiles() {
        // 测试备份配置文件功能
        AutoMergeService.MergeResult result = autoMergeService.backupConfigFiles();
        
        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertNotNull(result.getMergedFiles());
        assertNotNull(result.getErrors());
    }

    @Test
    void testCleanupConfigFiles() {
        // 测试清理配置文件功能
        AutoMergeService.MergeResult result = autoMergeService.cleanupConfigFiles(false);
        
        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertNotNull(result.getMergedFiles());
        assertNotNull(result.getErrors());
    }

    @Test
    void testPerformAutoMergeWithNoFiles() {
        // 测试在没有配置文件时的自动合并
        AutoMergeService.MergeResult result = autoMergeService.performAutoMerge();
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("未找到任何版本配置文件", result.getMessage());
        assertEquals(0, result.getMergedFilesCount());
        assertEquals(0, result.getNewVersionCount());
    }

    @Test
    void testMergeResultConstructor() {
        // 测试 MergeResult 构造函数
        List<String> files = Arrays.asList("file1.json", "file2.json");
        List<String> errors = Arrays.asList("error1", "error2");
        
        AutoMergeService.MergeResult result = new AutoMergeService.MergeResult(
                true, "测试消息", 2, 1, files, errors);
        
        assertTrue(result.isSuccess());
        assertEquals("测试消息", result.getMessage());
        assertEquals(2, result.getMergedFilesCount());
        assertEquals(1, result.getNewVersionCount());
        assertEquals(files, result.getMergedFiles());
        assertEquals(errors, result.getErrors());
    }

    @Test
    void testMergeResultWithNullLists() {
        // 测试 MergeResult 构造函数处理 null 列表
        AutoMergeService.MergeResult result = new AutoMergeService.MergeResult(
                false, "测试消息", 0, 0, null, null);
        
        assertFalse(result.isSuccess());
        assertEquals("测试消息", result.getMessage());
        assertEquals(0, result.getMergedFilesCount());
        assertEquals(0, result.getNewVersionCount());
        assertNotNull(result.getMergedFiles());
        assertNotNull(result.getErrors());
        assertTrue(result.getMergedFiles().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }
}