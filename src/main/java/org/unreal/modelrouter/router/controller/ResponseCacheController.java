package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v2.9.10: 响应缓存管理控制器.
 *
 * <p>提供手动失效 API，支持按 serviceType / model / 全部粒度清空缓存。
 * 仿 {@link RuleConfigController} 同步 controller 模式（无方法级 @PreAuthorize）。
 *
 * @author JAiRouter Team
 * @since 2.9.10
 */
@RestController
@RequestMapping("/api/config/cache")
@Tag(name = "响应缓存管理", description = "提供响应缓存的手动失效接口")
public class ResponseCacheController {

    private static final Logger logger = LoggerFactory.getLogger(ResponseCacheController.class);

    private final ResponseCacheService responseCacheService;

    public ResponseCacheController(final ResponseCacheService responseCacheService) {
        this.responseCacheService = responseCacheService;
    }

    /**
     * 查询响应缓存管理状态.
     *
     * <p>返回当前缓存配置与运行时状态（条目数、命中统计）。
     *
     * @return 缓存状态信息
     */
    @GetMapping("/response")
    @Operation(summary = "查询响应缓存状态",
            description = "返回缓存配置（enabled/ttl/maxSize/skipStreaming/onlyDeterministic）"
                    + "与运行时状态（size/hits/misses/hitRatio）")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getCacheStatus() {
        return ResponseEntity.ok(RouterResponse.success(responseCacheService.snapshot()));
    }

    /**
     * 失效响应缓存.
     *
     * <p>参数组合语义：
     * <ul>
     *   <li>无参数：清空全部缓存</li>
     *   <li>仅 serviceType：按服务类型失效</li>
     *   <li>serviceType + model：按服务类型与模型精确失效</li>
     * </ul>
     *
     * @param serviceType 服务类型（可选）
     * @param model 模型名称（可选）
     * @return 失效结果
     */
    @DeleteMapping("/response")
    @Operation(summary = "失效响应缓存", description = "按 serviceType / model / 全部清空响应缓存")
    public ResponseEntity<RouterResponse<Map<String, Object>>> invalidateCache(
            @RequestParam(required = false) final String serviceType,
            @RequestParam(required = false) final String model) {
        boolean executed;
        ServiceType resolvedType = null;
        if (serviceType != null && !serviceType.isBlank()) {
            resolvedType = Arrays.stream(ServiceType.values())
                    .filter(st -> st.name().equalsIgnoreCase(serviceType.trim()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid serviceType: " + serviceType
                            + " (valid: " + Arrays.toString(ServiceType.values()) + ")"));
        }
        String resolvedModel = (model != null && !model.isBlank()) ? model.trim() : null;

        if (resolvedType == null && resolvedModel == null) {
            executed = responseCacheService.invalidateAll();
            logger.info("Response cache: invalidateAll executed={}", executed);
        } else {
            executed = responseCacheService.invalidate(resolvedType, resolvedModel);
            logger.info("Response cache: invalidate serviceType={}, model={}, executed={}",
                    resolvedType, resolvedModel, executed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", executed);
        result.put("serviceType", resolvedType != null ? resolvedType.name() : null);
        result.put("model", resolvedModel);
        String message = executed ? "缓存失效操作已执行" : "缓存未启用，操作未执行";
        return ResponseEntity.ok(RouterResponse.success(result, message));
    }

    /**
     * 运行时更新响应缓存配置（部分更新）.
     *
     * <p>仅修改请求体中非 null 的字段，null 字段保持不变。
     * ttlSeconds 校验范围 [1, 604800]（7 天）。
     * 全 null 请求视为非法。
     *
     * @param request 配置更新请求（字段均可空）
     * @return 更新成功返回最新状态；校验失败返回 400
     */
    @PutMapping("/response/config")
    @Operation(summary = "运行时更新响应缓存配置",
            description = "部分更新 enabled/skipStreaming/onlyDeterministic/ttlSeconds，null 字段不修改")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateRuntimeConfig(
            @RequestBody final RuntimeConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", "INVALID_REQUEST"));
        }
        try {
            Map<String, Object> snapshot = responseCacheService.updateRuntimeConfig(
                    request.enabled, request.skipStreaming,
                    request.onlyDeterministic, request.ttlSeconds);
            logger.info("Response cache runtime config updated: {}", snapshot);
            return ResponseEntity.ok(RouterResponse.success(snapshot, "缓存配置已更新"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(e.getMessage(), "INVALID_REQUEST"));
        }
    }

    /**
     * 运行时配置更新请求体（字段均为包装类型以支持部分更新，null = 不修改）.
     */
    public static class RuntimeConfigUpdateRequest {
        /** 是否启用响应缓存 */
        public Boolean enabled;
        /** 是否跳过流式请求 */
        public Boolean skipStreaming;
        /** 是否仅缓存确定性请求 */
        public Boolean onlyDeterministic;
        /** 缓存 TTL（秒），范围 [1, 604800] */
        public Long ttlSeconds;
    }
}
