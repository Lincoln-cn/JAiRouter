package org.unreal.modelrouter.config.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.config.core.ConfigurationService;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.dto.VersionInfoResponse;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.config.version.diff.ConfigDiff;
import org.unreal.modelrouter.config.version.diff.VersionDiffService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置版本管理控制器 提供配置版本查询、回滚等管理接口
 */
@RestController
@RequestMapping("/api/config/version")
@Tag(name = "配置版本管理", description = "提供配置版本的查询、回滚和删除等管理接口")
public class ConfigurationVersionController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationVersionController.class);
    private static final String CONFIG_KEY = "model-router-config";

    private final ConfigurationService configurationService;
    private final ConfigVersionManager configVersionManager;  // 新增
    private final StoreManager storeManager;
    private final VersionDiffService versionDiffService;

    @Autowired
    public ConfigurationVersionController(final ConfigurationService configurationService,
                                          final ConfigVersionManager configVersionManager,  // 新增
                                          final StoreManager storeManager,
                                          final VersionDiffService versionDiffService) {
        this.configurationService = configurationService;
        this.configVersionManager = configVersionManager;  // 新增
        this.storeManager = storeManager;
        this.versionDiffService = versionDiffService;
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<List<Integer>>> getConfigVersions() {
        return Mono.fromSupplier(() -> {
            List<Integer> versions = configVersionManager.getAllVersions();
            return RouterResponse.success(versions, "获取配置版本列表成功");
        }).subscribeOn(Schedulers.boundedElastic());
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "404", description = "指定版本的配置不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Map<String, Object>>> getConfigByVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable("version") final int version) {
        return Mono.fromSupplier(() -> {
            Map<String, Object> config = configVersionManager.getVersionConfig(version);
            if (config == null) {
                return RouterResponse.<Map<String, Object>>error("指定版本的配置不存在: " + version);
            }
            return RouterResponse.success(config, "获取配置版本详情成功");
        }).subscribeOn(Schedulers.boundedElastic());
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "不能删除当前版本")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Void>> deleteConfigVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable("version") final int version) {
        return Mono.fromSupplier(() -> {
            int currentVersion = configVersionManager.getCurrentVersion();
            if (version == currentVersion) {
                return RouterResponse.<Void>error("不能删除当前版本");
            }
            // 调用ConfigurationService删除指定版本
            configVersionManager.deleteConfigVersion(version);
            return RouterResponse.<Void>success(null, "配置版本删除成功");
        }).subscribeOn(Schedulers.boundedElastic());
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Integer>> getCurrentVersion() {
        return Mono.fromSupplier(() -> {
            int currentVersion = configVersionManager.getCurrentVersion();
            return RouterResponse.success(currentVersion, "获取当前配置版本成功");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 应用指定版本的配置
     *
     * @param version 版本号
     * @return 操作结果
     */
    @PostMapping("/apply/{version}")
    @Operation(summary = "应用指定版本的配置", description = "应用指定版本的配置内容")
    @ApiResponse(responseCode = "200", description = "配置应用成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "配置内容不合法")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<String>> applyVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable("version") final int version) {
        return Mono.fromSupplier(() -> {
            configVersionManager.applyVersion(version);
            return RouterResponse.<String>success("配置应用成功");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取所有版本的详细信息
     *
     * @return 所有版本的详细信息列表
     */
    @GetMapping("/info")
    @Operation(summary = "获取所有版本详细信息", description = "一次性获取所有版本的详细信息，包括配置内容和压缩状态")
    @ApiResponse(responseCode = "200", description = "成功获取所有版本详细信息",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<List<VersionInfoResponse>>> getAllVersionInfo() {
        return Mono.fromSupplier(() -> {
            // 获取所有版本号
            List<Integer> versionNumbers = configVersionManager.getAllVersions();

            // 获取实际当前版本号
            int currentVersion = configVersionManager.getActualCurrentVersion();

            // 构建所有版本的详细信息
            List<VersionInfoResponse> versionInfos = new ArrayList<>();

            for (Integer version : versionNumbers) {
                // 获取配置详情（Map 格式）
                Map<String, Object> configMap = configVersionManager.getVersionConfig(version);

                VersionInfoResponse versionInfo = new VersionInfoResponse();
                versionInfo.setVersion(version);
                // 使用充血模型转换为强类型 DTO
                versionInfo.setConfigFromMap(configMap != null ? configMap : new HashMap<>());
                versionInfo.setCurrent(version == currentVersion);

                // 从配置中提取操作类型和详细信息
                if (configMap != null && configMap.containsKey("_metadata")) {
                    Map<String, Object> metadata = (Map<String, Object>) configMap.get("_metadata");
                    if (metadata != null) {
                        if (metadata.containsKey("operation")) {
                            versionInfo.setOperation((String) metadata.get("operation"));
                        }
                        if (metadata.containsKey("operationDetail")) {
                            versionInfo.setOperationDetail((String) metadata.get("operationDetail"));
                        }
                        if (metadata.containsKey("timestamp")) {
                            Object timestampObj = metadata.get("timestamp");
                            if (timestampObj instanceof Number) {
                                versionInfo.setTimestamp(((Number) timestampObj).longValue());
                            }
                        }
                    }
                }
                versionInfos.add(versionInfo);
            }
            // 按版本号降序排列
            versionInfos.sort((a, b) -> b.getVersion().compareTo(a.getVersion()));
            return RouterResponse.success(versionInfos, "获取所有版本详细信息成功");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 比较两个配置版本之间的差异
     *
     * @param sourceVersion 源版本号（较旧版本）
     * @param targetVersion 目标版本号（较新版本）
     * @return 版本差异信息
     */
    @GetMapping("/compare/{sourceVersion}/{targetVersion}")
    @Operation(summary = "比较两个版本差异",
               description = "比较两个配置版本之间的差异，返回新增、删除、修改的配置项")
    @ApiResponse(responseCode = "200", description = "成功获取版本差异",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "版本号不合法或版本不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<ConfigDiff>> compareVersions(
            @Parameter(description = "源版本号（较旧版本）", example = "1")
            @PathVariable("sourceVersion") final int sourceVersion,
            @Parameter(description = "目标版本号（较新版本）", example = "2")
            @PathVariable("targetVersion") final int targetVersion) {
        return Mono.fromSupplier(() -> {
            // 验证版本号
            if (sourceVersion < 0 || targetVersion < 0) {
                return RouterResponse.<ConfigDiff>error("版本号必须为非负数");
            }
            if (sourceVersion == targetVersion) {
                return RouterResponse.<ConfigDiff>error("源版本和目标版本不能相同");
            }

            try {
                ConfigDiff diff = versionDiffService.compareVersions(sourceVersion, targetVersion);
                return RouterResponse.success(diff, String.format(
                        "版本 %d 与 %d 对比完成，共发现 %d 处差异",
                        sourceVersion, targetVersion, diff.getTotalChanges()));
            } catch (IllegalArgumentException e) {
                return RouterResponse.<ConfigDiff>error(e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 比较指定版本与当前配置（上一个版本）的差异
     * 便捷接口，用于快速查看某个版本的变更内容
     *
     * @param version 版本号
     * @return 该版本相对于上一版本的差异
     */
    @GetMapping("/compare/{version}")
    @Operation(summary = "查看版本变更内容",
               description = "查看指定版本相对于上一版本的变更内容（第一版则与空配置对比）")
    @ApiResponse(responseCode = "200", description = "成功获取版本变更内容",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "版本不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<ConfigDiff>> getVersionChanges(
            @Parameter(description = "版本号", example = "2")
            @PathVariable("version") final int version) {
        return Mono.fromSupplier(() -> {
            if (version <= 0) {
                return RouterResponse.<ConfigDiff>error("版本号必须为正整数");
            }

            try {
                // 获取所有版本
                List<Integer> allVersions = configVersionManager.getAllVersions();
                if (!allVersions.contains(version)) {
                    return RouterResponse.<ConfigDiff>error("版本不存在: " + version);
                }

                // 找到上一版本
                int previousVersion = 0; // 0 表示初始状态（空配置）
                int index = allVersions.indexOf(version);
                if (index > 0) {
                    previousVersion = allVersions.get(index - 1);
                }

                ConfigDiff diff = versionDiffService.compareVersions(previousVersion, version);
                return RouterResponse.success(diff, String.format(
                        "版本 %d 的变更内容（基于版本 %d），共 %d 处变更",
                        version, previousVersion, diff.getTotalChanges()));
            } catch (IllegalArgumentException e) {
                return RouterResponse.<ConfigDiff>error(e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

}
