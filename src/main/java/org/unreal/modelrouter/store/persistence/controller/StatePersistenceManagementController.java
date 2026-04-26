package org.unreal.modelrouter.store.persistence.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.persistence.CompositeStatePersistenceServiceImpl;
import org.unreal.modelrouter.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.store.persistence.adapter.LoadBalancerStatePersistenceAdapter;
import org.unreal.modelrouter.store.persistence.adapter.RateLimiterStatePersistenceAdapter;
import org.unreal.modelrouter.store.persistence.recovery.StateRecoveryService;
import org.unreal.modelrouter.store.persistence.StatePersistenceService.StateType;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 状态持久化管理 REST API
 *
 * v2.4.4: 提供手动恢复和状态监控 API
 * v2.4.5: 新增状态详情查询和手动同步 API，限流器恢复 API
 *
 * API 端点:
 * - POST /api/state-persistence/recovery/all - 手动触发全部恢复
 * - POST /api/state-persistence/recovery/circuit-breaker/{instanceId} - 单个熔断器恢复
 * - POST /api/state-persistence/recovery/load-balancer/{serviceType} - 单个负载均衡器恢复
 * - POST /api/state-persistence/recovery/rate-limiter/{limiterId} - 单个限流器恢复 (v2.4.5)
 * - GET /api/state-persistence/status - 获取持久化状态
 * - GET /api/state-persistence/details - 获取状态详情列表 (v2.4.5)
 * - GET /api/state-persistence/tiers - 获取各层健康状态
 * - POST /api/state-persistence/tiers/switch/{tierName} - 手动切换存储层
 * - POST /api/state-persistence/tiers/refresh - 刷新健康状态
 * - POST /api/state-persistence/sync - 手动同步状态 (v2.4.5)
 *
 * @author JAiRouter Team
 * @since 2.4.4
 */
@RestController
@RequestMapping("/api/state-persistence")
public class StatePersistenceManagementController {

    private static final Logger logger = LoggerFactory.getLogger(StatePersistenceManagementController.class);

    @Autowired(required = false)
    private StateRecoveryService recoveryService;

    @Autowired
    private CompositeStatePersistenceServiceImpl compositePersistenceService;

    @Autowired
    private CircuitBreakerStatePersistenceAdapter cbPersistenceAdapter;

    @Autowired
    private LoadBalancerStatePersistenceAdapter lbPersistenceAdapter;

    @Autowired(required = false)
    private RateLimiterStatePersistenceAdapter rlPersistenceAdapter;

    /**
     * 手动触发全部状态恢复
     */
    @PostMapping("/recovery/all")
    public Mono<ResponseEntity<Map<String, Object>>> triggerFullRecovery() {
        logger.info("Manual full state recovery triggered via API");

        if (recoveryService == null) {
            return Mono.just(ResponseEntity.ok(createSuccessResponse(Map.of("message", "Recovery service is disabled"))));
        }

        return recoveryService.triggerManualRecovery()
                .map(stats -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("message", "State recovery completed");
                    data.put("durationMs", stats.durationMs);
                    data.put("circuitBreaker", Map.of(
                            "found", stats.cbStatesFound,
                            "recovered", stats.cbStatesRecovered,
                            "failed", stats.cbStatesFailed
                    ));
                    data.put("loadBalancer", Map.of(
                            "found", stats.lbStatesFound,
                            "recovered", stats.lbStatesRecovered,
                            "failed", stats.lbStatesFailed
                    ));
                    data.put("totalRecovered", stats.getTotalRecovered());
                    data.put("totalFailed", stats.getTotalFailed());
                    return ResponseEntity.ok(createSuccessResponse(data));
                });
    }

    /**
     * 手动触发单个熔断器恢复
     */
    @PostMapping("/recovery/circuit-breaker/{instanceId}")
    public Mono<ResponseEntity<Map<String, Object>>> recoverSingleCircuitBreaker(
            @PathVariable String instanceId) {

        logger.info("Manual recovery for circuit breaker: {}", instanceId);

        if (recoveryService == null) {
            return Mono.just(ResponseEntity.ok(createSuccessResponse(Map.of(
                    "instanceId", instanceId, "recovered", false, "message", "Recovery service is disabled"
            ))));
        }

        return recoveryService.recoverSingleCircuitBreaker(instanceId)
                .map(result -> ResponseEntity.ok(createSuccessResponse(Map.of(
                        "instanceId", instanceId, "recovered", result
                ))));
    }

    /**
     * 手动触发单个负载均衡器恢复
     */
    @PostMapping("/recovery/load-balancer/{serviceType}")
    public Mono<ResponseEntity<Map<String, Object>>> recoverSingleLoadBalancer(
            @PathVariable String serviceType) {

        logger.info("Manual recovery for load balancer: {}", serviceType);

        if (recoveryService == null) {
            return Mono.just(ResponseEntity.ok(createSuccessResponse(Map.of(
                    "serviceType", serviceType, "recovered", false, "message", "Recovery service is disabled"
            ))));
        }

        ModelServiceRegistry.ServiceType type;
        try {
            type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid service type: " + serviceType)));
        }

        return recoveryService.recoverSingleLoadBalancer(type)
                .map(result -> ResponseEntity.ok(createSuccessResponse(Map.of(
                        "serviceType", serviceType, "recovered", result
                ))));
    }

    /**
     * 手动触发单个限流器恢复 (v2.4.5)
     */
    @PostMapping("/recovery/rate-limiter/{limiterId}")
    public Mono<ResponseEntity<Map<String, Object>>> recoverSingleRateLimiter(
            @PathVariable String limiterId) {

        logger.info("Manual recovery for rate limiter: {}", limiterId);

        if (rlPersistenceAdapter == null) {
            return Mono.just(ResponseEntity.ok(createSuccessResponse(Map.of(
                    "limiterId", limiterId, "recovered", false, "message", "Rate limiter persistence is disabled"
            ))));
        }

        return rlPersistenceAdapter.restoreRateLimiterState(limiterId)
                .map(result -> ResponseEntity.ok(createSuccessResponse(Map.of(
                        "limiterId", limiterId, "recovered", result
                ))));
    }

    /**
     * 获取持久化状态
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getPersistenceStatus() {
        return compositePersistenceService.isHealthy().map(healthy -> {
            Map<String, Object> data = new HashMap<>();
            data.put("currentTier", compositePersistenceService.getActiveTierName());
            data.put("tierHealth", compositePersistenceService.getAllTierStatus());
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("circuitBreakerCount", cbPersistenceAdapter.getPendingSyncCount());
            stats.put("loadBalancerCount", lbPersistenceAdapter.getPendingSyncCount());
            stats.put("rateLimiterCount", rlPersistenceAdapter != null ? 
                    (int) rlPersistenceAdapter.getStats().block().get("registeredCount") : 0);
            stats.put("pendingSync", cbPersistenceAdapter.getPendingSyncCount() + lbPersistenceAdapter.getPendingSyncCount());
            data.put("stats", stats);

            // 添加恢复统计
            if (recoveryService != null) {
                StateRecoveryService.RecoveryStatistics lastStats = recoveryService.getLastRecoveryStats();
                data.put("lastRecovery", Map.of(
                        "success", lastStats.success,
                        "durationMs", lastStats.durationMs,
                        "cbRecovered", lastStats.cbStatesRecovered,
                        "lbRecovered", lastStats.lbStatesRecovered
                ));
            }

            return ResponseEntity.ok(createSuccessResponse(data));
        });
    }

    /**
     * 获取状态详情列表 (v2.4.5)
     */
    @GetMapping("/details")
    public Mono<ResponseEntity<Map<String, Object>>> getStateDetails() {
        List<Map<String, Object>> details = new ArrayList<>();

        // 限流器状态（从注册列表获取）
        if (rlPersistenceAdapter != null) {
            for (String limiterId : rlPersistenceAdapter.getRegisteredLimiterIds()) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("instanceId", limiterId);
                detail.put("stateType", "RATE_LIMITER");
                detail.put("state", "registered");
                detail.put("lastModified", System.currentTimeMillis());
                details.add(detail);
            }
        }

        return Mono.just(ResponseEntity.ok(createSuccessResponse(details)));
    }

    /**
     * 获取各存储层健康状态
     */
    @GetMapping("/tiers")
    public Mono<ResponseEntity<Map<String, Object>>> getTierStatus() {
        Map<String, Boolean> tierStatus = compositePersistenceService.getAllTierStatus();

        Map<String, Object> redisHealth = new HashMap<>();
        redisHealth.put("healthy", tierStatus.getOrDefault("redis", false));
        redisHealth.put("message", tierStatus.getOrDefault("redis", false) ? "Available" : "Not configured");

        Map<String, Object> h2Health = new HashMap<>();
        h2Health.put("healthy", tierStatus.getOrDefault("h2", true));
        h2Health.put("message", "Available (default)");

        Map<String, Object> fileHealth = new HashMap<>();
        fileHealth.put("healthy", tierStatus.getOrDefault("file", true));
        fileHealth.put("message", "Available (fallback)");

        Map<String, Object> data = new HashMap<>();
        data.put("redis", redisHealth);
        data.put("h2", h2Health);
        data.put("file", fileHealth);

        return Mono.just(ResponseEntity.ok(createSuccessResponse(data)));
    }

    /**
     * 手动切换存储层
     */
    @PostMapping("/tiers/switch/{tierName}")
    public Mono<ResponseEntity<Map<String, Object>>> switchTier(@PathVariable String tierName) {
        logger.info("Manual tier switch requested: {}", tierName);

        boolean switched = compositePersistenceService.switchTier(tierName);

        Map<String, Object> data = new HashMap<>();
        data.put("tierName", tierName);
        data.put("switched", switched);
        data.put("currentTier", compositePersistenceService.getActiveTierName());

        if (!switched) {
            data.put("message", "Cannot switch to unhealthy tier: " + tierName);
        } else {
            data.put("message", "Successfully switched to: " + tierName);
        }

        return Mono.just(ResponseEntity.ok(createSuccessResponse(data)));
    }

    /**
     * 刷新健康状态缓存
     */
    @PostMapping("/tiers/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refreshHealthStatus() {
        logger.info("Manual health status refresh requested");

        compositePersistenceService.refreshHealthStatus();

        Map<String, Boolean> tierStatus = compositePersistenceService.getAllTierStatus();
        
        Map<String, Object> redisHealth = new HashMap<>();
        redisHealth.put("healthy", tierStatus.getOrDefault("redis", false));

        Map<String, Object> h2Health = new HashMap<>();
        h2Health.put("healthy", tierStatus.getOrDefault("h2", true));

        Map<String, Object> fileHealth = new HashMap<>();
        fileHealth.put("healthy", tierStatus.getOrDefault("file", true));

        Map<String, Object> data = new HashMap<>();
        data.put("redis", redisHealth);
        data.put("h2", h2Health);
        data.put("file", fileHealth);

        return Mono.just(ResponseEntity.ok(createSuccessResponse(data)));
    }

    /**
     * 手动同步状态 (v2.4.5)
     */
    @PostMapping("/sync")
    public Mono<ResponseEntity<Map<String, Object>>> syncStates() {
        logger.info("Manual state sync requested");

        int cbPending = cbPersistenceAdapter.getPendingSyncCount();
        int lbPending = lbPersistenceAdapter.getPendingSyncCount();

        // 触发限流器同步
        if (rlPersistenceAdapter != null) {
            rlPersistenceAdapter.syncPendingStates().subscribe();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "State sync triggered (rate limiter only for now)");
        data.put("circuitBreakerPending", cbPending);
        data.put("loadBalancerPending", lbPending);
        data.put("note", "CircuitBreaker and LoadBalancer sync requires additional context");

        return Mono.just(ResponseEntity.ok(createSuccessResponse(data)));
    }

    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}