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
            String errorResponse = "{\"error\": {\"message\": \"Rerank service is currently unavailable\", \"type\": \"service_unavailable\", \"code\": \"service_unavailable\"}}";
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse));
        }
        try {
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
                        String errorResponse = String.format(
                                "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"rerank_error\"}}",
                                escapeJson(ex.getMessage())
                        );
                        return Mono.just(ResponseEntity.internalServerError()
                                .header("Content-Type", "application/json")
                                .body(errorResponse));
                    });

        } catch (Exception ex) {
            String errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": \"invalid_model\"}}",
                    escapeJson(ex.getMessage())
            );
            return Mono.just(ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(errorResponse));
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/v1/rerank/status")
    public Mono<ResponseEntity<Object>> getEmbeddingStatus() {
        try {
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
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", e.getMessage())));
        }
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "null";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}