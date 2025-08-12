package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.store.AutoMergeService;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动合并控制器
 * 提供配置文件自动合并的 RESTful API
 */
@RestController
@RequestMapping("/api/config/merge")
@Tag(name = "配置合并管理", description = "配置文件自动合并相关接口")
public class AutoMergeController {

    private static final Logger logger = LoggerFactory.getLogger(AutoMergeController.class);

    private final AutoMergeService autoMergeService;

    @Autowired
    public AutoMergeController(AutoMergeService autoMergeService) {
        this.autoMergeService = autoMergeService;
    }

    /**
     * 扫描版本配置文件
     * @return 版本文件映射
     */
    @GetMapping("/scan")
    @Operation(summary = "扫描版本配置文件", description = "扫描 config 目录下的所有版本配置文件")
    public ResponseEntity<Map<String, Object>> scanVersionFiles() {
        logger.info("接收到扫描版本配置文件请求");
        
        try {
            Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "扫描完成");
            response.put("versionFiles", versionFiles);
            response.put("totalCount", versionFiles.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("扫描版本配置文件时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "扫描失败: " + e.getMessage());
            response.put("versionFiles", new HashMap<>());
            response.put("totalCount", 0);
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取合并预览
     * @return 合并预览结果
     */
    @GetMapping("/preview")
    @Operation(summary = "获取合并预览", description = "预览配置文件合并后的结果，不执行实际合并操作")
    public ResponseEntity<Map<String, Object>> getMergePreview() {
        logger.info("接收到获取合并预览请求");
        
        try {
            Map<String, Object> preview = autoMergeService.getMergePreview();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "预览生成成功");
            response.putAll(preview);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("生成合并预览时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "预览生成失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 执行自动合并
     * @return 合并结果
     */
    @PostMapping("/execute")
    @Operation(summary = "执行自动合并", description = "合并 config 目录下的多版本配置文件，并重置版本从1开始")
    public ResponseEntity<Map<String, Object>> performAutoMerge() {
        logger.info("接收到执行自动合并请求");
        
        try {
            AutoMergeService.MergeResult result = autoMergeService.performAutoMerge();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("mergedFilesCount", result.getMergedFilesCount());
            response.put("newVersionCount", result.getNewVersionCount());
            response.put("mergedFiles", result.getMergedFiles());
            response.put("errors", result.getErrors());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("执行自动合并时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "合并失败: " + e.getMessage());
            response.put("mergedFilesCount", 0);
            response.put("newVersionCount", 0);
            response.put("mergedFiles", new java.util.ArrayList<>());
            response.put("errors", java.util.Arrays.asList(e.getMessage()));
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 备份配置文件
     * @return 备份结果
     */
    @PostMapping("/backup")
    @Operation(summary = "备份配置文件", description = "备份 config 目录下的所有版本配置文件")
    public ResponseEntity<Map<String, Object>> backupConfigFiles() {
        logger.info("接收到备份配置文件请求");
        
        try {
            AutoMergeService.MergeResult result = autoMergeService.backupConfigFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("backedUpFilesCount", result.getMergedFilesCount());
            response.put("backedUpFiles", result.getMergedFiles());
            response.put("errors", result.getErrors());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("备份配置文件时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "备份失败: " + e.getMessage());
            response.put("backedUpFilesCount", 0);
            response.put("backedUpFiles", new java.util.ArrayList<>());
            response.put("errors", java.util.Arrays.asList(e.getMessage()));
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 清理配置文件
     * @param deleteOriginals 是否删除原始文件
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理配置文件", description = "清理 config 目录下的配置文件")
    public ResponseEntity<Map<String, Object>> cleanupConfigFiles(
            @Parameter(description = "是否删除原始配置文件", example = "false")
            @RequestParam(defaultValue = "false") boolean deleteOriginals) {
        
        logger.info("接收到清理配置文件请求，删除原始文件: {}", deleteOriginals);
        
        try {
            AutoMergeService.MergeResult result = autoMergeService.cleanupConfigFiles(deleteOriginals);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("cleanedFilesCount", result.getMergedFilesCount());
            response.put("cleanedFiles", result.getMergedFiles());
            response.put("errors", result.getErrors());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("清理配置文件时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清理失败: " + e.getMessage());
            response.put("cleanedFilesCount", 0);
            response.put("cleanedFiles", new java.util.ArrayList<>());
            response.put("errors", java.util.Arrays.asList(e.getMessage()));
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取合并服务状态
     * @return 服务状态信息
     */
    @GetMapping("/status")
    @Operation(summary = "获取合并服务状态", description = "获取自动合并服务的当前状态信息")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        logger.info("接收到获取合并服务状态请求");
        
        try {
            Map<Integer, String> versionFiles = autoMergeService.scanVersionFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "状态获取成功");
            response.put("availableVersionFiles", versionFiles.size());
            response.put("versionFiles", versionFiles);
            response.put("configDirectory", "config");
            response.put("serviceReady", true);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取合并服务状态时发生错误", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "状态获取失败: " + e.getMessage());
            response.put("availableVersionFiles", 0);
            response.put("serviceReady", false);
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}