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
import org.unreal.modelrouter.dto.TtsDTO;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class AudioSpeechController {

    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;

    public AudioSpeechController(ModelServiceRegistry registry, ServerChecker serverChecker) {
        this.registry = registry;
        this.serverChecker = serverChecker;
    }

    @PostMapping("/v1/audio/speech")
    public Mono<? extends ResponseEntity<?>> createSpeech(
            @RequestHeader("Authorization") String authorization,
            @RequestBody TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (!serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.tts.name())) {
            ErrorResponse error = ErrorResponse.builder()
                    .code("error")
                    .type("tts")
                    .message("TTS service is currently unavailable")
                    .build();
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(error.toJson()));
        }

        // 获取客户端IP用于负载均衡
        String clientIp = IpUtils.getClientIp(httpRequest);

        // 使用负载均衡选择实例
        ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                ModelServiceRegistry.ServiceType.tts,
                request.model(),
                clientIp
        );

        // 获取WebClient和路径
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.tts, request.model(), clientIp);
        String path = registry.getModelPath(ModelServiceRegistry.ServiceType.tts, request.model());

        //获取请求头上的accept-type
        String acceptType = httpRequest.getHeaders().getFirst("Accept");
        //判断是否是application/octet-stream
        boolean isStreaming = MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(acceptType);
        if (isStreaming) {
            // 流式音频响应
            var streamResponse = client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(byte[].class)
                    .doFinally(signalType -> {
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.tts, selectedInstance);
                    })
                    .onErrorResume(throwable -> {
                        ErrorResponse error = ErrorResponse.builder()
                                .code("service_unavailable")
                                .type("tts")
                                .message(throwable.getMessage())
                                .build();
                        return Mono.just(Objects.requireNonNull(ResponseEntity.status(503)
                                .header("Content-Type", "application/json")
                                .body(error.toJson().getBytes()).getBody()));
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON) // 或其他音频格式
                    .body(streamResponse));
        } else {
            return client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .toEntity(byte[].class)
                    .doFinally(signalType -> {
                        // 记录连接完成
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.tts, selectedInstance);
                    })
                    .onErrorResume(Exception.class, ex -> {
                        // TTS错误返回JSON错误信息
                        try {
                            ErrorResponse error = ErrorResponse.builder()
                                    .code("service_unavailable")
                                    .type("tts")
                                    .message(ex.getMessage())
                                    .build();
                            return Mono.just(ResponseEntity.internalServerError()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(error.toJson().getBytes()));
                        } catch (Exception jsonEx) {
                            return Mono.error(jsonEx);
                        }
                    });
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/v1/audio/speech/status")
    public Mono<ResponseEntity<Object>> getEmbeddingStatus() {
        var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.tts);
        var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.tts);
        var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.tts);
        var isServiceHealthy = serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.tts.name());

        // 添加每个实例的健康状态
        List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
        if (allInstances != null) {
            for (ModelRouterProperties.ModelInstance instance : allInstances) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("name", instance.getName());
                instanceInfo.put("baseUrl", instance.getBaseUrl());
                instanceInfo.put("path", instance.getPath());
                instanceInfo.put("weight", instance.getWeight());
                instanceInfo.put("healthy", serverChecker.isInstanceHealthy(ModelServiceRegistry.ServiceType.tts.name(), instance));
                instancesWithHealth.add(instanceInfo);
            }
        }

        var status = new java.util.HashMap<String, Object>();
        status.put("service_type", ModelServiceRegistry.ServiceType.tts.name());
        status.put("load_balance_strategy", loadBalanceStrategy);
        status.put("available_models", availableModels);
        status.put("total_instances", allInstances != null ? allInstances.size() : 0);
        status.put("service_healthy", isServiceHealthy);
        status.put("instances", instancesWithHealth);
        status.put("timestamp", java.time.Instant.now().toString());

        return Mono.just(ResponseEntity.ok(status));
    }
}