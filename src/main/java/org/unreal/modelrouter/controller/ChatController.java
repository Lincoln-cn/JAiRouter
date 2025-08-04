package org.unreal.modelrouter.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ChatController {

    private final ModelServiceRegistry registry;

    public ChatController(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/v1/chat/completions")
    public Mono<? extends ResponseEntity<?>> chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        try {
            // 获取客户端IP用于负载均衡
            String clientIp = IpUtils.getClientIp(httpRequest);

            // 使用负载均衡选择实例
            ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                    ModelServiceRegistry.ServiceType.CHAT,
                    request.model(),
                    clientIp
            );

            // 获取WebClient和路径
            WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.CHAT, request.model(), clientIp);
            String path = registry.getModelPath(ModelServiceRegistry.ServiceType.CHAT, request.model());

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
                            registry.recordCallComplete(ModelServiceRegistry.ServiceType.CHAT, selectedInstance);
                        })
                        .onErrorResume(throwable -> {
                            // 流式错误处理
                            return Flux.just("data: {\"error\": {\"message\": \"" +
                                    escapeJson(throwable.getMessage()) + "\", \"type\": \"internal_error\"}}\n\n");
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
                            registry.recordCallComplete(ModelServiceRegistry.ServiceType.CHAT, selectedInstance);
                        })
                        .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                                .headers(responseEntity.getHeaders())
                                .body(responseEntity.getBody()))
                        .onErrorResume(throwable -> {
                            // 非流式错误处理
                            String errorResponse = String.format(
                                    "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"model_error\"}}",
                                    escapeJson(throwable.getMessage())
                            );
                            return Mono.just(ResponseEntity.internalServerError()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(errorResponse));
                        });
            }
        } catch (Exception e) {
            // 配置或参数错误
            String errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": \"invalid_model\"}}",
                    escapeJson(e.getMessage())
            );
            return Mono.just(ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse));
        }
    }

    /**
     * 健康检查端点 - 显示当前负载均衡状态
     */
    @GetMapping("/v1/chat/status")
    public Mono<ResponseEntity<Object>> getChatStatus() {
        try {
            var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.CHAT);
            var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.CHAT);
            var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.CHAT);

            var status = new java.util.HashMap<String, Object>();
            status.put("service_type", "CHAT");
            status.put("load_balance_strategy", loadBalanceStrategy);
            status.put("available_models", availableModels);
            status.put("total_instances", allInstances != null ? allInstances.size() : 0);
            status.put("instances", allInstances);
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