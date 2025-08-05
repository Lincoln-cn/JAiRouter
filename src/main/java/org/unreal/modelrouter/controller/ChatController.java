package org.unreal.modelrouter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;

    public ChatController(ModelServiceRegistry registry, ServerChecker serverChecker) {
        this.registry = registry;
        this.serverChecker = serverChecker;
    }

    @PostMapping("/v1/chat/completions")
    public Mono<? extends ResponseEntity<?>> chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (!serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.chat.name())) {
            ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("chat").message("chat service is currently unavailable").build();
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        }

        // 获取客户端IP用于负载均衡
        String clientIp = IpUtils.getClientIp(httpRequest);

        // 使用负载均衡选择实例
        ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                clientIp
        );

        // 获取WebClient和路径
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.chat, request.model(), clientIp);
        String path = registry.getModelPath(ModelServiceRegistry.ServiceType.chat, request.model());

        if (request.stream()) {
            // 流式响应：直接转发流数据
            Flux<String> streamResponse = client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doFinally(signalType -> {
                        // 无论成功还是失败，都要记录连接完成
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance);
                    })
                    .onErrorResume(throwable -> {
                        // 流式错误处理
                        ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("chat").message(throwable.getMessage()).build();
                        return Flux.just(errorResponse.toJson());
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamResponse));
        } else {
            // 非流式响应：等待完整响应并转发
            return client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .bodyValue(request)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> {
                        // 无论成功还是失败，都要记录连接完成
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance);
                    })
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(responseEntity.getBody()))
                    .onErrorResume(throwable -> {
                        // 非流式错误处理
                        ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("chat").message(throwable.getMessage()).build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    /**
     * 健康检查端点 - 显示当前负载均衡状态
     */
    @GetMapping("/v1/chat/status")
    public Mono<ResponseEntity<Object>> getChatStatus() {
        var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat);
        var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.chat);
        var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.chat);
        var isServiceHealthy = serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.chat.name());

        // 添加每个实例的健康状态
        List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
        if (allInstances != null) {
            for (ModelRouterProperties.ModelInstance instance : allInstances) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("name", instance.getName());
                instanceInfo.put("baseUrl", instance.getBaseUrl());
                instanceInfo.put("path", instance.getPath());
                instanceInfo.put("weight", instance.getWeight());
                instanceInfo.put("healthy", serverChecker.isInstanceHealthy(ModelServiceRegistry.ServiceType.chat.name(), instance));
                instancesWithHealth.add(instanceInfo);
            }
        }

        var status = new java.util.HashMap<String, Object>();
        status.put("service_type", ModelServiceRegistry.ServiceType.chat.name());
        status.put("load_balance_strategy", loadBalanceStrategy);
        status.put("available_models", availableModels);
        status.put("total_instances", allInstances != null ? allInstances.size() : 0);
        status.put("service_healthy", isServiceHealthy);
        status.put("instances", instancesWithHealth);
        status.put("timestamp", java.time.Instant.now().toString());

        return Mono.just(ResponseEntity.ok(status));
    }

}