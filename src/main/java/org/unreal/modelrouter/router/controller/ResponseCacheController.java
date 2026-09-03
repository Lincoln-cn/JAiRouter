package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
}
