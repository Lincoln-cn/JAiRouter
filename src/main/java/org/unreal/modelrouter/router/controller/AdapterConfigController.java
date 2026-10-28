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
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.impl.ConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.impl.ExtendedAdapter;
import org.unreal.modelrouter.router.adapter.impl.OllamaConfigurableAdapter;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;
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
    private final AdapterDefinitionPersistenceService persistenceService;
    private final AdapterContext adapterContext;
    private final RequestProcessingSupport requestProcessingSupport;
    private final ResilienceSupport resilienceSupport;
    private final OpenAiRequestTransformer openAiRequestTransformer;
    private final OpenAiResponseTransformer openAiResponseTransformer;

    public AdapterConfigController(final AdapterRegistry adapterRegistry,
                                   final AdapterDefinitionProperties adapterDefinitionProperties,
                                   final AdapterDefinitionPersistenceService persistenceService,
                                   final AdapterContext adapterContext,
                                   final RequestProcessingSupport requestProcessingSupport,
                                   final ResilienceSupport resilienceSupport,
                                   final OpenAiRequestTransformer openAiRequestTransformer,
                                   final OpenAiResponseTransformer openAiResponseTransformer) {
        this.adapterRegistry = adapterRegistry;
        this.adapterDefinitionProperties = adapterDefinitionProperties;
        this.persistenceService = persistenceService;
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
                    .body(RouterResponse.error("获取adapter列表失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
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
                        .body(RouterResponse.error("adapter不存在: " + name, ErrorCode.NOT_FOUND.code()));
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
                    .body(RouterResponse.error("获取adapter详情失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
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
        // issue #432：请求体缺失属**客户端可归因**的失败 ⇒ 前置校验、天然 400。
        // ⚠️ 必须放在下面的 try **之外**：ServerWebInputException 是 RuntimeException，
        // 放进 try 会被本方法自己的兜底 `catch (Exception e)` 吞掉并重新包成 500 + INTERNAL_ERROR
        // （实测踩到：客户端拿到「更新adapter失败: 400 BAD_REQUEST "Request body is required"」+ 500）。
        // 放在 try 之外 ⇒ 交给中央处理器 ReactiveGlobalExceptionHandler 映射为 400。
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error("adapter名称不能为空", ErrorCode.INVALID_NAME.code()));
            }

            if (adapterRegistry.isBuiltinAdapter(request.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(RouterResponse.error(
                                "不能覆盖内置adapter: " + request.getName(), ErrorCode.CONFLICT_BUILTIN.code()));
            }

            if (adapterRegistry.isAdapterSupported(request.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(RouterResponse.error(
                                "adapter名称已存在: " + request.getName(), ErrorCode.CONFLICT_EXISTS.code()));
            }

            // issue #709：值级校验必须在构造 definition **之前** —— 下面的 caps.setChat(...) 会把
            // getOrDefault 的结果直接拆箱成 boolean，而「键存在、值为 null」时 getOrDefault 仍返回
            // null ⇒ 拆箱 NPE，被兜底 catch 包成 500，客户端完全看不出是哪个字段写错了。
            final String valueError = validateRequestValues(request);
            if (valueError != null) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error(valueError, ErrorCode.INVALID_PARAM.code()));
            }

            AdapterDefinitionProperties.AdapterDefinition definition =
                    new AdapterDefinitionProperties.AdapterDefinition();
            definition.setType(request.getType() != null ? request.getType() : "openai-compatible");
            definition.setParent(request.getParent());  // 设置父adapter

            if (request.getCapabilities() != null) {
                AdapterDefinitionProperties.CapabilitiesConfig caps =
                        new AdapterDefinitionProperties.CapabilitiesConfig();
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

            // issue #709：先校验「构造 adapter 时真正会被解引用」的必填字段，再做任何写入。
            // 修复前的顺序是「先 put + saveDefinition，再 createAdapterByType」：缺 auth/capabilities 时
            // 后者抛 NPE、被本方法兜底 catch 包成 500，而**不完整的定义已经落库** ⇒ 客户端重试只会撞上
            // 「adapter名称已存在」的 409，永远拿不到 201，仓库里则留着一条重启后会加载失败的脏定义。
            final String validationError = validateDefinitionRequest(request, definition);
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error(validationError, ErrorCode.INVALID_PARAM.code()));
            }

            // 先构造：构造函数只读入参、不触碰任何共享状态 ⇒ 抛错时仓库与注册表都保持原样
            ServiceCapability adapter = createAdapterByType(request.getName(), definition);

            // 再落库：写失败时内存里的定义表与注册表还没被改过
            persistenceService.saveDefinition(request.getName(), definition);

            // 最后切换内存态
            adapterDefinitionProperties.getAdapterDefinitions().put(request.getName(), definition);
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
                    .body(RouterResponse.error("创建adapter失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
        }
    }

    /**
     * 更新配置驱动adapter
     */
    @PutMapping("/{name}")
    @Operation(summary = "更新adapter", description = "更新配置驱动adapter的配置")
    @ApiResponse(responseCode = "200", description = "adapter更新成功")
    @ApiResponse(responseCode = "400", description = "请求参数无效（缺 auth / capabilities 等必填字段）")
    @ApiResponse(responseCode = "404", description = "adapter不存在")
    @ApiResponse(responseCode = "403", description = "不能更新内置adapter")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateAdapter(
            @Parameter(description = "adapter名称") @PathVariable final String name,
            @RequestBody final AdapterDefinitionRequest request) {
        // issue #432：同 createAdapter —— 请求体缺失前置校验为 400；**必须在 try 之外**，
        // 否则会被本方法的兜底 catch 吞成 500（理由见 createAdapter 处的注释）。
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }
        try {
            if (adapterRegistry.isBuiltinAdapter(name)) {
                // issue #405：内置项只读 = 授权语义，端点与规则链（ApiException.legacyResolveStatus）都是 403
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RouterResponse.error("不能更新内置adapter: " + name, ErrorCode.BUILTIN_READONLY.code()));
            }

            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                    adapterDefinitionProperties.getAdapterDefinitions();
            if (!definitions.containsKey(name)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("adapter不存在: " + name, ErrorCode.NOT_FOUND.code()));
            }

            // issue #709：值级校验必须在构造 definition **之前** —— 下面的 caps.setChat(...) 会把
            // getOrDefault 的结果直接拆箱成 boolean，而「键存在、值为 null」时 getOrDefault 仍返回
            // null ⇒ 拆箱 NPE，被兜底 catch 包成 500，客户端完全看不出是哪个字段写错了。
            final String valueError = validateRequestValues(request);
            if (valueError != null) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error(valueError, ErrorCode.INVALID_PARAM.code()));
            }


            AdapterDefinitionProperties.AdapterDefinition definition =
                    new AdapterDefinitionProperties.AdapterDefinition();
            definition.setType(request.getType() != null ? request.getType() : "openai-compatible");
            definition.setParent(request.getParent());  // 设置父adapter

            if (request.getCapabilities() != null) {
                AdapterDefinitionProperties.CapabilitiesConfig caps =
                        new AdapterDefinitionProperties.CapabilitiesConfig();
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

            // issue #709：与 createAdapter 同一顺序 —— 先校验、再构造、再落库、最后切内存。
            // 修复前是「先 definitions.put(name, ...) + saveDefinition，再 createAdapterByType」：
            // 缺 auth/capabilities 时既把**原来可用的定义覆盖成不完整的**，又返回 500。
            final String validationError = validateDefinitionRequest(request, definition);
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(RouterResponse.error(validationError, ErrorCode.INVALID_PARAM.code()));
            }

            ServiceCapability adapter = createAdapterByType(name, definition);

            persistenceService.saveDefinition(name, definition);

            definitions.put(name, definition);
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
                    .body(RouterResponse.error("更新adapter失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
        }
    }

    /**
     * 删除配置驱动adapter
     */
    @DeleteMapping("/{name}")
    @Operation(summary = "删除adapter", description = "删除配置驱动adapter")
    @ApiResponse(responseCode = "200", description = "adapter删除成功")
    @ApiResponse(responseCode = "404", description = "adapter不存在")
    @ApiResponse(responseCode = "403", description = "不能删除内置adapter")
    public ResponseEntity<RouterResponse<Void>> deleteAdapter(
            @Parameter(description = "adapter名称") @PathVariable final String name) {
        try {
            if (adapterRegistry.isBuiltinAdapter(name)) {
                // issue #405：内置项只读 = 授权语义，端点与规则链（ApiException.legacyResolveStatus）都是 403
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RouterResponse.error("不能删除内置adapter: " + name, ErrorCode.BUILTIN_READONLY.code()));
            }

            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions =
                    adapterDefinitionProperties.getAdapterDefinitions();
            if (!definitions.containsKey(name)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("adapter不存在: " + name, ErrorCode.NOT_FOUND.code()));
            }

            // 从配置中移除
            definitions.remove(name);

            // 从持久化存储中移除
            persistenceService.removeDefinition(name);

            // 从registry中移除
            adapterRegistry.removeAdapter(name);

            logger.info("Deleted configurable adapter: {}", name);
            return ResponseEntity.ok(RouterResponse.success("adapter删除成功"));
        } catch (Exception e) {
            logger.error("删除adapter失败: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("删除adapter失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
        }
    }

    /**
     * 获取可用于继承的父adapter列表
     */
    @GetMapping("/parents")
    @Operation(summary = "获取父adapter列表", description = "获取可用于继承的adapter列表")
    public ResponseEntity<RouterResponse<List<Map<String, Object>>>> getParentAdapters() {
        try {
            Map<String, ServiceCapability> allAdapters = adapterRegistry.getAllAdapters();
            List<Map<String, Object>> parentList = new ArrayList<>();

            for (Map.Entry<String, ServiceCapability> entry : allAdapters.entrySet()) {
                String adapterName = entry.getKey();
                ServiceCapability adapter = entry.getValue();

                // 排除extend类型的adapter（不能作为父adapter）
                if (adapter instanceof ExtendedAdapter) {
                    continue;
                }

                Map<String, Object> info = new HashMap<>();
                info.put("name", adapterName);
                info.put("source", adapterRegistry.isBuiltinAdapter(adapterName) ? "builtin" : "configurable");
                parentList.add(info);
            }

            return ResponseEntity.ok(RouterResponse.success(parentList, "获取父adapter列表成功"));
        } catch (Exception e) {
            logger.error("获取父adapter列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取父adapter列表失败: " + e.getMessage(),
                            ErrorCode.INTERNAL_ERROR.code()));
        }
    }

    /**
     * 校验请求体里「会以拆箱或原样透传方式进入 adapter 定义」的取值（issue #709）。
     *
     * <p>必须早于定义构造：{@code createAdapter} / {@code updateAdapter} 用
     * {@code request.getCapabilities().getOrDefault("chat", false)} 填充布尔字段，而
     * {@link java.util.Map#getOrDefault} 对「键存在但值为 {@code null}」仍然返回 {@code null}，
     * 拆箱成 {@code boolean} 即抛 NPE ⇒ 修复前被兜底 {@code catch (Exception e)} 包成 500。</p>
     *
     * <p>{@code auth} 的取值也是原样透传进 {@code AuthConfig}，{@code null} 会被落库、运行时拼不出
     * 鉴权头；空白 {@code headerName} 同理。</p>
     *
     * @param request 请求体
     * @return 校验通过返回 {@code null}；否则返回要回给客户端的 400 消息
     */
    private String validateRequestValues(final AdapterDefinitionRequest request) {
        if (request.getAuth() != null) {
            for (Map.Entry<String, String> authEntry : request.getAuth().entrySet()) {
                if (authEntry.getValue() == null) {
                    return "auth 里 " + authEntry.getKey() + " 的取值不能为 null："
                            + "null 会被原样写进 adapter 定义并落库，运行时据此拼不出鉴权头";
                }
            }
            if (request.getAuth().containsKey("headerName")
                    && request.getAuth().get("headerName").isBlank()) {
                return "auth.headerName 不能为空白：它会原样用作向上游发请求时的 HTTP 头名";
            }
        }
        if (request.getCapabilities() != null) {
            for (Map.Entry<String, Boolean> capability : request.getCapabilities().entrySet()) {
                if (capability.getValue() == null) {
                    return "capabilities 里 " + capability.getKey() + " 的取值不能为 null："
                            + "构造 adapter 时 getOrDefault 对「键存在但值为 null」仍返回 null，"
                            + "拆箱成 boolean 即抛 NullPointerException（此前被兜底成 500）";
                }
            }
        }
        return null;
    }

    /**
     * 校验构造 adapter 所需、但此前只在 {@code createAdapterByType} 里被直接解引用的必填字段（issue #709）。
     *
     * <p>修复前：缺 {@code auth} 时 {@code definition.getAuth().getHeaderName()} 抛 NPE、缺
     * {@code capabilities} 时 {@code definition.getCapabilities().isChat()} 抛 NPE，都被控制器的兜底
     * {@code catch (Exception e)} 包成 500。这两个字段决定「向上游发请求时用哪个头、支持哪些能力」，
     * 缺失属请求不合法 ⇒ 400，并且**不能**先把不完整定义写进仓库。</p>
     *
     * <p>{@code auth} 对所有类型必填：{@code openai-compatible} / {@code ollama-compatible} /
     * {@code extend} 三条构造路径都会解引用它。{@code capabilities} 对非 {@code extend} 类型必填
     * （{@code extend} 可省略 ⇒ 继承父 adapter 的能力）。{@code extend} 还要求 {@code parent}
     * 非空且已注册，否则同样只在构造期抛 {@link IllegalArgumentException}（此前也是 500）。</p>
     *
     * @param request    请求体
     * @param definition 由请求体构造出的定义（尚未落库、尚未注册）
     * @return 校验通过返回 {@code null}；否则返回要回给客户端的 400 消息
     */
    private String validateDefinitionRequest(final AdapterDefinitionRequest request,
                                             final AdapterDefinitionProperties.AdapterDefinition definition) {
        if (request.getAuth() == null || request.getAuth().isEmpty()) {
            return "auth 必填：请显式给出 auth（至少 headerName 或 headerPrefix；"
                    + "缺省值 headerName=Authorization、headerPrefix=\"Bearer \"）";
        }
        if ("extend".equals(definition.getType())) {
            final String parent = definition.getParent();
            if (parent == null || parent.isBlank()) {
                return "type=extend 时 parent 必填：请指定要继承的父 adapter";
            }
            if (adapterRegistry.getAdapterByName(parent) == null) {
                return "parent adapter 不存在: " + parent;
            }
            return null;
        }
        if (request.getCapabilities() == null || request.getCapabilities().isEmpty()) {
            return "capabilities 必填：请显式声明能力（chat/embedding/rerank/tts/stt/imgGen/imgEdit/streaming）";
        }
        return null;
    }

    private ServiceCapability createAdapterByType(
            final String name,
            final AdapterDefinitionProperties.AdapterDefinition definition) {
        String type = definition.getType() != null ? definition.getType() : "openai-compatible";

        if ("extend".equals(type)) {
            return createExtendedAdapter(name, definition);
        } else if ("ollama-compatible".equals(type)) {
            return createOllamaConfigurableAdapter(name, definition);
        }
        return createConfigurableAdapter(name, definition);
    }

    private ExtendedAdapter createExtendedAdapter(
            final String name,
            final AdapterDefinitionProperties.AdapterDefinition definition) {
        String parentName = definition.getParent();
        if (parentName == null || parentName.isBlank()) {
            throw new IllegalArgumentException("Extended adapter must specify a parent adapter");
        }

        ServiceCapability parentAdapter = adapterRegistry.getAdapterByName(parentName);
        if (parentAdapter == null) {
            throw new IllegalArgumentException("Parent adapter '" + parentName + "' not found");
        }

        AdapterCapabilities capabilities = null;
        if (definition.getCapabilities() != null) {
            capabilities = AdapterCapabilities.builder()
                    .chat(definition.getCapabilities().isChat())
                    .embedding(definition.getCapabilities().isEmbedding())
                    .rerank(definition.getCapabilities().isRerank())
                    .tts(definition.getCapabilities().isTts())
                    .stt(definition.getCapabilities().isStt())
                    .imageGenerate(definition.getCapabilities().isImgGen())
                    .imageEdit(definition.getCapabilities().isImgEdit())
                    .streaming(definition.getCapabilities().isStreaming())
                    .build();
        }

        return new ExtendedAdapter(
                parentAdapter, name, capabilities,
                definition.getAuth().getHeaderName(),
                definition.getAuth().getHeaderPrefix(),
                definition.getAdditionalHeaders()
        );
    }

    private OllamaConfigurableAdapter createOllamaConfigurableAdapter(
            final String name,
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

    private ConfigurableAdapter createConfigurableAdapter(
            final String name,
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
        private String parent;  // 继承模式下的父adapter名称
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

        public String getParent() {
            return parent;
        }

        public void setParent(final String parent) {
            this.parent = parent;
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
