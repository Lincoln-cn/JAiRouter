package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.impl.ConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter配置管理控制器
 * 提供配置驱动adapter的CRUD接口
 */
@RestController
@RequestMapping("/api/config/adapter")
@CrossOrigin(origins = "*")
@Tag(name = "Adapter配置管理", description = "提供配置驱动adapter的增删改查接口")
public class AdapterConfigController {

    private static final Logger logger = LoggerFactory.getLogger(AdapterConfigController.class);

    private final AdapterRegistry adapterRegistry;
    private final AdapterDefinitionProperties adapterDefinitionProperties;
    private final AdapterContext adapterContext;
    private final RequestProcessingSupport requestProcessingSupport;
    private final ResilienceSupport resilienceSupport;
    private final OpenAiRequestTransformer openAiRequestTransformer;
    private final OpenAiResponseTransformer openAiResponseTransformer;

    public AdapterConfigController(final AdapterRegistry adapterRegistry,
                                   final AdapterDefinitionProperties adapterDefinitionProperties,
                                   final AdapterContext adapterContext,
                                   final RequestProcessingSupport requestProcessingSupport,
                                   final ResilienceSupport resilienceSupport,
                                   final OpenAiRequestTransformer openAiRequestTransformer,
                                   final OpenAiResponseTransformer openAiResponseTransformer) {
        this.adapterRegistry = adapterRegistry;
        this.adapterDefinitionProperties = adapterDefinitionProperties;
        this.adapterContext = adapterContext;
        this.requestProcessingSupport = requestProcessingSupport;
        this.resilienceSupport = resilienceSupport;
        this.openAiRequestTransformer = openAiRequestTransformer;
        this.openAiResponseTransformer = openAiResponseTransformer;
    }

    /**
     * 获取所有adapter列表（内置 + 配置驱动）
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有adapter列表", description = "获取系统中所有adapter（内置和配置驱动）")
    @ApiResponse(responseCode = "200", description = "成功获取adapter列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    public ResponseEntity<RouterResponse<List<Map<String, Object>>>> getAllAdapters() {
        try {
            Map<String, ServiceCapability> allAdapters = adapterRegistry.getAllAdapters();
            List<Map<String, Object>> adapterList = new ArrayList<>();

            for (Map.Entry<String, ServiceCapability> entry : allAdapters.entrySet()) {
                String name = entry.getKey();
                ServiceCapability adapter = entry.getValue();
                Map<String, Object> info = new HashMap<>();
                info.put("name", name);
                info.put("source", adapterRegistry.isBuiltinAdapter(name) ? "builtin" : "configurable");

                if (adapter instanceof ConfigurableAdapter) {
                    info.put("type", "openai-compatible");
                } else {
                    info.put("type", "builtin");
                }

                Map<String, Boolean> capabilities = new HashMap<>();
                if (adapter instanceof org.unreal.modelrouter.router.adapter.BaseAdapter baseAdapter) {
                    AdapterCapabilities caps = baseAdapter.supportCapability();
                    capabilities.put("chat", caps.isSupportChat());
                    capabilities.put("embedding", caps.isSupportEmbedding());
                    capabilities.put("rerank", caps.isSupportRerank());
                    capabilities.put("tts", caps.isSupportTts());
                    capabilities.put("stt", caps.isSupportStt());
                    capabilities.put("imgGen", caps.isSupportImageGenerate());
                    capabilities.put("imgEdit", caps.isSupportImageEdit());
                    capabilities.put("streaming", caps.isSupportStreaming());
                }
                info.put("capabilities", capabilities);

                adapterList.add(info);
            }

            return ResponseEntity.ok(RouterResponse.success(adapterList, "获取adapter列表成功"));
        } catch (Exception e) {
            logger.error("获取adapter列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取adapter列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取单个adapter详情
     */
    @GetMapping("/{name}")
    @Operation(summary = "获取adapter详情", description = "获取指定adapter的详细配置信息")
    @ApiResponse(responseCode = "200", description = "成功获取adapter详情")
    @ApiResponse(responseCode = "404", description = "adapter不存在")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getAdapter(
            @Parameter(description = "adapter名称") @PathVariable final String name) {
        try {
            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                    adapterDefinitionProperties.getAdapterDefinitions();
            AdapterDefinitionProperties.AdapterDefinition definition = definitions.get(name);

            if (definition == null) {
                if (adapterRegistry.isAdapterSupported(name)) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", name);
                    info.put("source", "builtin");
                    info.put("type", "builtin");
                    return ResponseEntity.ok(RouterResponse.success(info, "获取adapter详情成功"));
                }
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("adapter不存在: " + name, "NOT_FOUND"));
            }

            Map<String, Object> info = new HashMap<>();
            info.put("name", name);
            info.put("source", "configurable");
            info.put("type", definition.getType());
            info.put("capabilities", definition.getCapabilities());
            info.put("auth", definition.getAuth());
            info.put("additionalHeaders", definition.getAdditionalHeaders());

            return ResponseEntity.ok(RouterResponse.success(info, "获取adapter详情成功"));
        } catch (Exception e) {
            logger.error("获取adapter详情失败: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取adapter详情失败: " + e.getMessage()));
        }
    }

    /**
     * 创建新的配置驱动adapter
     */
    @PostMapping
    @Operation(summary = "创建adapter", description = "创建新的配置驱动adapter")
    @ApiResponse(responseCode = "201", description = "adapter创建成功")
    @ApiResponse(responseCode = "400", description = "请求参数无效")
    @ApiResponse(responseCode = "409", description = "adapter名称已存在")
    public ResponseEntity<RouterResponse<Map<String, Object>>> createAdapter(
            @RequestBody final AdapterDefinitionRequest request) {
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("adapter名称不能为空", "INVALID_NAME"));
            }

            if (adapterRegistry.isBuiltinAdapter(request.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(RouterResponse.error("不能覆盖内置adapter: " + request.getName(), "CONFLICT_BUILTIN"));
            }

            if (adapterRegistry.isAdapterSupported(request.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(RouterResponse.error("adapter名称已存在: " + request.getName(), "CONFLICT_EXISTS"));
            }

            AdapterDefinitionProperties.AdapterDefinition definition = new AdapterDefinitionProperties.AdapterDefinition();
            definition.setType(request.getType() != null ? request.getType() : "openai-compatible");

            if (request.getCapabilities() != null) {
                AdapterDefinitionProperties.CapabilitiesConfig caps = new AdapterDefinitionProperties.CapabilitiesConfig();
                caps.setChat(request.getCapabilities().getOrDefault("chat", false));
                caps.setEmbedding(request.getCapabilities().getOrDefault("embedding", false));
                caps.setRerank(request.getCapabilities().getOrDefault("rerank", false));
                caps.setTts(request.getCapabilities().getOrDefault("tts", false));
                caps.setStt(request.getCapabilities().getOrDefault("stt", false));
                caps.setImgGen(request.getCapabilities().getOrDefault("imgGen", false));
                caps.setImgEdit(request.getCapabilities().getOrDefault("imgEdit", false));
                caps.setStreaming(request.getCapabilities().getOrDefault("streaming", false));
                definition.setCapabilities(caps);
            }

            if (request.getAuth() != null) {
                AdapterDefinitionProperties.AuthConfig auth = new AdapterDefinitionProperties.AuthConfig();
                auth.setHeaderName(request.getAuth().getOrDefault("headerName", "Authorization"));
                auth.setHeaderPrefix(request.getAuth().getOrDefault("headerPrefix", "Bearer "));
                definition.setAuth(auth);
            }

            if (request.getAdditionalHeaders() != null) {
                definition.setAdditionalHeaders(request.getAdditionalHeaders());
            }

            // 保存到配置
            adapterDefinitionProperties.getAdapterDefinitions().put(request.getName(), definition);

            // 创建adapter并注册
            ServiceCapability adapter = createAdapterByType(request.getName(), definition);
            adapterRegistry.registerAdapter(request.getName(), adapter);

            Map<String, Object> result = new HashMap<>();
            result.put("name", request.getName());
            result.put("source", "configurable");
            result.put("type", definition.getType());

            logger.info("Created configurable adapter: {}", request.getName());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RouterResponse.success(result, "adapter创建成功"));
        } catch (Exception e) {
            logger.error("创建adapter失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("创建adapter失败: " + e.getMessage()));
        }
    }

    /**
     * 更新配置驱动adapter
     */
    @PutMapping("/{name}")
    @Operation(summary = "更新adapter", description = "更新配置驱动adapter的配置")
    @ApiResponse(responseCode = "200", description = "adapter更新成功")
    @ApiResponse(responseCode = "404", description = "adapter不存在")
    @ApiResponse(responseCode = "400", description = "不能更新内置adapter")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateAdapter(
            @Parameter(description = "adapter名称") @PathVariable final String name,
            @RequestBody final AdapterDefinitionRequest request) {
        try {
            if (adapterRegistry.isBuiltinAdapter(name)) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("不能更新内置adapter: " + name, "BUILTIN_READONLY"));
            }

            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                    adapterDefinitionProperties.getAdapterDefinitions();
            if (!definitions.containsKey(name)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("adapter不存在: " + name, "NOT_FOUND"));
            }

            AdapterDefinitionProperties.AdapterDefinition definition = new AdapterDefinitionProperties.AdapterDefinition();
            definition.setType(request.getType() != null ? request.getType() : "openai-compatible");

            if (request.getCapabilities() != null) {
                AdapterDefinitionProperties.CapabilitiesConfig caps = new AdapterDefinitionProperties.CapabilitiesConfig();
                caps.setChat(request.getCapabilities().getOrDefault("chat", false));
                caps.setEmbedding(request.getCapabilities().getOrDefault("embedding", false));
                caps.setRerank(request.getCapabilities().getOrDefault("rerank", false));
                caps.setTts(request.getCapabilities().getOrDefault("tts", false));
                caps.setStt(request.getCapabilities().getOrDefault("stt", false));
                caps.setImgGen(request.getCapabilities().getOrDefault("imgGen", false));
                caps.setImgEdit(request.getCapabilities().getOrDefault("imgEdit", false));
                caps.setStreaming(request.getCapabilities().getOrDefault("streaming", false));
                definition.setCapabilities(caps);
            }

            if (request.getAuth() != null) {
                AdapterDefinitionProperties.AuthConfig auth = new AdapterDefinitionProperties.AuthConfig();
                auth.setHeaderName(request.getAuth().getOrDefault("headerName", "Authorization"));
                auth.setHeaderPrefix(request.getAuth().getOrDefault("headerPrefix", "Bearer "));
                definition.setAuth(auth);
            }

            if (request.getAdditionalHeaders() != null) {
                definition.setAdditionalHeaders(request.getAdditionalHeaders());
            }

            // 更新配置
            definitions.put(name, definition);

            // 重新创建adapter
            ServiceCapability adapter = createAdapterByType(name, definition);
            adapterRegistry.registerAdapter(name, adapter);

            Map<String, Object> result = new HashMap<>();
            result.put("name", name);
            result.put("source", "configurable");
            result.put("type", definition.getType());

            logger.info("Updated configurable adapter: {}", name);
            return ResponseEntity.ok(RouterResponse.success(result, "adapter更新成功"));
        } catch (Exception e) {
            logger.error("更新adapter失败: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新adapter失败: " + e.getMessage()));
        }
    }

    /**
     * 删除配置驱动adapter
     */
    @DeleteMapping("/{name}")
    @Operation(summary = "删除adapter", description = "删除配置驱动adapter")
    @ApiResponse(responseCode = "200", description = "adapter删除成功")
    @ApiResponse(responseCode = "404", description = "adapter不存在")
    @ApiResponse(responseCode = "400", description = "不能删除内置adapter")
    public ResponseEntity<RouterResponse<Void>> deleteAdapter(
            @Parameter(description = "adapter名称") @PathVariable final String name) {
        try {
            if (adapterRegistry.isBuiltinAdapter(name)) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("不能删除内置adapter: " + name, "BUILTIN_READONLY"));
            }

            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                    adapterDefinitionProperties.getAdapterDefinitions();
            if (!definitions.containsKey(name)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("adapter不存在: " + name, "NOT_FOUND"));
            }

            // 从配置中移除
            definitions.remove(name);

            // 从registry中移除
            adapterRegistry.removeAdapter(name);

            logger.info("Deleted configurable adapter: {}", name);
            return ResponseEntity.ok(RouterResponse.success("adapter删除成功"));
        } catch (Exception e) {
            logger.error("删除adapter失败: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("删除adapter失败: " + e.getMessage()));
        }
    }

    private ServiceCapability createAdapterByType(final String name,
                                                  final AdapterDefinitionProperties.AdapterDefinition definition) {
        String type = definition.getType() != null ? definition.getType() : "openai-compatible";

        if ("ollama-compatible".equals(type)) {
            return createOllamaConfigurableAdapter(name, definition);
        }
        return createConfigurableAdapter(name, definition);
    }

    private OllamaConfigurableAdapter createOllamaConfigurableAdapter(final String name,
                                                                      final AdapterDefinitionProperties.AdapterDefinition definition) {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(definition.getCapabilities().isChat())
                .embedding(definition.getCapabilities().isEmbedding())
                .rerank(definition.getCapabilities().isRerank())
                .tts(definition.getCapabilities().isTts())
                .stt(definition.getCapabilities().isStt())
                .imageGenerate(definition.getCapabilities().isImgGen())
                .imageEdit(definition.getCapabilities().isImgEdit())
                .streaming(definition.getCapabilities().isStreaming())
                .build();

        return new OllamaConfigurableAdapter(
                adapterContext, requestProcessingSupport, resilienceSupport,
                name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders()
        );
    }

    private ConfigurableAdapter createConfigurableAdapter(final String name,
                                                          final AdapterDefinitionProperties.AdapterDefinition definition) {
        AdapterCapabilities capabilities = AdapterCapabilities.builder()
                .chat(definition.getCapabilities().isChat())
                .embedding(definition.getCapabilities().isEmbedding())
                .rerank(definition.getCapabilities().isRerank())
                .tts(definition.getCapabilities().isTts())
                .stt(definition.getCapabilities().isStt())
                .imageGenerate(definition.getCapabilities().isImgGen())
                .imageEdit(definition.getCapabilities().isImgEdit())
                .streaming(definition.getCapabilities().isStreaming())
                .build();

        return new ConfigurableAdapter(
                adapterContext, requestProcessingSupport, resilienceSupport,
                name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders(),
                openAiRequestTransformer, openAiResponseTransformer
        );
    }

    /**
     * Adapter定义请求体
     */
    public static class AdapterDefinitionRequest {
        private String name;
        private String type;
        private Map<String, Boolean> capabilities;
        private Map<String, String> auth;
        private Map<String, String> additionalHeaders;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public Map<String, Boolean> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(final Map<String, Boolean> capabilities) {
            this.capabilities = capabilities;
        }

        public Map<String, String> getAuth() {
            return auth;
        }

        public void setAuth(final Map<String, String> auth) {
            this.auth = auth;
        }

        public Map<String, String> getAdditionalHeaders() {
            return additionalHeaders;
        }

        public void setAdditionalHeaders(final Map<String, String> additionalHeaders) {
            this.additionalHeaders = additionalHeaders;
        }
    }
}
