package org.unreal.modelrouter.router.adapter.template;

import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.controller.AdapterConfigController.AdapterDefinitionRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 适配器模板服务
 * 管理常见 AI 供应商的预配置模板
 */
@Service
public class AdapterTemplateService {

    private final List<AdapterTemplate> templates;

    public AdapterTemplateService() {
        this.templates = new ArrayList<>();
        initTemplates();
    }

    /**
     * 获取全部模板（按 sortOrder 排序）
     */
    public List<AdapterTemplate> getAllTemplates() {
        return templates.stream()
                .sorted(Comparator.comparingInt(AdapterTemplate::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * 按分类筛选模板
     */
    public List<AdapterTemplate> getTemplatesByCategory(final String category) {
        return templates.stream()
                .filter(t -> t.getCategory().equals(category))
                .sorted(Comparator.comparingInt(AdapterTemplate::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 获取单个模板
     */
    public AdapterTemplate getTemplateById(final String id) {
        return templates.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从模板生成适配器定义请求
     *
     * @param templateId 模板 ID
     * @param overrides  覆盖参数（name 必填，baseUrl 可选）
     * @return 适配器定义请求
     */
    public AdapterDefinitionRequest createDefinitionFromTemplate(final String templateId,
                                                                  final Map<String, String> overrides) {
        AdapterTemplate template = getTemplateById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }

        if (overrides == null || overrides.get("name") == null || overrides.get("name").isBlank()) {
            throw new IllegalArgumentException("适配器名称不能为空");
        }

        AdapterDefinitionRequest request = new AdapterDefinitionRequest();
        request.setName(overrides.get("name"));
        request.setType(template.getType());

        // 能力配置
        Map<String, Boolean> capabilities = new HashMap<>();
        capabilities.put("chat", template.getCapabilities().isChat());
        capabilities.put("embedding", template.getCapabilities().isEmbedding());
        capabilities.put("rerank", template.getCapabilities().isRerank());
        capabilities.put("tts", template.getCapabilities().isTts());
        capabilities.put("stt", template.getCapabilities().isStt());
        capabilities.put("imgGen", template.getCapabilities().isImgGen());
        capabilities.put("imgEdit", template.getCapabilities().isImgEdit());
        capabilities.put("streaming", template.getCapabilities().isStreaming());
        request.setCapabilities(capabilities);

        // 认证配置
        if (template.getAuth() != null) {
            Map<String, String> auth = new HashMap<>();
            auth.put("headerName", template.getAuth().getHeaderName());
            auth.put("headerPrefix", template.getAuth().getHeaderPrefix());
            request.setAuth(auth);
        }

        // 额外请求头
        if (template.getAdditionalHeaders() != null) {
            request.setAdditionalHeaders(new HashMap<>(template.getAdditionalHeaders()));
        }

        return request;
    }

    /**
     * 初始化预置模板
     */
    private void initTemplates() {
        // 国内供应商
        templates.add(createTemplate(
                "deepseek", "DeepSeek", "DeepSeek Chat/Code API",
                "domestic", "openai-compatible",
                "https://api.deepseek.com",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("deepseek-chat", "deepseek-coder", "deepseek-reasoner"),
                10
        ));

        templates.add(createTemplate(
                "zhipu", "智谱 AI (GLM)", "智谱 GLM 系列模型",
                "domestic", "openai-compatible",
                "https://open.bigmodel.cn/api/paas/v4",
                true, true, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("glm-4", "glm-4-flash", "embedding-3"),
                20
        ));

        templates.add(createTemplate(
                "moonshot", "月之暗面 (Kimi)", "月之暗面 Kimi 模型",
                "domestic", "openai-compatible",
                "https://api.moonshot.cn/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
                30
        ));

        templates.add(createTemplate(
                "baichuan", "百川智能", "百川大模型",
                "domestic", "openai-compatible",
                "https://api.baichuan-ai.com/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("Baichuan4", "Baichuan3-Turbo"),
                40
        ));

        templates.add(createTemplate(
                "qwen", "通义千问", "阿里云通义千问",
                "domestic", "openai-compatible",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                true, true, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("qwen-turbo", "qwen-plus", "qwen-max", "text-embedding-v3"),
                50
        ));

        templates.add(createTemplate(
                "minimax", "MiniMax", "MiniMax 模型",
                "domestic", "openai-compatible",
                "https://api.minimax.chat/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("abab6.5-chat", "abab6.5s-chat"),
                60
        ));

        templates.add(createTemplate(
                "yi", "零一万物 (Yi)", "零一万物 Yi 系列模型",
                "domestic", "openai-compatible",
                "https://api.lingyiwanwu.com/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("yi-large", "yi-medium", "yi-spark"),
                70
        ));

        templates.add(createTemplate(
                "stepfun", "阶跃星辰", "阶跃星辰 Step 系列模型",
                "domestic", "openai-compatible",
                "https://api.stepfun.com/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("step-1-8k", "step-1-32k", "step-1-128k"),
                80
        ));

        templates.add(createTemplate(
                "siliconflow", "硅基流动", "硅基流动 SiliconFlow 平台",
                "domestic", "openai-compatible",
                "https://api.siliconflow.cn/v1",
                true, true, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-7B-Instruct"),
                90
        ));

        // 国际供应商
        templates.add(createTemplate(
                "groq", "Groq", "Groq 高速推理平台",
                "international", "openai-compatible",
                "https://api.groq.com/openai/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("llama-3.3-70b-versatile", "mixtral-8x7b-32768"),
                100
        ));

        templates.add(createTemplate(
                "openrouter", "OpenRouter", "OpenRouter 多模型路由",
                "international", "openai-compatible",
                "https://openrouter.ai/api/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("openai/gpt-4o", "anthropic/claude-3.5-sonnet"),
                110
        ));

        templates.add(createTemplate(
                "together", "Together AI", "Together AI 推理平台",
                "international", "openai-compatible",
                "https://api.together.xyz/v1",
                true, false, false, false, false, false, false, true,
                "Authorization", "Bearer ",
                List.of("meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"),
                120
        ));

        // 本地部署
        templates.add(createTemplate(
                "local-ollama", "本地 Ollama", "本地 Ollama 服务",
                "local", "ollama-compatible",
                "http://localhost:11434",
                true, true, false, false, false, false, false, true,
                "", "",
                List.of("llama3", "qwen2", "deepseek-v2"),
                130
        ));
    }

    private AdapterTemplate createTemplate(final String id, final String name, final String description,
                                           final String category, final String type,
                                           final String defaultBaseUrl,
                                           final boolean chat, final boolean embedding,
                                           final boolean rerank, final boolean tts,
                                           final boolean stt, final boolean imgGen,
                                           final boolean imgEdit, final boolean streaming,
                                           final String authHeaderName, final String authHeaderPrefix,
                                           final List<String> supportedModels, final int sortOrder) {
        AdapterTemplate template = new AdapterTemplate();
        template.setId(id);
        template.setName(name);
        template.setDescription(description);
        template.setCategory(category);
        template.setType(type);
        template.setDefaultBaseUrl(defaultBaseUrl);
        template.setCapabilities(new AdapterTemplate.CapabilitiesConfig(
                chat, embedding, rerank, tts, stt, imgGen, imgEdit, streaming
        ));
        template.setAuth(new AdapterTemplate.AuthConfig(authHeaderName, authHeaderPrefix));
        template.setSupportedModels(supportedModels);
        template.setSortOrder(sortOrder);
        return template;
    }
}
