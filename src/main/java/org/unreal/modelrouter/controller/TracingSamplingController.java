package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.controller.response.RouterResponse;

import java.util.Map;

/**
 * 追踪采样配置控制器
 *
 * 提供追踪采样配置的管理接口，包括查询、更新和重置功能
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config/tracing/sampling")
@Tag(name = "追踪采样配置", description = "管理追踪采样配置")
public class TracingSamplingController {

    private final ConfigurationService configurationService;

    /**
     * 获取当前追踪采样配置
     *
     * @return 追踪采样配置
     */
    @GetMapping
    @Operation(summary = "获取追踪采样配置", description = "获取当前的追踪采样配置")
    @ApiResponse(responseCode = "200", description = "成功获取配置",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    public ResponseEntity<RouterResponse<Map<String, Object>>> getTracingSamplingConfig() {
        try {
            Map<String, Object> config = configurationService.getTracingSamplingConfig();
            log.info("成功获取追踪采样配置");
            return ResponseEntity.ok(RouterResponse.success(config, "获取追踪采样配置成功"));
        } catch (Exception e) {
            log.error("获取追踪采样配置失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取追踪采样配置失败: " + e.getMessage()));
        }
    }

    /**
     * 更新追踪采样配置
     *
     * @param samplingConfig 新的采样配置
     * @param createNewVersion 是否创建新版本
     * @return 更新结果
     */
    @PutMapping
    @Operation(summary = "更新追踪采样配置", description = "更新追踪采样配置")
    @ApiResponse(responseCode = "200", description = "配置更新成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> updateTracingSamplingConfig(
            @Parameter(description = "采样配置") @RequestBody Map<String, Object> samplingConfig,
            @Parameter(description = "是否创建新版本") @RequestParam(defaultValue = "true") boolean createNewVersion) {
        try {
            configurationService.updateTracingSamplingConfig(samplingConfig, createNewVersion);
            log.info("追踪采样配置更新成功，创建新版本: {}", createNewVersion);
            return ResponseEntity.ok(RouterResponse.success("追踪采样配置更新成功"));
        } catch (Exception e) {
            log.error("更新追踪采样配置失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("更新追踪采样配置失败: " + e.getMessage()));
        }
    }

    /**
     * 重置追踪采样配置为默认值
     *
     * @param createNewVersion 是否创建新版本
     * @return 重置结果
     */
    @PostMapping("/reset")
    @Operation(summary = "重置追踪采样配置", description = "将追踪采样配置重置为默认值")
    @ApiResponse(responseCode = "200", description = "配置重置成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> resetTracingSamplingConfig(
            @Parameter(description = "是否创建新版本") @RequestParam(defaultValue = "true") boolean createNewVersion) {
        try {
            configurationService.updateTracingSamplingConfig(Map.of(), createNewVersion);
            log.info("追踪采样配置重置成功，创建新版本: {}", createNewVersion);
            return ResponseEntity.ok(RouterResponse.success("追踪采样配置重置成功"));
        } catch (Exception e) {
            log.error("重置追踪采样配置失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("重置追踪采样配置失败: " + e.getMessage()));
        }
    }

}
