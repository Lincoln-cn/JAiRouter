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
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.util.*;

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
    private static final String CONFIG_KEY = "model-router-config";

    private final ConfigurationService configurationService;
    private final StoreManager storeManager;

    @Autowired
    public ConfigurationVersionController(ConfigurationService configurationService, StoreManager storeManager) {
        this.configurationService = configurationService;
        this.storeManager = storeManager;
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
        try {
            List<Integer> versions = configurationService.getAllVersions();
            return Mono.just(RouterResponse.success(versions, "获取配置版本列表成功"));
        } catch (Exception e) {
            logger.error("获取配置版本列表失败", e);
            return Mono.just(RouterResponse.error("获取配置版本列表失败: " + e.getMessage()));
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "404", description = "指定版本的配置不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Map<String, Object>>> getConfigByVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            Map<String, Object> config = configurationService.getVersionConfig(version);
            if (config == null) {
                return Mono.just(RouterResponse.error("指定版本的配置不存在: " + version));
            }
            return Mono.just(RouterResponse.success(config, "获取配置版本详情成功"));
        } catch (Exception e) {
            logger.error("获取配置版本详情失败: version={}", version, e);
            return Mono.just(RouterResponse.error("获取配置版本详情失败: " + e.getMessage()));
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "回滚配置失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Void>> rollbackToVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            configurationService.applyVersion(version);
            return Mono.just(RouterResponse.success((Void) null, "配置已成功回滚到版本: " + version));
        } catch (IllegalArgumentException e) {
            logger.warn("回滚配置失败，版本不存在: version={}", version, e);
            return Mono.just(RouterResponse.error("回滚配置失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("回滚配置失败: version={}", version, e);
            return Mono.just(RouterResponse.error("回滚配置失败: " + e.getMessage()));
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "不能删除当前版本")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Void>> deleteConfigVersion(
            @Parameter(description = "版本号", example = "1")
            @PathVariable int version) {
        try {
            // 不允许删除当前版本
            int currentVersion = configurationService.getCurrentVersion();
            if (version == currentVersion) {
                return Mono.just(RouterResponse.error("不能删除当前版本"));
            }

            // 调用StoreManager删除指定版本
            configurationService.deleteConfigVersion(version);
            return Mono.just(RouterResponse.success((Void) null, "配置版本删除成功"));
        } catch (Exception e) {
            logger.error("删除配置版本失败: version={}", version, e);
            return Mono.just(RouterResponse.error("删除配置版本失败: " + e.getMessage()));
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
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<RouterResponse<Integer>> getCurrentVersion() {
        try {
            int currentVersion = configurationService.getCurrentVersion();
            return Mono.just(RouterResponse.success(currentVersion, "获取当前配置版本成功"));
        } catch (Exception e) {
            logger.error("获取当前配置版本失败", e);
            return Mono.just(RouterResponse.error("获取当前配置版本失败: " + e.getMessage()));
        }
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
    public Mono<RouterResponse<Void>> applyVersion(
            @PathVariable int version) {
        try {
            configurationService.applyVersion(version);
            return Mono.just(RouterResponse.success((Void) null, "配置应用成功"));
        } catch (IllegalArgumentException e) {
            logger.warn("配置内容不合法", e);
            return Mono.just(RouterResponse.error("配置内容不合法: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("应用配置失败", e);
            return Mono.just(RouterResponse.error("应用配置失败: " + e.getMessage()));
        }
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
    public Mono<RouterResponse<List<VersionInfo>>> getAllVersionInfo() {
        try {
            // 获取所有版本号
            List<Integer> versionNumbers = configurationService.getAllVersions();
            
            // 获取当前版本号
            int currentVersion = configurationService.getCurrentVersion();
            
            // 构建所有版本的详细信息
            List<VersionInfo> versionInfos = new ArrayList<>();
            
            for (Integer version : versionNumbers) {
                try {
                    // 获取配置详情
                    Map<String, Object> config = configurationService.getVersionConfig(version);
                    
                    VersionInfo versionInfo = new VersionInfo();
                    versionInfo.setVersion(version);
                    versionInfo.setConfig(config != null ? config : new HashMap<>());
                    versionInfo.setCurrent(version == currentVersion);
                    
                    versionInfos.add(versionInfo);
                } catch (Exception e) {
                    logger.warn("获取版本 {} 详情失败: {}", version, e.getMessage());
                    // 即使某个版本获取失败，也继续处理其他版本
                    VersionInfo versionInfo = new VersionInfo();
                    versionInfo.setVersion(version);
                    versionInfo.setConfig(new HashMap<>());
                    versionInfo.setCurrent(version == currentVersion);
                    versionInfos.add(versionInfo);
                }
            }
            
            // 按版本号降序排列
            versionInfos.sort((a, b) -> b.getVersion().compareTo(a.getVersion()));
            
            return Mono.just(RouterResponse.success(versionInfos, "获取所有版本详细信息成功"));
        } catch (Exception e) {
            logger.error("获取所有版本详细信息失败", e);
            return Mono.just(RouterResponse.error("获取所有版本详细信息失败: " + e.getMessage()));
        }
    }

    /**
     * 版本信息数据传输对象
     */
    public static class VersionInfo {
        private Integer version;
        private Map<String, Object> config;
        private Boolean current;

        // Getters and setters
        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }

        public Boolean getCurrent() {
            return current;
        }

        public void setCurrent(Boolean current) {
            this.current = current;
        }
    }
}