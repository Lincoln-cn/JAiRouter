package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.tracing.encryption.TracingEncryptionService;
import org.unreal.modelrouter.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.tracing.security.TracingSecurityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 追踪安全管理控制器
 * 
 * 提供追踪安全功能的管理接口，包括：
 * - 脱敏规则管理
 * - 访问权限管理
 * - 加密密钥管理
 * - 安全审计查询
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config/tracing/security")
@Tag(name = "追踪安全管理", description = "管理追踪安全功能")
@PreAuthorize("hasRole('ADMIN')")
public class TracingSecurityController {
    
    private final TracingSanitizationService tracingSanitizationService;
    private final TracingSecurityManager tracingSecurityManager;
    private final TracingEncryptionService tracingEncryptionService;
    
    // ========================================
    // 脱敏规则管理
    // ========================================
    
    /**
     * 获取追踪敏感字段列表
     */
    @GetMapping("/sanitization/sensitive-fields")
    @Operation(summary = "获取追踪敏感字段", description = "获取当前配置的追踪敏感字段列表")
    @ApiResponse(responseCode = "200", description = "成功获取敏感字段列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    public ResponseEntity<RouterResponse<Set<String>>> getTracingSensitiveFields() {
        try {
            Set<String> sensitiveFields = tracingSanitizationService.getTracingSensitiveFields();
            log.info("成功获取追踪敏感字段列表，字段数量: {}", sensitiveFields.size());
            return ResponseEntity.ok(RouterResponse.success(sensitiveFields, "获取追踪敏感字段成功"));
        } catch (Exception e) {
            log.error("获取追踪敏感字段失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取追踪敏感字段失败: " + e.getMessage()));
        }
    }
    
    /**
     * 添加追踪敏感字段
     */
    @PostMapping("/sanitization/sensitive-fields")
    @Operation(summary = "添加追踪敏感字段", description = "添加新的追踪敏感字段")
    @ApiResponse(responseCode = "200", description = "成功添加敏感字段")
    public ResponseEntity<RouterResponse<Void>> addTracingSensitiveField(@RequestParam String field) {
        try {
            tracingSanitizationService.addTracingSensitiveField(field);
            log.info("成功添加追踪敏感字段: {}", field);
            return ResponseEntity.ok(RouterResponse.success(null, "添加追踪敏感字段成功"));
        } catch (Exception e) {
            log.error("添加追踪敏感字段失败: {}", field, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("添加追踪敏感字段失败: " + e.getMessage()));
        }
    }
    
    /**
     * 移除追踪敏感字段
     */
    @DeleteMapping("/sanitization/sensitive-fields")
    @Operation(summary = "移除追踪敏感字段", description = "移除指定的追踪敏感字段")
    @ApiResponse(responseCode = "200", description = "成功移除敏感字段")
    public ResponseEntity<RouterResponse<Void>> removeTracingSensitiveField(@RequestParam String field) {
        try {
            tracingSanitizationService.removeTracingSensitiveField(field);
            log.info("成功移除追踪敏感字段: {}", field);
            return ResponseEntity.ok(RouterResponse.success(null, "移除追踪敏感字段成功"));
        } catch (Exception e) {
            log.error("移除追踪敏感字段失败: {}", field, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("移除追踪敏感字段失败: " + e.getMessage()));
        }
    }
    
    // ========================================
    // 访问权限管理
    // ========================================
    
    /**
     * 获取用户追踪访问历史
     */
    @GetMapping("/access/history/{username}")
    @Operation(summary = "获取用户追踪访问历史", description = "获取指定用户的追踪访问历史记录")
    @ApiResponse(responseCode = "200", description = "成功获取访问历史")
    public ResponseEntity<RouterResponse<List<TracingSecurityManager.TraceAccessRecord>>> getUserTraceAccessHistory(
            @PathVariable String username) {
        try {
            List<TracingSecurityManager.TraceAccessRecord> history = 
                    tracingSecurityManager.getUserTraceAccessHistory(username);
            log.info("成功获取用户追踪访问历史: {}, 记录数量: {}", username, history.size());
            return ResponseEntity.ok(RouterResponse.success(history, "获取追踪访问历史成功"));
        } catch (Exception e) {
            log.error("获取用户追踪访问历史失败: {}", username, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取追踪访问历史失败: " + e.getMessage()));
        }
    }
    
    /**
     * 清理用户权限缓存
     */
    @DeleteMapping("/access/cache/{username}")
    @Operation(summary = "清理用户权限缓存", description = "清理指定用户的权限缓存")
    @ApiResponse(responseCode = "200", description = "成功清理权限缓存")
    public ResponseEntity<RouterResponse<Void>> clearUserPermissionCache(@PathVariable String username) {
        try {
            tracingSecurityManager.clearUserPermissionCache(username);
            log.info("成功清理用户权限缓存: {}", username);
            return ResponseEntity.ok(RouterResponse.success(null, "清理用户权限缓存成功"));
        } catch (Exception e) {
            log.error("清理用户权限缓存失败: {}", username, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("清理用户权限缓存失败: " + e.getMessage()));
        }
    }
    
    /**
     * 清理所有权限缓存
     */
    @DeleteMapping("/access/cache")
    @Operation(summary = "清理所有权限缓存", description = "清理所有用户的权限缓存")
    @ApiResponse(responseCode = "200", description = "成功清理所有权限缓存")
    public ResponseEntity<RouterResponse<Void>> clearAllPermissionCache() {
        try {
            tracingSecurityManager.clearAllPermissionCache();
            log.info("成功清理所有用户权限缓存");
            return ResponseEntity.ok(RouterResponse.success(null, "清理所有权限缓存成功"));
        } catch (Exception e) {
            log.error("清理所有权限缓存失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("清理所有权限缓存失败: " + e.getMessage()));
        }
    }
    
    // ========================================
    // 加密密钥管理
    // ========================================
    
    /**
     * 轮换加密密钥
     */
    @PostMapping("/encryption/rotate-key/{traceId}")
    @Operation(summary = "轮换加密密钥", description = "轮换指定追踪的加密密钥")
    @ApiResponse(responseCode = "200", description = "成功轮换密钥")
    public ResponseEntity<RouterResponse<Boolean>> rotateEncryptionKey(@PathVariable String traceId) {
        try {
            Boolean result = tracingEncryptionService.rotateEncryptionKey(traceId).block();
            if (Boolean.TRUE.equals(result)) {
                log.info("成功轮换加密密钥: {}", traceId);
                return ResponseEntity.ok(RouterResponse.success(result, "轮换加密密钥成功"));
            } else {
                log.warn("轮换加密密钥失败: {}", traceId);
                return ResponseEntity.ok(RouterResponse.error("轮换加密密钥失败"));
            }
        } catch (Exception e) {
            log.error("轮换加密密钥异常: {}", traceId, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("轮换加密密钥异常: " + e.getMessage()));
        }
    }
    
    /**
     * 清理过期数据
     */
    @PostMapping("/encryption/cleanup")
    @Operation(summary = "清理过期数据", description = "清理过期的加密追踪数据")
    @ApiResponse(responseCode = "200", description = "成功清理过期数据")
    public ResponseEntity<RouterResponse<Integer>> cleanupExpiredData() {
        try {
            Integer cleanupCount = tracingEncryptionService.cleanupExpiredData().block();
            log.info("成功清理过期数据，清理数量: {}", cleanupCount);
            return ResponseEntity.ok(RouterResponse.success(cleanupCount, "清理过期数据成功"));
        } catch (Exception e) {
            log.error("清理过期数据失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("清理过期数据失败: " + e.getMessage()));
        }
    }
    
    /**
     * 安全清理追踪数据
     */
    @DeleteMapping("/encryption/data/{traceId}")
    @Operation(summary = "安全清理追踪数据", description = "安全清理指定追踪的所有数据")
    @ApiResponse(responseCode = "200", description = "成功清理追踪数据")
    public ResponseEntity<RouterResponse<Void>> secureCleanupTraceData(@PathVariable String traceId) {
        try {
            tracingEncryptionService.secureCleanupTraceData(traceId).block();
            log.info("成功安全清理追踪数据: {}", traceId);
            return ResponseEntity.ok(RouterResponse.success(null, "安全清理追踪数据成功"));
        } catch (Exception e) {
            log.error("安全清理追踪数据失败: {}", traceId, e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("安全清理追踪数据失败: " + e.getMessage()));
        }
    }
    
    // ========================================
    // 安全状态概览
    // ========================================
    
    /**
     * 获取追踪安全状态概览
     */
    @GetMapping("/overview")
    @Operation(summary = "获取追踪安全状态概览", description = "获取追踪安全功能的整体状态概览")
    @ApiResponse(responseCode = "200", description = "成功获取安全状态概览")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getSecurityOverview() {
        try {
            Map<String, Object> overview = new HashMap<>();
            
            // 脱敏配置状态
            Map<String, Object> sanitizationStatus = new HashMap<>();
            sanitizationStatus.put("sensitiveFieldCount", tracingSanitizationService.getTracingSensitiveFields().size());
            sanitizationStatus.put("enabled", true);
            overview.put("sanitization", sanitizationStatus);
            
            // 访问控制状态
            Map<String, Object> accessControlStatus = new HashMap<>();
            accessControlStatus.put("enabled", true);
            accessControlStatus.put("restrictTraceAccess", true);
            overview.put("accessControl", accessControlStatus);
            
            // 加密状态
            Map<String, Object> encryptionStatus = new HashMap<>();
            encryptionStatus.put("enabled", true);
            encryptionStatus.put("algorithm", "AES");
            encryptionStatus.put("keySize", 256);
            overview.put("encryption", encryptionStatus);
            
            // 审计状态
            Map<String, Object> auditStatus = new HashMap<>();
            auditStatus.put("enabled", true);
            auditStatus.put("auditLevel", "INFO");
            overview.put("audit", auditStatus);
            
            log.info("成功获取追踪安全状态概览");
            return ResponseEntity.ok(RouterResponse.success(overview, "获取追踪安全状态概览成功"));
        } catch (Exception e) {
            log.error("获取追踪安全状态概览失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取追踪安全状态概览失败: " + e.getMessage()));
        }
    }
}