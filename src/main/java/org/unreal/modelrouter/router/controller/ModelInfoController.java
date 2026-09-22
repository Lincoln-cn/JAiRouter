package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.common.exception.ApiExceptions;

/**
 * 模型信息控制器 - 处理模型信息查询相关接口（控制台面，返回 RouterResponse 包裹体）.
 *
 * <p>v3.1: 模型汇总逻辑抽取到 {@link ModelCatalogService}，与 OpenAI 原生面
 * {@code GET /v1/models} 共用同一份数据，字段集合保持不变。</p>
 */
@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
@Tag(name = "模型信息接口", description = "提供模型信息查询相关接口")
public class ModelInfoController {

    private static final Logger logger = LoggerFactory.getLogger(ModelInfoController.class);

    private final ModelCatalogService modelCatalogService;

    /**
     * 构造函数.
     *
     * @param modelCatalogService 模型目录服务
     */
    public ModelInfoController(final ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    /**
     * 获取所有可用模型
     */
    @GetMapping
    @Operation(
        summary = "获取所有可用模型",
        description = "获取系统中所有可用的模型列表，包括模型基本信息和服务类型",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "成功获取模型列表",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "500", 
                description = "服务器内部错误",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)
                )
            )
        }
    )
    public Mono<RouterResponse<Object>> getModels() {
        try {
            return Mono.just(RouterResponse.success(
                    modelCatalogService.listAllModelsAsOpenAiList(), "获取模型列表成功"));
        } catch (Exception e) {
            logger.error("获取模型列表失败", e);
            return Mono.error(ApiExceptions.wrap(e, "获取模型列表失败"));
        }
    }
}
