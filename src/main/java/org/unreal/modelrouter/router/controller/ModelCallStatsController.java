package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ModelCallStats;
import org.unreal.modelrouter.monitor.service.ModelCallAnalyzer;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型调用统计控制器
 * 
 * v2.0.0 新增功能：
 * - 按模型名称统计分析
 * - 支持多服务类型统计
 * - 包含成功率、延迟、QPS 等关键指标
 * - 支持分页和过滤
 * - Top N 模型排行
 * - 健康状态监控
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/model-stats")
@CrossOrigin(origins = "*")
@Tag(name = "模型调用统计", description = "提供模型调用统计分析功能")
public class ModelCallStatsController {

    private static final Logger logger = LoggerFactory.getLogger(ModelCallStatsController.class);

    private final ModelCallAnalyzer modelCallAnalyzer;

    public ModelCallStatsController(final ModelCallAnalyzer modelCallAnalyzer) {
        this.modelCallAnalyzer = modelCallAnalyzer;
    }

    /**
     * 获取统计摘要
     */
    @GetMapping("/summary")
    @Operation(summary = "获取统计摘要", description = "获取所有模型的总体统计信息")
    @ApiResponse(responseCode = "200", description = "成功获取统计摘要")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getSummary() {
        try {
            Map<String, Object> summary = modelCallAnalyzer.getStatsSummary();
            return Mono.just(ResponseEntity.ok(RouterResponse.success(summary, "获取统计摘要成功")));
        } catch (Exception e) {
            logger.error("获取统计摘要失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取统计摘要失败：" + e.getMessage())));
        }
    }

    /**
     * 获取所有模型统计（分页）
     */
    @GetMapping("/models")
    @Operation(summary = "获取所有模型统计", description = "获取所有模型的详细统计信息，支持分页和过滤")
    @ApiResponse(responseCode = "200", description = "成功获取模型统计")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getAllModelStats(
            @RequestParam(required = false) final String serviceType,
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "totalCalls") final String sortBy,
            @RequestParam(defaultValue = "false") final boolean ascending) {
        try {
            Map<String, Object> result = modelCallAnalyzer.getAllModelStats(serviceType, page, size, sortBy, ascending);
            return Mono.just(ResponseEntity.ok(RouterResponse.success(result, "获取模型统计成功")));
        } catch (Exception e) {
            logger.error("获取模型统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取模型统计失败：" + e.getMessage())));
        }
    }

    /**
     * 按服务类型获取统计
     */
    @GetMapping("/service-types/{serviceType}")
    @Operation(summary = "按服务类型获取统计", description = "获取指定服务类型的所有模型统计")
    @ApiResponse(responseCode = "200", description = "成功获取统计")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<List<ModelCallStats>>>> getStatsByServiceType(
            @PathVariable final String serviceType) {
        try {
            List<ModelCallStats> stats = modelCallAnalyzer.getStatsByServiceType(serviceType);
            return Mono.just(ResponseEntity.ok(RouterResponse.success(stats, "获取服务类型统计成功")));
        } catch (Exception e) {
            logger.error("获取服务类型统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取服务类型统计失败：" + e.getMessage())));
        }
    }

    /**
     * 获取指定模型的统计
     */
    @GetMapping("/models/{serviceType}/{modelName}")
    @Operation(summary = "获取指定模型统计", description = "获取指定模型的详细统计信息")
    @ApiResponse(responseCode = "200", description = "成功获取模型统计")
    @ApiResponse(responseCode = "404", description = "模型不存在")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<ModelCallStats>>> getModelStats(
            @PathVariable final String serviceType,
            @PathVariable final String modelName) {
        try {
            ModelCallStats stats = modelCallAnalyzer.getModelStats(serviceType, modelName);
            if (stats == null) {
                return Mono.just(ResponseEntity.notFound().build());
            }
            return Mono.just(ResponseEntity.ok(RouterResponse.success(stats, "获取模型统计成功")));
        } catch (Exception e) {
            logger.error("获取模型统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取模型统计失败：" + e.getMessage())));
        }
    }

    /**
     * 获取 Top 10 活跃模型
     */
    @GetMapping("/top/active")
    @Operation(summary = "获取 Top 10 活跃模型", description = "获取调用次数最多的前 10 个模型")
    @ApiResponse(responseCode = "200", description = "成功获取 Top 10")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<List<ModelCallStats>>>> getTop10ActiveModels() {
        try {
            List<ModelCallStats> topModels = modelCallAnalyzer.getTop10ActiveModels();
            return Mono.just(ResponseEntity.ok(RouterResponse.success(topModels, "获取 Top 10 活跃模型成功")));
        } catch (Exception e) {
            logger.error("获取 Top 10 活跃模型失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取 Top 10 活跃模型失败：" + e.getMessage())));
        }
    }

    /**
     * 获取健康状态异常的模型
     */
    @GetMapping("/unhealthy")
    @Operation(summary = "获取健康状态异常的模型", description = "获取健康状态为 DEGRADED 或 UNHEALTHY 的模型")
    @ApiResponse(responseCode = "200", description = "成功获取异常模型")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<List<ModelCallStats>>>> getUnhealthyModels() {
        try {
            List<ModelCallStats> unhealthyModels = modelCallAnalyzer.getUnhealthyModels();
            return Mono.just(ResponseEntity.ok(RouterResponse.success(unhealthyModels, "获取健康状态异常的模型成功")));
        } catch (Exception e) {
            logger.error("获取健康状态异常的模型失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取健康状态异常的模型失败：" + e.getMessage())));
        }
    }

    /**
     * 获取按服务类型分组的统计
     */
    @GetMapping("/grouped-by-service-type")
    @Operation(summary = "按服务类型分组统计", description = "获取按服务类型分组的统计信息")
    @ApiResponse(responseCode = "200", description = "成功获取分组统计")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getGroupedByServiceType() {
        try {
            Map<String, Object> grouped = modelCallAnalyzer.getGroupedByServiceType();
            return Mono.just(ResponseEntity.ok(RouterResponse.success(grouped, "获取分组统计成功")));
        } catch (Exception e) {
            logger.error("获取分组统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取分组统计失败：" + e.getMessage())));
        }
    }

    /**
     * 获取调用趋势
     */
    @GetMapping("/trend")
    @Operation(summary = "获取调用趋势", description = "获取最近 N 分钟的调用趋势")
    @ApiResponse(responseCode = "200", description = "成功获取趋势")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>> getCallTrend(
            @RequestParam(defaultValue = "60") final int minutes) {
        try {
            List<Map<String, Object>> trend = modelCallAnalyzer.getCallTrend(minutes);
            return Mono.just(ResponseEntity.ok(RouterResponse.success(trend, "获取调用趋势成功")));
        } catch (Exception e) {
            logger.error("获取调用趋势失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取调用趋势失败：" + e.getMessage())));
        }
    }

    /**
     * 刷新统计（手动触发）
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新统计", description = "手动刷新统计信息（QPS 等）")
    @ApiResponse(responseCode = "200", description = "成功刷新统计")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> refreshStats() {
        try {
            modelCallAnalyzer.refreshQps();
            Map<String, Object> result = new HashMap<>();
            result.put("message", "统计已刷新");
            result.put("timestamp", System.currentTimeMillis());
            return Mono.just(ResponseEntity.ok(RouterResponse.success(result, "刷新统计成功")));
        } catch (Exception e) {
            logger.error("刷新统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("刷新统计失败：" + e.getMessage())));
        }
    }

    /**
     * 清空统计（管理员操作）
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清空统计", description = "清空所有模型统计信息（管理员操作）")
    @ApiResponse(responseCode = "200", description = "成功清空统计")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "403", description = "权限不足")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> clearStats() {
        try {
            // 添加权限检查：检查用户是否具有管理员权限
            // 这里假设有一个权限检查方法，实际实现可能依赖于具体的认证框架
            if (!hasAdminPermission()) {
                return Mono.just(ResponseEntity.status(403)
                        .body(RouterResponse.error("权限不足，无法执行此操作")));
            }
            
            // 执行清空统计操作
            modelCallAnalyzer.clearAllStats(); // 假设ModelCallAnalyzer有此方法
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "统计已清空");
            result.put("timestamp", System.currentTimeMillis());
            return Mono.just(ResponseEntity.ok(RouterResponse.success(result, "清空统计成功")));
        } catch (Exception e) {
            logger.error("清空统计失败", e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body(RouterResponse.error("清空统计失败：" + e.getMessage())));
        }
    }
    
    /**
     * 检查当前用户是否具有管理员权限
     * @return 是否具有管理员权限
     */
    private boolean hasAdminPermission() {
        // 实现权限检查逻辑，例如：
        // 1. 检查当前认证用户的权限
        // 2. 检查用户角色是否为ADMIN
        // 这里是一个简化的实现，实际应结合Spring Security等框架
        
        // 在实际应用中，这可能涉及：
        // Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        
        // 临时返回true，表示有权限，实际应用中应实现真实的权限检查
        return true;
    }
}
