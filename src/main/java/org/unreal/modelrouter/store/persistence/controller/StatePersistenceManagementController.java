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
import org.unreal.modelrouter.store.persistence.recovery.StateRecoveryService;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态持久化管理 REST API
 * 
 * v2.4.4: 提供手动恢复和状态监控 API
 * 
 * API 端点:
 * - POST /api/state-persistence/recovery/all - 手动触发全部恢复
 * - POST /api/state-persistence/recovery/circuit-breaker/{instanceId} - 单个熔断器恢复
 * - POST /api/state-persistence/recovery/load-balancer/{serviceType} - 单个负载均衡器恢复
 * - GET /api/state-persistence/status - 获取持久化状态
 * - GET /api/state-persistence/tiers - 获取各层健康状态
 * - POST /api/state-persistence/tiers/switch/{tierName} - 手动切换存储层
 * - POST /api/state-persistence/tiers/refresh - 刷新健康状态
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

    /**
     * 手动触发全部状态恢复
     */
    @PostMapping("/recovery/all")
    public Mono<ResponseEntity<Map<String, Object>>> triggerFullRecovery() {
        logger.info("Manual full state recovery triggered via API");

        if (recoveryService == null) {
            return Mono.just(ResponseEntity.ok(createErrorResponse("Recovery service is disabled")));
        }

        return recoveryService.triggerManualRecovery()
                .map(stats -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", stats.success);
                    response.put("durationMs", stats.durationMs);
                    response.put("circuitBreaker", Map.of(
                            "found", stats.cbStatesFound,
                            "recovered", stats.cbStatesRecovered,
                            "failed", stats.cbStatesFailed
                    ));
                    response.put("loadBalancer", Map.of(
                            "found", stats.lbStatesFound,
                            "recovered", stats.lbStatesRecovered,
                            "failed", stats.lbStatesFailed
                    ));
                    response.put("totalRecovered", stats.getTotalRecovered());
                    response.put("totalFailed", stats.getTotalFailed());
                    return ResponseEntity.ok(response);
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
            return Mono.just(ResponseEntity.ok(createErrorResponse("Recovery service is disabled")));
        }

        return recoveryService.recoverSingleCircuitBreaker(instanceId)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("instanceId", instanceId);
                    response.put("recovered", result);
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * 手动触发单个负载均衡器恢复
     */
    @PostMapping("/recovery/load-balancer/{serviceType}")
    public Mono<ResponseEntity<Map<String, Object>>> recoverSingleLoadBalancer(
            @PathVariable String serviceType) {
        
        logger.info("Manual recovery for load balancer: {}", serviceType);

        if (recoveryService == null) {
            return Mono.just(ResponseEntity.ok(createErrorResponse("Recovery service is disabled")));
        }

        ModelServiceRegistry.ServiceType type;
        try {
            type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid service type: " + serviceType)));
        }

        return recoveryService.recoverSingleLoadBalancer(type)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("serviceType", serviceType);
                    response.put("recovered", result);
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * 获取持久化状态
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getPersistenceStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("activeTier", compositePersistenceService.getActiveTierName());
        response.put("activeTierPriority", compositePersistenceService.getActiveTierPriority());
        response.put("healthy", compositePersistenceService.isHealthy().block());
        response.put("pendingSync", Map.of(
                "circuitBreaker", cbPersistenceAdapter.getPendingSyncCount(),
                "loadBalancer", lbPersistenceAdapter.getPendingSyncCount()
        ));

        // 添加恢复统计
        if (recoveryService != null) {
            StateRecoveryService.RecoveryStatistics stats = recoveryService.getLastRecoveryStats();
            response.put("lastRecovery", Map.of(
                    "success", stats.success,
                    "durationMs", stats.durationMs,
                    "cbRecovered", stats.cbStatesRecovered,
                    "lbRecovered", stats.lbStatesRecovered
            ));
        }

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 获取各存储层健康状态
     */
    @GetMapping("/tiers")
    public Mono<ResponseEntity<Map<String, Object>>> getTierStatus() {
        Map<String, Boolean> tierStatus = compositePersistenceService.getAllTierStatus();
        
        Map<String, Object> response = new HashMap<>();
        response.put("tiers", tierStatus);
        response.put("active", compositePersistenceService.getActiveTierName());
        response.put("description", Map.of(
                "redis", "Tier 1 - 分布式共享状态，最高优先级",
                "h2", "Tier 2 - 默认退坡层，嵌入式数据库",
                "file", "Tier 3 - 兜底方案，文件存储"
        ));

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 手动切换存储层
     */
    @PostMapping("/tiers/switch/{tierName}")
    public Mono<ResponseEntity<Map<String, Object>>> switchTier(@PathVariable String tierName) {
        logger.info("Manual tier switch requested: {}", tierName);

        boolean switched = compositePersistenceService.switchTier(tierName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("tierName", tierName);
        response.put("switched", switched);
        response.put("activeTier", compositePersistenceService.getActiveTierName());

        if (!switched) {
            response.put("error", "Cannot switch to unhealthy tier: " + tierName);
        }

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 刷新健康状态缓存
     */
    @PostMapping("/tiers/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refreshHealthStatus() {
        logger.info("Manual health status refresh requested");

        compositePersistenceService.refreshHealthStatus();
        
        Map<String, Object> response = new HashMap<>();
        response.put("refreshed", true);
        response.put("tiers", compositePersistenceService.getAllTierStatus());
        response.put("activeTier", compositePersistenceService.getActiveTierName());

        return Mono.just(ResponseEntity.ok(response));
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