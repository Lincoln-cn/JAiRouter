package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.dto.*;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.security.service.ApiKeyQuotaService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * v2.7.11 API Key createdBy字段控制器测试
 * 测试API返回包含createdBy字段
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("v2.7.11 API Key createdBy字段控制器测试")
class ApiKeyMultiUserControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ApiKeyQuotaService apiKeyQuotaService;

    @InjectMocks
    private ApiKeyManagementController controller;

    private ApiKeyVO testKeyVO;
    private ApiKeyCreationVO creationVO;

    @BeforeEach
    void setUp() {
        testKeyVO = ApiKeyVO.builder()
                .keyId("test-key")
                .description("测试Key")
                .createdBy("admin")
                .permissions(Arrays.asList("chat"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        creationVO = ApiKeyCreationVO.builder()
                .keyId("test-key")
                .keyValue("sk-test-abc123")
                .description("测试Key")
                .createdBy("admin")
                .permissions(Arrays.asList("chat"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .warning("密钥值只会显示一次")
                .build();
    }

    // ==================== 创建API Key测试 ====================

    @Test
    @DisplayName("MU-CRTL-001: 创建API Key返回createdBy")
    void testCreateApiKey_ReturnsCreatedBy() {
        // Given
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .keyId("test-key")
                .description("测试Key")
                .permissions(Arrays.asList("chat"))
                .build();

        when(apiKeyService.createApiKey(any(ApiKeyCreateRequest.class)))
                .thenReturn(Mono.just(creationVO));

        // When & Then
        StepVerifier.create(controller.createApiKey(request))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.isSuccess());
                    assertEquals("admin", response.getData().getCreatedBy());
                })
                .verifyComplete();
    }

    // ==================== 查询API Key测试 ====================

    @Test
    @DisplayName("MU-CRTL-002: 查询API Key列表返回createdBy")
    void testGetAllApiKeys_ReturnsCreatedBy() {
        // Given
        ApiKeyListVO listVO = ApiKeyListVO.builder()
                .items(Arrays.asList(testKeyVO))
                .total(1)
                .enabledCount(1)
                .disabledCount(0)
                .expiredCount(0)
                .build();

        when(apiKeyService.getAllApiKeysVO()).thenReturn(Mono.just(listVO));

        // When & Then
        StepVerifier.create(controller.getAllApiKeys())
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.isSuccess());
                    assertEquals(1, response.getData().getItems().size());
                    assertEquals("admin", response.getData().getItems().get(0).getCreatedBy());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("MU-CRTL-003: 查询单个API Key返回createdBy")
    void testGetApiKeyById_ReturnsCreatedBy() {
        // Given
        when(apiKeyService.getApiKeyByIdVO("test-key"))
                .thenReturn(Mono.just(testKeyVO));

        // When & Then
        StepVerifier.create(controller.getApiKeyById("test-key"))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.isSuccess());
                    assertEquals("admin", response.getData().getCreatedBy());
                })
                .verifyComplete();
    }

    // ==================== 编辑API Key测试 ====================

    @Test
    @DisplayName("MU-CRTL-004: 编辑API Key保留createdBy")
    void testUpdateApiKey_PreservesCreatedBy() {
        // Given
        ApiKeyVO updatedVO = ApiKeyVO.builder()
                .keyId("test-key")
                .description("修改后的描述")
                .createdBy("admin")
                .permissions(Arrays.asList("chat"))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        ApiKeyUpdateRequest updateRequest = ApiKeyUpdateRequest.builder()
                .description("修改后的描述")
                .build();

        when(apiKeyService.updateApiKey(eq("test-key"), any(ApiKeyUpdateRequest.class)))
                .thenReturn(Mono.just(updatedVO));

        // When & Then
        StepVerifier.create(controller.updateApiKey("test-key", updateRequest))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.isSuccess());
                    assertEquals("admin", response.getData().getCreatedBy());
                    assertEquals("修改后的描述", response.getData().getDescription());
                })
                .verifyComplete();
    }
}
