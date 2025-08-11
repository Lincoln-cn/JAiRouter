package org.unreal.modelrouter.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.store.ConfigurationVersionService;
import org.unreal.modelrouter.response.ApiResponse;

import java.util.List;
import java.util.Map;

/**
 * 配置版本管理控制器
 * 提供配置版本查询、回滚等管理接口
 */
@RestController
@RequestMapping("/api/config/version")
@CrossOrigin(origins = "*")
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
    public ResponseEntity<ApiResponse<List<Integer>>> getConfigVersions() {
        try {
            List<Integer> versions = configurationVersionService.getConfigVersions();
            return ResponseEntity.ok(ApiResponse.success(versions, "获取配置版本列表成功"));
        } catch (Exception e) {
            logger.error("获取配置版本列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取配置版本列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定版本的配置详情
     *
     * @param version 版本号
     * @return 配置内容
     */
    @GetMapping("/{version}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfigByVersion(@PathVariable int version) {
        try {
            Map<String, Object> config = configurationVersionService.getConfigByVersion(version);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("指定版本的配置不存在: " + version));
            }
            return ResponseEntity.ok(ApiResponse.success(config, "获取配置版本详情成功"));
        } catch (Exception e) {
            logger.error("获取配置版本详情失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取配置版本详情失败: " + e.getMessage()));
        }
    }

    /**
     * 回滚到指定版本的配置
     *
     * @param version 版本号
     * @return 操作结果
     */
    @PostMapping("/rollback/{version}")
    public ResponseEntity<ApiResponse<Void>> rollbackToVersion(@PathVariable int version) {
        try {
            configurationVersionService.rollbackToVersion(version);
            return ResponseEntity.ok(ApiResponse.success("配置已成功回滚到版本: " + version));
        } catch (IllegalArgumentException e) {
            logger.warn("回滚配置失败，版本不存在: version={}", version, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("回滚配置失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("回滚配置失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("回滚配置失败: " + e.getMessage()));
        }
    }

    /**
     * 删除指定版本的配置
     *
     * @param version 版本号
     * @return 操作结果
     */
    @DeleteMapping("/{version}")
    public ResponseEntity<ApiResponse<Void>> deleteConfigVersion(@PathVariable int version) {
        try {
            // 不允许删除当前版本
            int currentVersion = configurationVersionService.getCurrentVersion();
            if (version == currentVersion) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("不能删除当前版本"));
            }

            configurationVersionService.deleteConfigVersion(version);
            return ResponseEntity.ok(ApiResponse.success("配置版本 " + version + " 已删除"));
        } catch (Exception e) {
            logger.error("删除配置版本失败: version={}", version, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除配置版本失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前配置版本
     *
     * @return 当前版本号
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Integer>> getCurrentVersion() {
        try {
            int currentVersion = configurationVersionService.getCurrentVersion();
            return ResponseEntity.ok(ApiResponse.success(currentVersion, "获取当前配置版本成功"));
        } catch (Exception e) {
            logger.error("获取当前配置版本失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取当前配置版本失败: " + e.getMessage()));
        }
    }
}
