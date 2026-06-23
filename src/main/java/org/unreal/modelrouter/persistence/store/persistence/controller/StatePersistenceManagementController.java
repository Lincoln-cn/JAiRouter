package org.unreal.modelrouter.persistence.store.persistence.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.store.persistence.CompositeStatePersistenceServiceImpl;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.persistence.store.persistence.adapter.LoadBalancerStatePersistenceAdapter;
import org.unreal.modelrouter.persistence.store.persistence.adapter.RateLimiterStatePersistenceAdapter;
import org.unreal.modelrouter.persistence.store.persistence.recovery.StateRecoveryService;
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
            @PathVariable final String instanceId) {

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
            @PathVariable final String serviceType) {

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
            @PathVariable final String limiterId) {

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
     * v2.9.x: 修复统计逻辑，使用实际存储数量而非待同步数量
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getPersistenceStatus() {
        return compositePersistenceService.isHealthy().flatMap(healthy -> {
            Map<String, Object> data = new HashMap<>();
            data.put("currentTier", compositePersistenceService.getActiveTierName());
            data.put("tierHealth", compositePersistenceService.getAllTierStatus());

            // 获取熔断器实际存储数量
            Mono<Long> cbCountMono = cbPersistenceAdapter.getAllCircuitBreakerInstanceIds()
                    .flatMapIterable(ids -> ids)
                    .count();

            // 获取负载均衡器实际存储数量
            Mono<Integer> lbCountMono = lbPersistenceAdapter.getAllLoadBalancerStates()
                    .map(lbStates -> lbStates.size());

            // 获取限流器实际存储数量
            Mono<Integer> rlCountMono;
            if (rlPersistenceAdapter != null) {
                rlCountMono = compositePersistenceService.getAllKeys(
                        org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService.StateType.RATE_LIMITER)
                        .map(keys -> {
                            // Iterable 转 Collection 计算 size
                            java.util.List<String> list = new java.util.ArrayList<>();
                            keys.forEach(list::add);
                            return list.size();
                        })
                        .defaultIfEmpty(0);
            } else {
                rlCountMono = Mono.just(0);
            }

            // 合并所有计数
            return Mono.zip(cbCountMono, lbCountMono, rlCountMono)
                    .map(tuple -> {
                        Long cbCount = tuple.getT1();
                        Integer lbCount = tuple.getT2();
                        Integer rlCount = tuple.getT3();

                        Map<String, Object> stats = new HashMap<>();
                        stats.put("circuitBreakerCount", cbCount.intValue());
                        stats.put("loadBalancerCount", lbCount);
                        stats.put("rateLimiterCount", rlCount);
                        stats.put("pendingSync", cbPersistenceAdapter.getPendingSyncCount()
                                + lbPersistenceAdapter.getPendingSyncCount());
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
        });
    }

    /**
     * 获取状态详情列表 (v2.4.5)
     * 从持久化层加载熔断器、负载均衡器、限流器的实际状态
     */
    @GetMapping("/details")
    public Mono<ResponseEntity<Map<String, Object>>> getStateDetails() {
        List<Map<String, Object>> details = new ArrayList<>();

        // 1. 从持久化层获取熔断器状态
        return cbPersistenceAdapter.getAllCircuitBreakerInstanceIds()
                .flatMapIterable(instanceIds -> instanceIds)
                .flatMap(instanceId -> {
                    return cbPersistenceAdapter.loadCircuitBreakerState(instanceId)
                            .map(stateData -> {
                                Map<String, Object> detail = new HashMap<>();
                                detail.put("instanceId", instanceId);
                                detail.put("stateType", "CIRCUIT_BREAKER");
                                detail.put("state", stateData.getOrDefault("state", "UNKNOWN"));
                                detail.put("lastModified", stateData.getOrDefault("timestamp", System.currentTimeMillis()));
                                detail.put("failureCount", stateData.get("failureCount"));
                                detail.put("successCount", stateData.get("successCount"));
                                detail.put("tier", compositePersistenceService.getTierName());
                                return detail;
                            })
                            .onErrorResume(e -> {
                                logger.warn("Failed to load circuit breaker state: {}", instanceId);
                                return Mono.empty();
                            });
                })
                .collectList()
                .flatMap(cbDetails -> {
                    details.addAll(cbDetails);

                    // 2. 从持久化层获取负载均衡器状态
                    return lbPersistenceAdapter.getAllLoadBalancerStates();
                })
                .flatMap(lbStates -> {
                    for (Map.Entry<ModelServiceRegistry.ServiceType, Map<String, Object>> entry : lbStates.entrySet()) {
                        Map<String, Object> stateData = entry.getValue();
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("serviceType", entry.getKey().name());
                        detail.put("stateType", "LOAD_BALANCER");
                        detail.put("state", stateData.getOrDefault("strategy", "unknown"));
                        detail.put("lastModified", stateData.getOrDefault("timestamp", System.currentTimeMillis()));
                        detail.put("tier", compositePersistenceService.getTierName());
                        details.add(detail);
                    }

                    // 3. 从持久化层获取限流器状态
                    if (rlPersistenceAdapter != null) {
                        return compositePersistenceService.getAllKeys(
                                org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService.StateType.RATE_LIMITER)
                                .flatMapIterable(keys -> keys)
                                .flatMap(limiterId -> {
                                    return rlPersistenceAdapter.loadRateLimiterState(limiterId)
                                            .map(stateData -> {
                                                Map<String, Object> detail = new HashMap<>();
                                                detail.put("instanceId", limiterId);
                                                detail.put("stateType", "RATE_LIMITER");
                                                detail.put("state", stateData.getOrDefault("algorithm", "unknown"));
                                                detail.put("lastModified", stateData.getOrDefault("timestamp", System.currentTimeMillis()));
                                                detail.put("requestsPerSecond", stateData.get("requestsPerSecond"));
                                                detail.put("capacity", stateData.get("capacity"));
                                                detail.put("tier", compositePersistenceService.getTierName());
                                                return detail;
                                            })
                                            .onErrorResume(e -> {
                                                logger.warn("Failed to load rate limiter state: {}", limiterId);
                                                return Mono.empty();
                                            });
                                })
                                .collectList();
                    }
                    return Mono.just(new ArrayList<Map<String, Object>>());
                })
                .map(rlDetails -> {
                    details.addAll(rlDetails);
                    return ResponseEntity.ok(createSuccessResponse(details));
                });
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
    public Mono<ResponseEntity<Map<String, Object>>> switchTier(@PathVariable final String tierName) {
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
     * 测试用：手动保存熔断器状态 (v2.9.x)
     * 用于验证状态持久化和前端显示
     */
    @PostMapping("/test/save-circuit-breaker/{instanceId}")
    public Mono<ResponseEntity<Map<String, Object>>> testSaveCircuitBreakerState(
            @PathVariable final String instanceId) {

        logger.info("Test save circuit breaker state for instance: {}", instanceId);

        // 创建测试状态数据
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("instanceId", instanceId);
        stateData.put("state", "OPEN");
        stateData.put("failureCount", 5);
        stateData.put("successCount", 0);
        stateData.put("timestamp", System.currentTimeMillis());

        return compositePersistenceService.save(
                org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService.StateType.CIRCUIT_BREAKER,
                instanceId,
                stateData)
                .map(saved -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("instanceId", instanceId);
                    data.put("saved", saved);
                    data.put("stateData", stateData);
                    return ResponseEntity.ok(createSuccessResponse(data));
                });
    }

    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(final Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(final String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}