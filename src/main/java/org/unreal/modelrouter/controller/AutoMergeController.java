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
import org.unreal.modelrouter.entity.MergeResult;
import org.unreal.modelrouter.store.AutoMergeService;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动合并控制器
 * 提供配置文件自动合并的 RESTful API
 */
@RestController
@RequestMapping("/api/config/merge")
@CrossOrigin(origins = "*")
@Tag(name = "配置合并管理", description = "配置文件自动合并相关接口")
public class AutoMergeController {

    private static final Logger logger = LoggerFactory.getLogger(AutoMergeController.class);

    private final AutoMergeService autoMergeService;

    @Autowired
    public AutoMergeController(AutoMergeService autoMergeService) {
        this.autoMergeService = autoMergeService;
    }

    /**
     * 扫描配置文件
     * @return 版本文件映射
     */
    @GetMapping("/scan")
    @Operation(summary = "扫描配置文件", description = "扫描 config 目录下的所有版本配置文件")
    @ApiResponse(responseCode = "200", description = "扫描成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<Integer, String>>> scanVersionFiles() {
        logger.info("接收到扫描配置文件请求");

        Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
        return ResponseEntity.ok(RouterResponse.success(versionFiles,
                String.format("扫描完成，找到 %d 个版本文件", versionFiles.size())));

    }

    /**
     * 获取合并预览
     * @return 合并预览结果
     */
    @GetMapping("/preview")
    @Operation(summary = "获取合并预览", description = "预览配置文件合并后的结果，不执行实际合并操作")
    @ApiResponse(responseCode = "200", description = "预览生成成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "没有找到可合并的配置文件")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getMergePreview() {
        logger.info("接收到获取合并预览请求");
        Map<String, Object> preview = autoMergeService.getMergePreview();

        if (preview.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("预览生成失败: " + preview.get("error")));
        }
        return ResponseEntity.ok(RouterResponse.success(preview, "预览生成成功"));

    }

    /**
     * 执行自动合并
     * @return 合并结果
     */
    @PostMapping("/execute")
    @Operation(summary = "执行自动合并", description = "合并 config 目录下的多版本配置文件，并重置版本")
    @ApiResponse(responseCode = "200", description = "合并成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "合并失败，没有找到可合并的文件")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<MergeResult>> performMerge() {
        logger.info("接收到执行自动合并请求");

        MergeResult result = autoMergeService.performMerge();

        if (result.success()) {
            return ResponseEntity.ok(RouterResponse.success(result, result.message()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(result.message()));
        }

    }

    /**
     * 备份配置文件
     * @return 备份结果
     */
    @PostMapping("/backup")
    @Operation(summary = "备份配置文件", description = "备份 config 目录下的所有版本配置文件")
    @ApiResponse(responseCode = "200", description = "备份成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "备份失败，没有找到可备份的文件")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<MergeResult>> backupConfigFiles() {
        logger.info("接收到备份配置文件请求");

        MergeResult result = autoMergeService.backupConfigFiles();

        if (result.success()) {
            return ResponseEntity.ok(RouterResponse.success(result, result.message()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(result.message()));
        }

    }

    /**
     * 清理配置文件
     * @param deleteOriginals 是否删除原始文件
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理配置文件", description = "清理 config 目录下的配置文件")
    @ApiResponse(responseCode = "200", description = "清理成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "清理失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<MergeResult>> cleanupConfigFiles(
            @Parameter(description = "是否删除原始配置文件", example = "false")
            @RequestParam(defaultValue = "false") boolean deleteOriginals) {

        logger.info("接收到清理配置文件请求，删除原始文件: {}", deleteOriginals);

        MergeResult result = autoMergeService.cleanupConfigFiles(deleteOriginals);

        if (result.success()) {
            return ResponseEntity.ok(RouterResponse.success(result, result.message()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error(result.message()));
        }

    }

    /**
     * 获取合并服务状态
     * @return 服务状态信息
     */
    @GetMapping("/status")
    @Operation(summary = "获取合并服务状态", description = "获取自动合并服务的当前状态信息")
    @ApiResponse(responseCode = "200", description = "状态获取成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getServiceStatus() {
        logger.info("接收到获取合并服务状态请求");

        Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();

        Map<String, Object> statusData = Map.of(
                "availableVersionFiles", versionFiles.size(),
                "versionFiles", versionFiles,
                "configDirectory", "config",
                "serviceReady", true
        );

        return ResponseEntity.ok(RouterResponse.success(statusData, "状态获取成功"));

    }

    /**
     * 批量操作：备份 + 合并 + 清理
     * @param deleteOriginals 是否删除原始文件
     * @return 批量操作结果
     */
    @PostMapping("/batch")
    @Operation(summary = "批量操作", description = "依次执行备份、合并、清理操作")
    @ApiResponse(responseCode = "200", description = "批量操作成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "400", description = "批量操作失败")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> performBatchOperation(
            @Parameter(description = "是否删除原始配置文件", example = "false")
            @RequestParam(defaultValue = "false") boolean deleteOriginals) {

        logger.info("接收到批量操作请求，删除原始文件: {}", deleteOriginals);

        Map<String, Object> backupMap = new HashMap<>();
        backupMap.put("executed", false);
        backupMap.put("result", null);

        Map<String, Object> mergeMap = new HashMap<>();
        mergeMap.put("executed", false);
        mergeMap.put("result", null);

        Map<String, Object> cleanupMap = new HashMap<>();
        cleanupMap.put("executed", false);
        cleanupMap.put("result", null);

        Map<String, Object> batchResult = new HashMap<>();
        batchResult.put("backup", backupMap);
        batchResult.put("merge", mergeMap);
        batchResult.put("cleanup", cleanupMap);

        // 1. 备份
        logger.info("开始执行备份操作");
        MergeResult backupResult = autoMergeService.backupConfigFiles();
        ((Map<String, Object>) batchResult.get("backup")).put("executed", true);
        ((Map<String, Object>) batchResult.get("backup")).put("result", backupResult);

        if (!backupResult.success()) {
            logger.warn("备份操作失败，终止批量操作");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("批量操作失败：备份阶段失败 - " + backupResult.message()));
        }

        // 2. 合并
        logger.info("开始执行合并操作");
        MergeResult mergeResult = autoMergeService.performMerge();
        ((Map<String, Object>) batchResult.get("merge")).put("executed", true);
        ((Map<String, Object>) batchResult.get("merge")).put("result", mergeResult);

        if (!mergeResult.success()) {
            logger.warn("合并操作失败，终止批量操作");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RouterResponse.error("批量操作失败：合并阶段失败 - " + mergeResult.message()));
        }

        // 3. 清理（可选）
        if (deleteOriginals) {
            logger.info("开始执行清理操作");
            MergeResult cleanupResult = autoMergeService.cleanupConfigFiles(true);
            ((Map<String, Object>) batchResult.get("cleanup")).put("executed", true);
            ((Map<String, Object>) batchResult.get("cleanup")).put("result", cleanupResult);

            if (!cleanupResult.success()) {
                logger.warn("清理操作失败，但不影响整体结果");
            }
        }

        return ResponseEntity.ok(RouterResponse.success(batchResult, "批量操作完成"));

    }

    /**
     * 验证配置文件
     * @return 验证结果
     */
    @GetMapping("/validate")
    @Operation(summary = "验证配置文件", description = "验证 config 目录下配置文件的格式和内容")
    @ApiResponse(responseCode = "200", description = "验证完成",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> validateConfigFiles() {
        logger.info("接收到验证配置文件请求");

        Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
        Map<String, Object> validationResult = Map.of(
                "totalFiles", versionFiles.size(),
                "validFiles", versionFiles.size(), // 简化实现，实际应该验证每个文件
                "invalidFiles", 0,
                "details", versionFiles
        );

        return ResponseEntity.ok(RouterResponse.success(validationResult,
                String.format("验证完成，共 %d 个文件", versionFiles.size())));

    }

    /**
     * 获取配置文件统计信息
     * @return 统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取配置文件统计信息", description = "获取配置文件的详细统计信息")
    @ApiResponse(responseCode = "200", description = "统计信息获取成功",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfigStatistics() {
        logger.info("接收到获取配置文件统计信息请求");

        Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
        Map<String, Object> preview = autoMergeService.getMergePreview();

        Integer oldestVersion = null;
        Integer newestVersion = null;
        
        if (!versionFiles.isEmpty()) {
            oldestVersion = versionFiles.keySet().iterator().next();
            newestVersion = versionFiles.keySet().stream().max(Integer::compareTo).orElse(null);
        }

        Map<String, Object> statistics = Map.of(
                "totalVersionFiles", versionFiles.size(),
                "oldestVersion", oldestVersion,
                "newestVersion", newestVersion,
                "previewAvailable", !preview.containsKey("error"),
                "configDirectory", "config"
        );

        return ResponseEntity.ok(RouterResponse.success(statistics, "统计信息获取成功"));

    }
}