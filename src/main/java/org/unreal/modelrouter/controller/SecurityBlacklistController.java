package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.AddBlacklistRequest;
import org.unreal.modelrouter.dto.BlacklistEntryDTO;
import org.unreal.modelrouter.dto.BlacklistStatsDTO;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.jpa.entity.SecurityBlacklistEntity.BlacklistType;
import org.unreal.modelrouter.security.service.SecurityBlacklistService;

import java.security.Principal;
import java.util.List;

/**
 * 统一安全黑名单管理API
 */
@Slf4j
@RestController
@RequestMapping("/api/security/blacklist")
@RequiredArgsConstructor
@Tag(name = "黑名单管理", description = "统一安全黑名单管理接口，支持Token/IP/Device多种类型")
public class SecurityBlacklistController {

    private final SecurityBlacklistService blacklistService;

    /**
     * 获取黑名单列表（分页）
     */
    @GetMapping("/list")
    @Operation(summary = "获取黑名单列表", description = "分页查询黑名单条目，支持按类型和状态过滤")
    public ResponseEntity<RouterResponse<Page<BlacklistEntryDTO>>> getBlacklistPage(
            @Parameter(description = "黑名单类型: TOKEN, IP, DEVICE") @RequestParam(required = false) String type,
            @Parameter(description = "状态: ACTIVE, EXPIRED, REMOVED") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {

        log.info("获取黑名单列表: type={}, status={}, page={}, size={}", type, status, page, size);

        BlacklistType blacklistType = null;
        if (type != null && !type.isEmpty()) {
            try {
                blacklistType = BlacklistType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(RouterResponse.error("无效的黑名单类型: " + type, "INVALID_TYPE"));
            }
        }

        Page<BlacklistEntryDTO> result = blacklistService.getBlacklistPage(blacklistType, status, page, size);
        return ResponseEntity.ok(RouterResponse.success(result, "黑名单列表获取成功"));
    }

    /**
     * 获取黑名单统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取黑名单统计", description = "获取各类黑名单的数量统计")
    public ResponseEntity<RouterResponse<BlacklistStatsDTO>> getBlacklistStats() {
        log.info("获取黑名单统计信息");

        BlacklistStatsDTO stats = blacklistService.getBlacklistStats();
        return ResponseEntity.ok(RouterResponse.success(stats, "黑名单统计获取成功"));
    }

    /**
     * 获取黑名单条目详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取黑名单详情", description = "根据ID获取黑名单条目详情")
    public ResponseEntity<RouterResponse<BlacklistEntryDTO>> getBlacklistEntry(
            @Parameter(description = "黑名单条目ID") @PathVariable Long id) {

        log.info("获取黑名单详情: id={}", id);

        BlacklistEntryDTO entry = blacklistService.getBlacklistEntry(id);
        if (entry == null) {
            return ResponseEntity.ok(RouterResponse.error("黑名单条目不存在", "NOT_FOUND"));
        }
        return ResponseEntity.ok(RouterResponse.success(entry, "黑名单详情获取成功"));
    }

    /**
     * 添加到黑名单
     */
    @PostMapping("/add")
    @Operation(summary = "添加黑名单", description = "手动添加条目到黑名单，支持Token/IP/Device类型")
    public ResponseEntity<RouterResponse<BlacklistEntryDTO>> addToBlacklist(
            @Valid @RequestBody AddBlacklistRequest request,
            Principal principal) {

        String addedBy = principal != null ? principal.getName() : "system";
        log.info("添加黑名单: type={}, value={}, addedBy={}",
                request.getBlacklistType(), maskValue(request.getBlacklistType(), request.getTargetValue()), addedBy);

        try {
            BlacklistEntryDTO entry = blacklistService.addToBlacklist(request, addedBy);
            return ResponseEntity.ok(RouterResponse.success(entry, "黑名单添加成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(RouterResponse.error("无效的参数: " + e.getMessage(), "INVALID_PARAM"));
        } catch (Exception e) {
            log.error("添加黑名单失败", e);
            return ResponseEntity.ok(RouterResponse.error("添加失败: " + e.getMessage(), "ADD_FAILED"));
        }
    }

    /**
     * 批量添加黑名单
     */
    @PostMapping("/batch-add")
    @Operation(summary = "批量添加黑名单", description = "批量添加多个条目到黑名单")
    public ResponseEntity<RouterResponse<Integer>> batchAddToBlacklist(
            @RequestBody List<AddBlacklistRequest> requests,
            Principal principal) {

        String addedBy = principal != null ? principal.getName() : "system";
        log.info("批量添加黑名单: count={}, addedBy={}", requests.size(), addedBy);

        int successCount = blacklistService.batchAddToBlacklist(requests, addedBy);
        return ResponseEntity.ok(RouterResponse.success(successCount, "批量添加完成，成功数量: " + successCount));
    }

    /**
     * 从黑名单移除
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "移除黑名单", description = "根据ID从黑名单移除条目")
    public ResponseEntity<RouterResponse<Boolean>> removeFromBlacklist(
            @Parameter(description = "黑名单条目ID") @PathVariable Long id) {

        log.info("移除黑名单: id={}", id);

        boolean success = blacklistService.removeFromBlacklist(id);
        if (success) {
            return ResponseEntity.ok(RouterResponse.success(true, "黑名单移除成功"));
        } else {
            return ResponseEntity.ok(RouterResponse.error("黑名单条目不存在", "NOT_FOUND"));
        }
    }

    /**
     * 检查是否在黑名单中
     */
    @GetMapping("/check")
    @Operation(summary = "检查黑名单", description = "检查指定目标是否在黑名单中")
    public ResponseEntity<RouterResponse<Boolean>> checkBlacklist(
            @Parameter(description = "黑名单类型") @RequestParam String type,
            @Parameter(description = "目标值") @RequestParam String value) {

        log.info("检查黑名单: type={}, value={}", type, maskValue(type, value));

        try {
            BlacklistType blacklistType = BlacklistType.valueOf(type.toUpperCase());
            boolean inBlacklist = blacklistService.isInBlacklist(blacklistType, value);
            return ResponseEntity.ok(RouterResponse.success(inBlacklist,
                    inBlacklist ? "目标在黑名单中" : "目标不在黑名单中"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(RouterResponse.error("无效的黑名单类型: " + type, "INVALID_TYPE"));
        }
    }

    /**
     * 手动触发清理过期条目
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期黑名单", description = "手动触发清理过期的黑名单条目")
    public ResponseEntity<RouterResponse<Integer>> cleanupExpired() {
        log.info("手动触发清理过期黑名单");

        int cleaned = blacklistService.cleanupExpiredEntries();
        return ResponseEntity.ok(RouterResponse.success(cleaned, "清理完成，数量: " + cleaned));
    }

    /**
     * 脱敏处理目标值
     */
    private String maskValue(String type, String value) {
        if (value == null || value.length() < 8) {
            return value == null ? "null" : value;
        }
        return value.substring(0, 8) + "...";
    }
}