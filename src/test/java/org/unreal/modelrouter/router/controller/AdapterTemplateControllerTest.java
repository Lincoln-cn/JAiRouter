package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.template.AdapterTemplate;
import org.unreal.modelrouter.router.adapter.template.AdapterTemplateService;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AdapterTemplateController 模板 API 测试
 *
 * TDD 测试先行：9 个测试用例覆盖模板 API 端点
 */
@DisplayName("AdapterTemplateController 模板 API 测试")
@ExtendWith(MockitoExtension.class)
class AdapterTemplateControllerTest {

    @Mock
    private AdapterTemplateService templateService;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private AdapterDefinitionProperties adapterDefinitionProperties;

    @Mock
    private AdapterDefinitionPersistenceService persistenceService;

    @Mock
    private AdapterContext adapterContext;

    @Mock
    private RequestProcessingSupport requestProcessingSupport;

    @Mock
    private ResilienceSupport resilienceSupport;

    @Mock
    private OpenAiRequestTransformer openAiRequestTransformer;

    @Mock
    private OpenAiResponseTransformer openAiResponseTransformer;

    @InjectMocks
    private AdapterTemplateController controller;

    private AdapterTemplate deepseekTemplate;

    @BeforeEach
    void setUp() {
        deepseekTemplate = new AdapterTemplate();
        deepseekTemplate.setId("deepseek");
        deepseekTemplate.setName("DeepSeek");
        deepseekTemplate.setType("openai-compatible");
        deepseekTemplate.setDefaultBaseUrl("https://api.deepseek.com");
        deepseekTemplate.setCategory("domestic");
        deepseekTemplate.setCapabilities(new AdapterTemplate.CapabilitiesConfig(
                true, false, false, false, false, false, false, true
        ));
        deepseekTemplate.setAuth(new AdapterTemplate.AuthConfig("Authorization", "Bearer "));

        // Mock AdapterDefinitionProperties to return a mutable map
        lenient().when(adapterDefinitionProperties.getAdapterDefinitions())
                .thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    }

    // ==================== 获取模板列表测试 ====================

    @Nested
    @DisplayName("获取模板列表测试")
    class GetAllTemplatesTests {

        @Test
        @DisplayName("TMPL-API-001: 获取全部模板成功")
        void testGetAllTemplates_success() {
            // Given
            List<AdapterTemplate> templates = List.of(deepseekTemplate);
            when(templateService.getAllTemplates()).thenReturn(templates);

            // When
            ResponseEntity<RouterResponse<List<AdapterTemplate>>> result = controller.getAllTemplates(null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(1, result.getBody().getData().size());
        }

        @Test
        @DisplayName("TMPL-API-002: 按分类筛选模板")
        void testGetTemplates_withCategory() {
            // Given
            List<AdapterTemplate> templates = List.of(deepseekTemplate);
            when(templateService.getTemplatesByCategory("domestic")).thenReturn(templates);

            // When
            ResponseEntity<RouterResponse<List<AdapterTemplate>>> result = controller.getAllTemplates("domestic");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(1, result.getBody().getData().size());
            verify(templateService).getTemplatesByCategory("domestic");
        }
    }

    // ==================== 获取单个模板测试 ====================

    @Nested
    @DisplayName("获取单个模板测试")
    class GetTemplateByIdTests {

        @Test
        @DisplayName("TMPL-API-003: 获取单个模板成功")
        void testGetTemplateById_found() {
            // Given
            when(templateService.getTemplateById("deepseek")).thenReturn(deepseekTemplate);

            // When
            ResponseEntity<RouterResponse<AdapterTemplate>> result = controller.getTemplateById("deepseek");

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals("DeepSeek", result.getBody().getData().getName());
        }

        @Test
        @DisplayName("TMPL-API-004: 获取不存在的模板返回 404")
        void testGetTemplateById_notFound() {
            // Given
            when(templateService.getTemplateById("nonexistent")).thenReturn(null);

            // When
            ResponseEntity<RouterResponse<AdapterTemplate>> result = controller.getTemplateById("nonexistent");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals("TEMPLATE_NOT_FOUND", result.getBody().getErrorCode());
        }
    }

    // ==================== 从模板创建适配器测试 ====================

    @Nested
    @DisplayName("从模板创建适配器测试")
    class CreateFromTemplateTests {

        @Test
        @DisplayName("TMPL-API-005: 从模板创建适配器成功")
        void testCreateFromTemplate_success() {
            // Given
            when(templateService.getTemplateById("deepseek")).thenReturn(deepseekTemplate);
            when(adapterRegistry.isAdapterSupported("my-deepseek")).thenReturn(false);

            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "my-deepseek");
            overrides.put("apiKey", "sk-test");

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result =
                    controller.createFromTemplate("deepseek", overrides);

            // Then
            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            verify(adapterRegistry).registerAdapter(eq("my-deepseek"), any());
        }

        @Test
        @DisplayName("TMPL-API-006: 名称冲突返回 409")
        void testCreateFromTemplate_nameConflict() {
            // Given
            when(templateService.getTemplateById("deepseek")).thenReturn(deepseekTemplate);
            when(adapterRegistry.isAdapterSupported("existing")).thenReturn(true);

            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "existing");

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result =
                    controller.createFromTemplate("deepseek", overrides);

            // Then
            assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals("CONFLICT_EXISTS", result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("TMPL-API-007: 模板不存在返回 404")
        void testCreateFromTemplate_templateNotFound() {
            // Given
            when(templateService.getTemplateById("nonexistent")).thenReturn(null);

            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "test");

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result =
                    controller.createFromTemplate("nonexistent", overrides);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals("TEMPLATE_NOT_FOUND", result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("TMPL-API-008: 名称为空返回 400")
        void testCreateFromTemplate_emptyName() {
            // Given
            when(templateService.getTemplateById("deepseek")).thenReturn(deepseekTemplate);

            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "");

            // When
            ResponseEntity<RouterResponse<Map<String, Object>>> result =
                    controller.createFromTemplate("deepseek", overrides);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals("INVALID_NAME", result.getBody().getErrorCode());
        }

        @Test
        @DisplayName("TMPL-API-009: 验证注册和持久化调用")
        void testCreateFromTemplate_callsRegistryRegister() {
            // Given
            when(templateService.getTemplateById("deepseek")).thenReturn(deepseekTemplate);
            when(adapterRegistry.isAdapterSupported("my-deepseek")).thenReturn(false);
            doNothing().when(persistenceService).saveDefinition(anyString(), any());

            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "my-deepseek");
            overrides.put("apiKey", "sk-test");

            // When
            controller.createFromTemplate("deepseek", overrides);

            // Then
            verify(adapterRegistry).registerAdapter(eq("my-deepseek"), any());
            verify(persistenceService).saveDefinition(eq("my-deepseek"), any());
        }
    }
}
