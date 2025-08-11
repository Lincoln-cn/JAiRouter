package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.store.ConfigurationVersionService;

import java.util.List;
import java.util.Map;

/**
 * 配置版本管理控制器
 * 提供配置版本查询、回滚等管理接口
 */
@RestController
@RequestMapping("/api/config/version")
@CrossOrigin(origins = "*")
@Tag(name = "配置版本管理", description = "提供配置版本的查询、回滚和删除等管理接口")
public class ConfigurationVersionController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationVersionController.class);

    private final ConfigurationVersionService configurationVersionService;

    @Autowired
    public ConfigurationVersionController(ConfigurationVersionService configurationVersionService) {
        this.configurationVersionService = configurationVersionService;
    }

    /**
     * 获取配置的所有版本列表
     *
     * @return 版本列表
     */
    @GetMapping
    @Operation(summary = "获取配置版本列表", description = "获取系统中所有配置版本的列表")
    @ApiResponse(responseCode = "200", description = "成功获取配置版本列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<List<Integer>>> getConfigVersions() {
        try {
            List<Integer> versions = configurationVersionService.getConfigVersions();
            return ResponseEntity.ok(RouterResponse.success(versions, "获取配置版本列表成功"));
        } catch (Exception e) {
            logger.error("获取配置版本列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取配置版本列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定版本的配置详情
     *
     * @param version 版本号
     * @return 配置内容
     */
    @GetMapping("/{version}")
    @Operation(summary = "获取指定版本配置详情", description = "根据版本号获取该版本的详细配置信息")
    @ApiResponse(responseCode = "200", description = "成功获取配置版本详情",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "指定版本的配置不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfigByVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            Map<String, Object> config = configurationVersionService.getConfigByVersion(version);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RouterResponse.error("指定版本的配置不存在: " + version));
            }
            return ResponseEntity.ok(RouterResponse.success(config, "获取配置版本详情成功"));
        } catch (Exception e) {
            logger.error("获取配置版本详情失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取配置版本详情失败: " + e.getMessage()));
        }
    }

    /**
     * 回滚到指定版本的配置
     *
     * @param version 版本号
     * @return 操作结果
     */
    @PostMapping("/rollback/{version}")
    @Operation(summary = "回滚到指定版本", description = "将系统配置回滚到指定版本")
    @ApiResponse(responseCode = "200", description = "配置回滚成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "回滚配置失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> rollbackToVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            configurationVersionService.rollbackToVersion(version);
            return ResponseEntity.ok(RouterResponse.success("配置已成功回滚到版本: " + version));
        } catch (IllegalArgumentException e) {
            logger.warn("回滚配置失败，版本不存在: version={}", version, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("回滚配置失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("回滚配置失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("回滚配置失败: " + e.getMessage()));
        }
    }

    /**
     * 删除指定版本的配置
     *
     * @param version 版本号
     * @return 操作结果
     */
    @DeleteMapping("/{version}")
    @Operation(summary = "删除指定版本", description = "删除指定的配置版本（不能删除当前版本）")
    @ApiResponse(responseCode = "200", description = "配置版本删除成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "不能删除当前版本")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Void>> deleteConfigVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            // 不允许删除当前版本
            int currentVersion = configurationVersionService.getCurrentVersion();
            if (version == currentVersion) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RouterResponse.error("不能删除当前版本"));
            }

            configurationVersionService.deleteConfigVersion(version);
            return ResponseEntity.ok(RouterResponse.success("配置版本 " + version + " 已删除"));
        } catch (Exception e) {
            logger.error("删除配置版本失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("删除配置版本失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前配置版本
     *
     * @return 当前版本号
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前配置版本", description = "获取系统当前正在使用的配置版本号")
    @ApiResponse(responseCode = "200", description = "成功获取当前配置版本",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Integer>> getCurrentVersion() {
        try {
            int currentVersion = configurationVersionService.getCurrentVersion();
            return ResponseEntity.ok(RouterResponse.success(currentVersion, "获取当前配置版本成功"));
        } catch (Exception e) {
            logger.error("获取当前配置版本失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RouterResponse.error("获取当前配置版本失败: " + e.getMessage()));
        }
    }
}
