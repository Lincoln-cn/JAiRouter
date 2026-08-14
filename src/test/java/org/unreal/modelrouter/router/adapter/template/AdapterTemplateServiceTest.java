package org.unreal.modelrouter.router.adapter.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdapterTemplateService 模板服务测试
 *
 * TDD 测试先行：15 个测试用例覆盖模板查询、分类筛选、模板生成等场景
 */
@DisplayName("AdapterTemplateService 模板服务测试")
class AdapterTemplateServiceTest {

    private AdapterTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new AdapterTemplateService();
    }

    // ==================== 获取全部模板测试 ====================

    @Nested
    @DisplayName("获取全部模板测试")
    class GetAllTemplatesTests {

        @Test
        @DisplayName("TMPL-001: 获取全部模板数量正确（13个）")
        void testGetAllTemplates_returns13Templates() {
            // When
            List<AdapterTemplate> templates = templateService.getAllTemplates();

            // Then
            assertNotNull(templates);
            assertEquals(13, templates.size(), "应返回 13 个预置模板");
        }

        @Test
        @DisplayName("TMPL-002: 模板按 sortOrder 排序")
        void testGetAllTemplates_sortedBySortOrder() {
            // When
            List<AdapterTemplate> templates = templateService.getAllTemplates();

            // Then
            for (int i = 0; i < templates.size() - 1; i++) {
                assertTrue(templates.get(i).getSortOrder() <= templates.get(i + 1).getSortOrder(),
                        "模板应按 sortOrder 升序排列: " + templates.get(i).getId()
                                + " > " + templates.get(i + 1).getId());
            }
        }
    }

    // ==================== 按 ID 获取模板测试 ====================

    @Nested
    @DisplayName("按 ID 获取模板测试")
    class GetTemplateByIdTests {

        @Test
        @DisplayName("TMPL-003: 按 ID 获取 DeepSeek 模板")
        void testGetTemplateById_deepseek_returnsDeepseekTemplate() {
            // When
            AdapterTemplate template = templateService.getTemplateById("deepseek");

            // Then
            assertNotNull(template, "DeepSeek 模板应存在");
            assertEquals("DeepSeek", template.getName());
            assertEquals("openai-compatible", template.getType());
            assertEquals("https://api.deepseek.com", template.getDefaultBaseUrl());
        }

        @Test
        @DisplayName("TMPL-004: 获取不存在的模板返回 null")
        void testGetTemplateById_notFound_returnsNull() {
            // When
            AdapterTemplate template = templateService.getTemplateById("nonexistent");

            // Then
            assertNull(template, "不存在的模板应返回 null");
        }
    }

    // ==================== 按分类筛选测试 ====================

    @Nested
    @DisplayName("按分类筛选测试")
    class GetTemplatesByCategoryTests {

        @Test
        @DisplayName("TMPL-005: 筛选国内供应商模板")
        void testGetTemplatesByCategory_domestic() {
            // When
            List<AdapterTemplate> templates = templateService.getTemplatesByCategory("domestic");

            // Then
            assertNotNull(templates);
            assertEquals(9, templates.size(), "国内供应商模板应有 9 个");
            List<String> ids = templates.stream().map(AdapterTemplate::getId).toList();
            assertTrue(ids.contains("deepseek"));
            assertTrue(ids.contains("zhipu"));
            assertTrue(ids.contains("moonshot"));
            assertTrue(ids.contains("baichuan"));
            assertTrue(ids.contains("qwen"));
            assertTrue(ids.contains("minimax"));
            assertTrue(ids.contains("yi"));
            assertTrue(ids.contains("stepfun"));
            assertTrue(ids.contains("siliconflow"));
        }

        @Test
        @DisplayName("TMPL-006: 筛选国际供应商模板")
        void testGetTemplatesByCategory_international() {
            // When
            List<AdapterTemplate> templates = templateService.getTemplatesByCategory("international");

            // Then
            assertNotNull(templates);
            assertEquals(3, templates.size(), "国际供应商模板应有 3 个");
            List<String> ids = templates.stream().map(AdapterTemplate::getId).toList();
            assertTrue(ids.contains("groq"));
            assertTrue(ids.contains("openrouter"));
            assertTrue(ids.contains("together"));
        }

        @Test
        @DisplayName("TMPL-007: 筛选本地部署模板")
        void testGetTemplatesByCategory_local() {
            // When
            List<AdapterTemplate> templates = templateService.getTemplatesByCategory("local");

            // Then
            assertNotNull(templates);
            assertEquals(1, templates.size(), "本地部署模板应有 1 个");
            assertEquals("local-ollama", templates.get(0).getId());
        }

        @Test
        @DisplayName("TMPL-008: 筛选未知分类返回空列表")
        void testGetTemplatesByCategory_unknown_returnsEmpty() {
            // When
            List<AdapterTemplate> templates = templateService.getTemplatesByCategory("unknown");

            // Then
            assertNotNull(templates);
            assertTrue(templates.isEmpty(), "未知分类应返回空列表");
        }
    }

    // ==================== 从模板生成定义测试 ====================

    @Nested
    @DisplayName("从模板生成定义测试")
    class CreateDefinitionFromTemplateTests {

        @Test
        @DisplayName("TMPL-009: 从 DeepSeek 模板生成定义")
        void testCreateDefinitionFromTemplate_deepseek() {
            // Given
            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "my-deepseek");

            // When
            var result = templateService.createDefinitionFromTemplate("deepseek", overrides);

            // Then
            assertNotNull(result);
            assertEquals("my-deepseek", result.getName());
            assertEquals("openai-compatible", result.getType());
            assertTrue(result.getCapabilities().getOrDefault("chat", false));
            assertTrue(result.getCapabilities().getOrDefault("streaming", false));
        }

        @Test
        @DisplayName("TMPL-010: 覆盖模板默认值")
        void testCreateDefinitionFromTemplate_withOverrides() {
            // Given
            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "custom-deepseek");
            overrides.put("baseUrl", "http://custom-url");

            Map<String, Boolean> capOverrides = new HashMap<>();
            capOverrides.put("embedding", true);

            // When
            var result = templateService.createDefinitionFromTemplate("deepseek", overrides);

            // Then
            assertNotNull(result);
            assertEquals("custom-deepseek", result.getName());
            assertEquals("openai-compatible", result.getType());
        }

        @Test
        @DisplayName("TMPL-011: 不存在的模板抛出异常")
        void testCreateDefinitionFromTemplate_notFound_throwsException() {
            // Given
            Map<String, String> overrides = new HashMap<>();
            overrides.put("name", "test");

            // When & Then
            assertThrows(IllegalArgumentException.class,
                    () -> templateService.createDefinitionFromTemplate("nonexistent", overrides),
                    "不存在的模板应抛出 IllegalArgumentException");
        }
    }

    // ==================== 模板数据完整性测试 ====================

    @Nested
    @DisplayName("模板数据完整性测试")
    class TemplateDataIntegrityTests {

        @Test
        @DisplayName("TMPL-012: 所有模板的必填字段不为空")
        void testAllTemplates_haveRequiredFields() {
            // When
            List<AdapterTemplate> templates = templateService.getAllTemplates();

            // Then
            for (AdapterTemplate template : templates) {
                assertNotNull(template.getId(), "模板 ID 不应为 null: " + template);
                assertFalse(template.getId().isBlank(), "模板 ID 不应为空: " + template);
                assertNotNull(template.getName(), "模板名称不应为 null: " + template.getId());
                assertFalse(template.getName().isBlank(), "模板名称不应为空: " + template.getId());
                assertNotNull(template.getType(), "模板类型不应为 null: " + template.getId());
                assertNotNull(template.getDefaultBaseUrl(), "默认 URL 不应为 null: " + template.getId());
            }
        }

        @Test
        @DisplayName("TMPL-013: 所有模板至少启用一项能力")
        void testAllTemplates_capabilitiesAtLeastOneTrue() {
            // When
            List<AdapterTemplate> templates = templateService.getAllTemplates();

            // Then
            for (AdapterTemplate template : templates) {
                AdapterTemplate.CapabilitiesConfig caps = template.getCapabilities();
                assertNotNull(caps, "能力配置不应为 null: " + template.getId());
                assertTrue(caps.isChat() || caps.isEmbedding() || caps.isRerank()
                                || caps.isTts() || caps.isStt() || caps.isImgGen()
                                || caps.isImgEdit() || caps.isStreaming(),
                        "模板应至少启用一项能力: " + template.getId());
            }
        }

        @Test
        @DisplayName("TMPL-014: DeepSeek 模板默认配置正确")
        void testDeepseekTemplate_defaultConfig() {
            // When
            AdapterTemplate template = templateService.getTemplateById("deepseek");

            // Then
            assertNotNull(template);
            assertEquals("https://api.deepseek.com", template.getDefaultBaseUrl());
            assertNotNull(template.getAuth());
            assertEquals("Bearer ", template.getAuth().getHeaderPrefix());
            assertTrue(template.getCapabilities().isChat());
            assertTrue(template.getCapabilities().isStreaming());
            assertFalse(template.getCapabilities().isEmbedding());
        }

        @Test
        @DisplayName("TMPL-015: 本地 Ollama 模板无需认证")
        void testLocalOllamaTemplate_noAuth() {
            // When
            AdapterTemplate template = templateService.getTemplateById("local-ollama");

            // Then
            assertNotNull(template);
            assertEquals("ollama-compatible", template.getType());
            assertEquals("http://localhost:11434", template.getDefaultBaseUrl());
            assertNotNull(template.getAuth());
            // Ollama 不需要认证头
            assertTrue(template.getAuth().getHeaderName() == null
                            || template.getAuth().getHeaderName().isEmpty(),
                    "本地 Ollama 模板不应设置认证头名称");
        }
    }
}
