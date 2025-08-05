package org.unreal.modelrouter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.dto.RerankDTO;
import org.unreal.modelrouter.util.IpUtils;
import org.unreal.modelrouter.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RerankController {

    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;

    public RerankController(ModelServiceRegistry registry, ServerChecker serverChecker) {
        this.registry = registry;
        this.serverChecker = serverChecker;
    }

    @PostMapping("/v1/rerank")
    public Mono<ResponseEntity<String>> rerank(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RerankDTO.Request request,
            ServerHttpRequest httpRequest) {
        if (!serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.rerank.name())) {
            ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("rerank").message("Rerank service is currently unavailable").build();
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        }
        // 获取客户端IP用于负载均衡
        String clientIp = IpUtils.getClientIp(httpRequest);

        // 使用负载均衡选择实例
        ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                ModelServiceRegistry.ServiceType.rerank,
                request.model(),
                clientIp
        );

        // 获取WebClient和路径
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.rerank, request.model(), clientIp);
        String path = registry.getModelPath(ModelServiceRegistry.ServiceType.rerank, request.model());

        return client.post()
                .uri(path)
                .header("Authorization", authorization)
                .bodyValue(request)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> {
                    // 记录连接完成
                    registry.recordCallComplete(ModelServiceRegistry.ServiceType.rerank, selectedInstance);
                })
                .onErrorResume(Exception.class, ex -> {
                    ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("rerank").message(ex.getMessage()).build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .header("Content-Type", "application/json")
                            .body(errorResponse.toJson()));
                });

    }

    /**
     * 健康检查端点
     */
    @GetMapping("/v1/rerank/status")
    public Mono<ResponseEntity<Object>> getEmbeddingStatus() {
        var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.rerank);
        var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.rerank);
        var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.rerank);
        var isServiceHealthy = serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.rerank.name());

        // 添加每个实例的健康状态
        List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
        if (allInstances != null) {
            for (ModelRouterProperties.ModelInstance instance : allInstances) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("name", instance.getName());
                instanceInfo.put("baseUrl", instance.getBaseUrl());
                instanceInfo.put("path", instance.getPath());
                instanceInfo.put("weight", instance.getWeight());
                instanceInfo.put("healthy", serverChecker.isInstanceHealthy(ModelServiceRegistry.ServiceType.rerank.name(), instance));
                instancesWithHealth.add(instanceInfo);
            }
        }

        var status = new java.util.HashMap<String, Object>();
        status.put("service_type", ModelServiceRegistry.ServiceType.rerank.name());
        status.put("load_balance_strategy", loadBalanceStrategy);
        status.put("available_models", availableModels);
        status.put("total_instances", allInstances != null ? allInstances.size() : 0);
        status.put("service_healthy", isServiceHealthy);
        status.put("instances", instancesWithHealth);
        status.put("timestamp", java.time.Instant.now().toString());
        
        return Mono.just(ResponseEntity.ok(status));
    }

}