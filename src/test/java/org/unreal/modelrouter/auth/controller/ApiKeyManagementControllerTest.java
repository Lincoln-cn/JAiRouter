package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.dto.*;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ApiKeyManagementController 单元测试
 *
 * <p>使用 StepVerifier 测试响应式 API</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ApiKeyManagementController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyManagementControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private ApiKeyManagementController controller;

    private ApiKeyVO testApiKeyVO;
    private ApiKeyCreationVO testCreationVO;

    @BeforeEach
    void setUp() {
        testApiKeyVO = ApiKeyVO.builder()
                .keyId("key-001")
                .description("Test API Key")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        testCreationVO = ApiKeyCreationVO.builder()
                .keyId("key-001")
                .keyValue("sk-test-value")
                .description("Test API Key")
                .enabled(true)
                .build();
    }

    // ==================== 获取所有API密钥测试 ====================

    @Nested
    @DisplayName("GET /api/auth/api-keys - 获取所有API密钥测试")
    class GetAllApiKeysTests {

        @Test
        @DisplayName("APIKEY-001: 成功获取所有API密钥")
        void testGetAllApiKeys_success() {
            // Given
            ApiKeyListVO listVO = ApiKeyListVO.builder()
                    .items(List.of(testApiKeyVO))
                    .total(1)
                    .enabledCount(1)
                    .disabledCount(0)
                    .build();
            when(apiKeyService.getAllApiKeysVO()).thenReturn(Mono.just(listVO));

            // When
            Mono<RouterResponse<ApiKeyListVO>> result = controller.getAllApiKeys();

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() && response.getData().getTotal() == 1)
                    .verifyComplete();
        }
    }

    // ==================== 获取单个API密钥测试 ====================

    @Nested
    @DisplayName("GET /api/auth/api-keys/{keyId} - 获取单个API密钥测试")
    class GetApiKeyByIdTests {

        @Test
        @DisplayName("APIKEY-002: 成功获取API密钥")
        void testGetApiKeyById_success() {
            // Given
            when(apiKeyService.getApiKeyByIdVO("key-001")).thenReturn(Mono.just(testApiKeyVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.getApiKeyById("key-001");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() 
                            && response.getData().getKeyId().equals("key-001"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("APIKEY-003: API密钥不存在")
        void testGetApiKeyById_notFound() {
            // Given
            when(apiKeyService.getApiKeyByIdVO("nonexistent"))
                    .thenReturn(Mono.error(new RuntimeException("Not found")));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.getApiKeyById("nonexistent");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> !response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 创建API密钥测试 ====================

    @Nested
    @DisplayName("POST /api/auth/api-keys - 创建API密钥测试")
    class CreateApiKeyTests {

        @Test
        @DisplayName("APIKEY-004: 成功创建API密钥")
        void testCreateApiKey_success() {
            // Given
            ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                    .description("Test Key")
                    .build();
            when(apiKeyService.createApiKey(any())).thenReturn(Mono.just(testCreationVO));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.createApiKey(request);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() 
                            && response.getData().getKeyValue() != null)
                    .verifyComplete();
        }
    }

    // ==================== 更新API密钥测试 ====================

    @Nested
    @DisplayName("PUT /api/auth/api-keys/{keyId} - 更新API密钥测试")
    class UpdateApiKeyTests {

        @Test
        @DisplayName("APIKEY-005: 成功更新API密钥")
        void testUpdateApiKey_success() {
            // Given
            ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                    .description("Updated description")
                    .build();
            when(apiKeyService.updateApiKey(anyString(), any())).thenReturn(Mono.just(testApiKeyVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.updateApiKey("key-001", request);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 删除API密钥测试 ====================

    @Nested
    @DisplayName("DELETE /api/auth/api-keys/{keyId} - 删除API密钥测试")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("APIKEY-006: 成功删除API密钥")
        void testDeleteApiKey_success() {
            // Given
            when(apiKeyService.deleteApiKey("key-001")).thenReturn(Mono.empty());

            // When
            Mono<RouterResponse<Void>> result = controller.deleteApiKey("key-001");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 禁用API密钥测试 ====================

    @Nested
    @DisplayName("PATCH /api/auth/api-keys/{keyId}/disable - 禁用API密钥测试")
    class DisableApiKeyTests {

        @Test
        @DisplayName("APIKEY-007: 成功禁用API密钥")
        void testDisableApiKey_success() {
            // Given
            ApiKeyVO disabledVO = ApiKeyVO.builder()
                    .keyId("key-001")
                    .enabled(false)
                    .build();
            when(apiKeyService.disableApiKey("key-001")).thenReturn(Mono.just(disabledVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.disableApiKey("key-001");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() 
                            && !response.getData().isEnabled())
                    .verifyComplete();
        }
    }

    // ==================== 启用API密钥测试 ====================

    @Nested
    @DisplayName("PATCH /api/auth/api-keys/{keyId}/enable - 启用API密钥测试")
    class EnableApiKeyTests {

        @Test
        @DisplayName("APIKEY-008: 成功启用API密钥")
        void testEnableApiKey_success() {
            // Given
            when(apiKeyService.enableApiKey("key-001")).thenReturn(Mono.just(testApiKeyVO));

            // When
            Mono<RouterResponse<ApiKeyVO>> result = controller.enableApiKey("key-001");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 强制轮换API密钥测试 ====================

    @Nested
    @DisplayName("POST /api/auth/api-keys/{keyId}/rotate - 强制轮换API密钥测试")
    class ForceRotateApiKeyTests {

        @Test
        @DisplayName("APIKEY-009: 成功强制轮换API密钥")
        void testForceRotateApiKey_success() {
            // Given
            when(apiKeyService.forceRotateKey(anyString(), anyString())).thenReturn(Mono.just(testCreationVO));

            // When
            Mono<RouterResponse<ApiKeyCreationVO>> result = controller.forceRotateApiKey("key-001");

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 批量导出API密钥测试 ====================

    @Nested
    @DisplayName("GET /api/auth/api-keys/export - 批量导出API密钥测试")
    class ExportApiKeysTests {

        @Test
        @DisplayName("APIKEY-010: 成功导出API密钥")
        void testExportApiKeys_success() {
            // Given
            ApiKeyBatchExportVO exportVO = ApiKeyBatchExportVO.builder()
                    .total(1)
                    .exportTime(LocalDateTime.now())
                    .keys(List.of())
                    .build();
            when(apiKeyService.exportApiKeys()).thenReturn(Mono.just(exportVO));

            // When
            Mono<RouterResponse<ApiKeyBatchExportVO>> result = controller.exportApiKeys();

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() 
                            && response.getData().getTotal() == 1)
                    .verifyComplete();
        }
    }

    // ==================== 批量导入API密钥测试 ====================

    @Nested
    @DisplayName("POST /api/auth/api-keys/import - 批量导入API密钥测试")
    class ImportApiKeysTests {

        @Test
        @DisplayName("APIKEY-011: 成功导入API密钥")
        void testImportApiKeys_success() {
            // Given
            ApiKeyBatchImportRequest request = ApiKeyBatchImportRequest.builder()
                    .keys(List.of())
                    .build();
            ApiKeyBatchImportResult resultVO = ApiKeyBatchImportResult.builder()
                    .successCount(1)
                    .failureCount(0)
                    .build();
            when(apiKeyService.importApiKeys(any(), anyString(), any())).thenReturn(Mono.just(resultVO));

            // When
            Mono<RouterResponse<ApiKeyBatchImportResult>> result = controller.importApiKeys(request);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess() 
                            && response.getData().getSuccessCount() == 1)
                    .verifyComplete();
        }
    }
}
