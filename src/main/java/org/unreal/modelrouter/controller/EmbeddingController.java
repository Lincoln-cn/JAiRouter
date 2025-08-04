package org.unreal.modelrouter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.dto.EmbeddingDTO;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class EmbeddingController {

    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;

    public EmbeddingController(ModelServiceRegistry registry, ServerChecker serverChecker) {
        this.registry = registry;
        this.serverChecker = serverChecker;
    }

    @PostMapping("/v1/embeddings")
    public Mono<ResponseEntity<String>> createEmbedding(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EmbeddingDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (!serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.embedding.name())) {
            String errorResponse = "{\"error\": {\"message\": \"Embedding service is currently unavailable\", \"type\": \"service_unavailable\", \"code\": \"service_unavailable\"}}";
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse));
        }

        try {
            // 获取客户端IP用于负载均衡
            String clientIp = IpUtils.getClientIp(httpRequest);

            // 使用负载均衡选择实例
            ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                    ModelServiceRegistry.ServiceType.embedding,
                    request.model(),
                    clientIp
            );

            // 获取WebClient和路径
            WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), clientIp);
            String path = registry.getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

            return client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .bodyValue(request)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> {
                        // 无论成功还是失败，都要记录连接完成
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance);
                    })
                    .onErrorResume(Exception.class, ex -> {
                        // 记录异常并返回标准化错误响应
                        String errorResponse = String.format(
                                "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"model_error\"}}",
                                escapeJson(ex.getMessage())
                        );
                        return Mono.just(ResponseEntity.internalServerError()
                                .header("Content-Type", "application/json")
                                .body(errorResponse));
                    });

        } catch (Exception ex) {
            // 配置或参数错误
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
     * 健康检查端点 - 显示当前负载均衡状态
     */
    @GetMapping("/v1/embeddings/status")
    public Mono<ResponseEntity<Object>> getEmbeddingStatus() {
        try {
            var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.embedding);
            var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.embedding);
            var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.embedding);
            var isServiceHealthy = serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.embedding.name());

            // 添加每个实例的健康状态
            List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
            if (allInstances != null) {
                for (ModelRouterProperties.ModelInstance instance : allInstances) {
                    Map<String, Object> instanceInfo = new HashMap<>();
                    instanceInfo.put("name", instance.getName());
                    instanceInfo.put("baseUrl", instance.getBaseUrl());
                    instanceInfo.put("path", instance.getPath());
                    instanceInfo.put("weight", instance.getWeight());
                    instanceInfo.put("healthy", serverChecker.isInstanceHealthy(ModelServiceRegistry.ServiceType.embedding.name(), instance));
                    instancesWithHealth.add(instanceInfo);
                }
            }

            var status = new java.util.HashMap<String, Object>();
            status.put("service_type", ModelServiceRegistry.ServiceType.embedding.name());
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
     * 批量嵌入端点（支持多个输入）
     */
    @PostMapping("/v1/embeddings/batch")
    public Mono<ResponseEntity<String>> createBatchEmbedding(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EmbeddingDTO.BatchRequest batchRequest,
            ServerHttpRequest httpRequest) {

        try {
            String clientIp = IpUtils.getClientIp(httpRequest);

            // 为批量请求也使用负载均衡
            ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                    ModelServiceRegistry.ServiceType.embedding,
                    batchRequest.model(),
                    clientIp
            );

            WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, batchRequest.model(), clientIp);
            String path = registry.getModelPath(ModelServiceRegistry.ServiceType.embedding, batchRequest.model());

            return client.post()
                    .uri(path + "/batch") // 假设批量端点是 /batch
                    .header("Authorization", authorization)
                    .bodyValue(batchRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> {
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance);
                    })
                    .onErrorResume(Exception.class, ex -> {
                        String errorResponse = String.format(
                                "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"batch_error\"}}",
                                escapeJson(ex.getMessage())
                        );
                        return Mono.just(ResponseEntity.internalServerError()
                                .header("Content-Type", "application/json")
                                .body(errorResponse));
                    });

        } catch (Exception ex) {
            String errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": \"invalid_batch_request\"}}",
                    escapeJson(ex.getMessage())
            );
            return Mono.just(ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(errorResponse));
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