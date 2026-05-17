package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.dto.*;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyManagementController RESTful 接口测试
 * 
 * 测试范围：
 * - GET /api/auth/api-keys - 获取所有API密钥
 * - GET /api/auth/api-keys/{keyId} - 获取指定API密钥
 * - POST /api/auth/api-keys - 创建API密钥
 * - PUT /api/auth/api-keys/{keyId} - 更新API密钥
 * - DELETE /api/auth/api-keys/{keyId} - 删除API密钥
 * - PATCH /api/auth/api-keys/{keyId}/disable - 禁用API密钥
 * - PATCH /api/auth/api-keys/{keyId}/enable - 启用API密钥
 * - POST /api/auth/api-keys/{keyId}/reset - 重置API密钥
 * - POST /api/auth/api-keys/{keyId}/rotate - 强制轮换API密钥
 * - GET /api/auth/api-keys/export - 批量导出
 * - POST /api/auth/api-keys/import - 批量导入
 * 
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("ApiKeyManagementController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class ApiKeyManagementControllerIntegrationTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private ApiKeyManagementController controller;

    private static final String BASE_URL = "api/auth/api-keys";
    private static final String TEST_KEY_ID = "test-key-001";
    private static final String TEST_KEY_VALUE = "sk-test-abc123xyz";

    private ApiKeyVO testApiKeyVO;
    private ApiKeyCreationVO testCreationVO;

    @BeforeEach
    void setUp() {
        // 创建测试用的 ApiKeyVO
        testApiKeyVO = ApiKeyVO.builder()
                .keyId(TEST_KEY_ID)
                .description("Test API Key")
                .enabled(true)
                .permissions(Arrays.asList("chat", "embedding"))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // 创建测试用的 ApiKeyCreationVO
        testCreationVO = ApiKeyCreationVO.builder()
                .keyId(TEST_KEY_ID)
                .keyValue(TEST_KEY_VALUE)
                .description("Test API Key")
                .enabled(true)
                .permissions(Arrays.asList("chat", "embedding"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== 获取所有API密钥测试 ====================

    @Nested
    @DisplayName("GET /api/auth/api-keys - 获取所有API密钥测试")
    class GetAllApiKeysTests {

        @Test
        @DisplayName("API-001: 获取所有API密钥成功")
        void testGetAllApiKeys_success() {
            // Given
            ApiKeyListVO listVO = ApiKeyListVO.builder()
                    .items(Arrays.asList(testApiKeyVO))
                    .total(1)
                    .build();

            when(apiKeyService.getAllApiKeysVO()).thenReturn(Mono.just(listVO));

            // When
            Mono<RouterResponse<ApiKeyListVO>> result = controller.getAllApiKeys();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(1, response.getData().getTotal());
                        assertEquals(1, response.getData().getItems().size());
                        assertEquals(TEST_KEY_ID, response.getData().getItems().get(0).getKeyId());
                        assertEquals("获取API密钥列表成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-002: 空API密钥列表")
        void testGetAllApiKeys_empty() {
            // Given
            ApiKeyListVO emptyList = ApiKeyListVO.builder()
                    .items(Collections.emptyList())
                    .total(0)
                    .build();

            when(apiKeyService.getAllApiKeysVO()).thenReturn(Mono.just(emptyList));

            // When
            Mono<RouterResponse<ApiKeyListVO>> result = controller.getAllApiKeys();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(0, response.getData().getTotal());
                        assertTrue(response.getData().getItems().isEmpty());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-003: 获取API密钥列表失败 - 服务异常")
        void testGetAllApiKeys_error() {
            // Given
            when(apiKeyService.getAllApiKeysVO())
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When
            Mono<RouterResponse<ApiKeyListVO>> result = controller.getAllApiKeys();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("INTERNAL_ERROR", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 获取指定API密钥测试 ====================

    @Nested
    @DisplayName("GET /api/auth/api-keys/{keyId} - 获取指定API密钥测试")
    class GetApiKeyByIdTests {

        @Test
        @DisplayName("API-004: 获取指定API密钥成功")
        void testGetApiKeyById_success() {
            // Given
            when(apiKeyService.getApiKeyByIdVO(TEST_KEY_ID)).thenReturn(Mono.just(testApiKeyVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.getApiKeyById(TEST_KEY_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(TEST_KEY_ID, response.getData().getKeyId());
                        assertEquals("Test API Key", response.getData().getDescription());
                        assertEquals("获取API密钥信息成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-005: 获取不存在的API密钥")
        void testGetApiKeyById_notFound() {
            // Given
            when(apiKeyService.getApiKeyByIdVO("nonexistent"))
                    .thenReturn(Mono.error(new RuntimeException("API Key not found")));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.getApiKeyById("nonexistent");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("NOT_FOUND", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 创建API密钥测试 ====================

    @Nested
    @DisplayName("POST /api/auth/api-keys - 创建API密钥测试")
    class CreateApiKeyTests {

        @Test
        @DisplayName("API-006: 创建API密钥成功")
        void testCreateApiKey_success() {
            // Given
            ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                    .description("New Test Key")
                    .permissions(Arrays.asList("chat"))
                    .build();

            when(apiKeyService.createApiKey(any())).thenReturn(Mono.just(testCreationVO));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.createApiKey(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(TEST_KEY_ID, response.getData().getKeyId());
                        assertEquals(TEST_KEY_VALUE, response.getData().getKeyValue());
                        assertTrue(response.getMessage().contains("请妥善保存密钥值"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-007: 创建API密钥失败 - 名称重复")
        void testCreateApiKey_duplicate() {
            // Given
            ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                    .keyId("existing-key")
                    .description("Duplicate Key")
                    .build();

            when(apiKeyService.createApiKey(any()))
                    .thenReturn(Mono.error(new RuntimeException("Key ID already exists")));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.createApiKey(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("INTERNAL_ERROR", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 更新API密钥测试 ====================

    @Nested
    @DisplayName("PUT /api/auth/api-keys/{keyId} - 更新API密钥测试")
    class UpdateApiKeyTests {

        @Test
        @DisplayName("API-008: 更新API密钥成功")
        void testUpdateApiKey_success() {
            // Given
            ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                    .description("Updated Description")
                    .build();

            ApiKeyVO updatedVO = ApiKeyVO.builder()
                    .keyId(TEST_KEY_ID)
                    .description("Updated Description")
                    .enabled(true)
                    .build();

            when(apiKeyService.updateApiKey(anyString(), any())).thenReturn(Mono.just(updatedVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.updateApiKey(TEST_KEY_ID, request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("Updated Description", response.getData().getDescription());
                        assertEquals("更新API密钥成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-009: 更新不存在的API密钥")
        void testUpdateApiKey_notFound() {
            // Given
            ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                    .description("Updated Description")
                    .build();

            when(apiKeyService.updateApiKey(anyString(), any()))
                    .thenReturn(Mono.error(new RuntimeException("API Key not found")));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.updateApiKey("nonexistent", request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("NOT_FOUND", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 删除API密钥测试 ====================

    @Nested
    @DisplayName("DELETE /api/auth/api-keys/{keyId} - 删除API密钥测试")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("API-010: 删除API密钥成功")
        void testDeleteApiKey_success() {
            // Given
            when(apiKeyService.deleteApiKey(TEST_KEY_ID)).thenReturn(Mono.empty());

            // When
            Mono<RouterResponse<Void>> result = controller.deleteApiKey(TEST_KEY_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("删除API密钥成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-011: 删除不存在的API密钥")
        void testDeleteApiKey_notFound() {
            // Given
            when(apiKeyService.deleteApiKey("nonexistent"))
                    .thenReturn(Mono.error(new RuntimeException("API Key not found")));

            // When
            Mono<RouterResponse<Void>> result = controller.deleteApiKey("nonexistent");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("NOT_FOUND", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 启用/禁用API密钥测试 ====================

    @Nested
    @DisplayName("PATCH /api/auth/api-keys/{keyId}/* - 启用/禁用API密钥测试")
    class EnableDisableApiKeyTests {

        @Test
        @DisplayName("API-012: 禁用API密钥成功")
        void testDisableApiKey_success() {
            // Given
            ApiKeyVO disabledVO = ApiKeyVO.builder()
                    .keyId(TEST_KEY_ID)
                    .enabled(false)
                    .build();

            when(apiKeyService.disableApiKey(TEST_KEY_ID)).thenReturn(Mono.just(disabledVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.disableApiKey(TEST_KEY_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertFalse(response.getData().isEnabled());
                        assertEquals("禁用API密钥成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-013: 启用API密钥成功")
        void testEnableApiKey_success() {
            // Given
            ApiKeyVO enabledVO = ApiKeyVO.builder()
                    .keyId(TEST_KEY_ID)
                    .enabled(true)
                    .build();

            when(apiKeyService.enableApiKey(TEST_KEY_ID)).thenReturn(Mono.just(enabledVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.enableApiKey(TEST_KEY_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertTrue(response.getData().isEnabled());
                        assertEquals("启用API密钥成功", response.getMessage());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 重置/轮换API密钥测试 ====================

    @Nested
    @DisplayName("POST /api/auth/api-keys/{keyId}/* - 重置/轮换API密钥测试")
    class ResetRotateApiKeyTests {

        @Test
        @DisplayName("API-014: 强制轮换API密钥成功")
        void testForceRotateApiKey_success() {
            // Given
            ApiKeyCreationVO rotatedVO = ApiKeyCreationVO.builder()
                    .keyId(TEST_KEY_ID)
                    .keyValue("sk-new-rotated-key")
                    .description("Test API Key")
                    .enabled(true)
                    .build();

            when(apiKeyService.forceRotateKey(anyString(), anyString()))
                    .thenReturn(Mono.just(rotatedVO));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.forceRotateApiKey(TEST_KEY_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals("sk-new-rotated-key", response.getData().getKeyValue());
                        assertTrue(response.getMessage().contains("请妥善保存新的密钥值"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-015: 轮换不存在的API密钥失败")
        void testForceRotateApiKey_notFound() {
            // Given
            when(apiKeyService.forceRotateKey(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("API Key not found")));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.forceRotateApiKey("nonexistent");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("INTERNAL_ERROR", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 批量导出/导入测试 ====================

    @Nested
    @DisplayName("GET/POST /api/auth/api-keys/export|import - 批量导出/导入测试")
    class BatchExportImportTests {

        @Test
        @DisplayName("API-016: 批量导出API密钥成功")
        void testExportApiKeys_success() {
            // Given
            ApiKeyBatchExportVO exportVO = ApiKeyBatchExportVO.builder()
                    .keys(Arrays.asList(
                            ApiKeyBatchExportVO.ExportedKey.builder()
                                    .keyId(TEST_KEY_ID)
                                    .description("Test Key")
                                    .build()
                    ))
                    .exportTime(LocalDateTime.now())
                    .total(1)
                    .build();

            when(apiKeyService.exportApiKeys()).thenReturn(Mono.just(exportVO));

            // When
            Mono<RouterResponse<ApiKeyBatchExportVO>> result = controller.exportApiKeys();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(1, response.getData().getTotal());
                        assertEquals("导出API密钥配置成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("API-017: 批量导入API密钥成功")
        void testImportApiKeys_success() {
            // Given
            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .mode(ApiKeyBatchImportRequest.ImportMode.MERGE)
                    .keys(Arrays.asList(
                            ApiKeyBatchImportRequest.ApiKeyImportItem.builder()
                                    .keyId("import-key-001")
                                    .description("Imported Key")
                                    .build()
                    ))
                    .build();

            ApiKeyCreationVO importedKey = ApiKeyCreationVO.builder()
                    .keyId("import-key-001")
                    .keyValue("sk-new-imported-key")
                    .description("Imported Key")
                    .enabled(true)
                    .build();

            ApiKeyBatchImportResult resultVO = ApiKeyBatchImportResult.builder()
                    .successCount(1)
                    .failureCount(0)
                    .importedKeys(Arrays.asList(importedKey))
                    .build();

            when(apiKeyService.importApiKeys(any(), anyString(), any()))
                    .thenReturn(Mono.just(resultVO));

            // When
            Mono<RouterResponse<ApiKeyBatchImportResult>> result = controller.importApiKeys(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(1, response.getData().getSuccessCount());
                        assertEquals(0, response.getData().getFailureCount());
                    })
                    .verifyComplete();
        }
    }
}
