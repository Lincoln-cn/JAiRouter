package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.test.AdapterTestResult;
import org.unreal.modelrouter.router.adapter.test.AdapterTestService;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 适配器测试控制器
 * 提供适配器连通性测试 API
 */
@RestController
@RequestMapping("/api/config/adapter")
@CrossOrigin(origins = "*")
@Tag(name = "适配器测试", description = "提供适配器连通性测试接口")
public class AdapterTestController {

    private static final Logger logger = LoggerFactory.getLogger(AdapterTestController.class);

    private final AdapterTestService testService;
    private final AdapterRegistry adapterRegistry;

    public AdapterTestController(final AdapterTestService testService,
                                 final AdapterRegistry adapterRegistry) {
        this.testService = testService;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 测试已注册的适配器
     */
    @PostMapping("/{name}/test")
    @Operation(summary = "测试适配器", description = "测试指定适配器的连通性")
    @ApiResponse(responseCode = "200", description = "测试完成")
    @ApiResponse(responseCode = "404", description = "适配器不存在")
    public Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> testAdapter(
            @Parameter(description = "适配器名称") @PathVariable final String name,
            @RequestBody final Map<String, Object> request) {
        try {
            if (!adapterRegistry.isAdapterSupported(name)) {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("适配器不存在: " + name, "ADAPTER_NOT_FOUND")));
            }

            String testType = (String) request.getOrDefault("testType", "PING");
            String apiKey = (String) request.get("apiKey");
            String model = (String) request.get("model");
            String baseUrl = (String) request.get("baseUrl");

            return testService.testRegisteredAdapter(name, testType, apiKey, model, baseUrl)
                    .map(result -> ResponseEntity.ok(RouterResponse.success(result, "测试完成")))
                    .onErrorResume(ex -> {
                        logger.error("测试适配器失败: {}", name, ex);
                        return Mono.just(ResponseEntity.internalServerError()
                                .body(RouterResponse.error("测试失败: " + ex.getMessage())));
                    });
        } catch (Exception e) {
            logger.error("测试适配器异常: {}", name, e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("测试异常: " + e.getMessage())));
        }
    }

    /**
     * 预览测试（测试未注册的适配器配置）
     */
    @PostMapping("/test")
    @Operation(summary = "预览测试", description = "测试未注册的适配器配置")
    @ApiResponse(responseCode = "200", description = "测试完成")
    @ApiResponse(responseCode = "400", description = "请求参数无效")
    public Mono<ResponseEntity<RouterResponse<AdapterTestResult>>> testPreview(
            @RequestBody final Map<String, Object> request) {
        String type = (String) request.get("type");
        String baseUrl = (String) request.get("baseUrl");
        String testType = (String) request.getOrDefault("testType", "PING");
        String authHeaderName = (String) request.getOrDefault("authHeaderName", "Authorization");
        String authHeaderPrefix = (String) request.getOrDefault("authHeaderPrefix", "Bearer ");
        String apiKey = (String) request.get("apiKey");
        String model = (String) request.get("model");

        if (type == null || type.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("适配器类型不能为空", "INVALID_REQUEST")));
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("Base URL 不能为空", "INVALID_REQUEST")));
        }

        String authHeaderValue = apiKey != null && !apiKey.isBlank()
                ? authHeaderPrefix + apiKey : "";

        return testService.testPreview(type, baseUrl, authHeaderName, authHeaderValue, testType, model)
                .map(result -> ResponseEntity.ok(RouterResponse.success(result, "测试完成")))
                .onErrorResume(ex -> {
                    logger.error("预览测试失败", ex);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(RouterResponse.error("测试失败: " + ex.getMessage())));
                });
    }
}
