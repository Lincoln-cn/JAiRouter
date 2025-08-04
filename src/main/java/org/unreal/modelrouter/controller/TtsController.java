package org.unreal.modelrouter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.dto.TtsDTO;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

@RestController
public class TtsController {

    private final ModelServiceRegistry registry;

    public TtsController(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/v1/audio/speech")
    public Mono<ResponseEntity<byte[]>> createSpeech(
            @RequestHeader("Authorization") String authorization,
            @RequestBody TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        try {
            // 获取客户端IP用于负载均衡
            String clientIp = IpUtils.getClientIp(httpRequest);

            // 使用负载均衡选择实例
            ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                    ModelServiceRegistry.ServiceType.TTS,
                    request.model(),
                    clientIp
            );

            // 获取WebClient和路径
            WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.TTS, request.model(), clientIp);
            String path = registry.getModelPath(ModelServiceRegistry.ServiceType.TTS, request.model());

            return client.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .bodyValue(request)
                    .retrieve()
                    .toEntity(byte[].class)
                    .doFinally(signalType -> {
                        // 记录连接完成
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.TTS, selectedInstance);
                    })
                    .onErrorResume(Exception.class, ex -> {
                        // TTS错误返回JSON错误信息
                        String errorResponse = String.format(
                                "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"tts_error\"}}",
                                escapeJson(ex.getMessage())
                        );
                        return Mono.just(ResponseEntity.internalServerError()
                                .header("Content-Type", "application/json")
                                .body(errorResponse.getBytes()));
                    });

        } catch (Exception ex) {
            String errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": \"invalid_model\"}}",
                    escapeJson(ex.getMessage())
            );
            return Mono.just(ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(errorResponse.getBytes()));
        }
    }

    /**
     * 文本转语音 - 支持流式输出
     */
    @PostMapping("/v1/audio/speech/stream")
    public Mono<ResponseEntity<Object>> createSpeechStream(
            @RequestHeader("Authorization") String authorization,
            @RequestBody TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        try {
            String clientIp = IpUtils.getClientIp(httpRequest);

            ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                    ModelServiceRegistry.ServiceType.TTS,
                    request.model(),
                    clientIp
            );

            WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.TTS, request.model(), clientIp);
            String path = registry.getModelPath(ModelServiceRegistry.ServiceType.TTS, request.model());

            // 流式音频响应
            var streamResponse = client.post()
                    .uri(path + "/stream")
                    .header("Authorization", authorization)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(byte[].class)
                    .doFinally(signalType -> {
                        registry.recordCallComplete(ModelServiceRegistry.ServiceType.TTS, selectedInstance);
                    })
                    .onErrorResume(throwable -> {
                        // 流式错误处理
                        String errorJson = String.format(
                                "{\"error\": {\"message\": \"%s\", \"type\": \"internal_error\"}}",
                                escapeJson(throwable.getMessage())
                        );
                        return reactor.core.publisher.Flux.just(errorJson.getBytes());
                    });

            return Mono.just(ResponseEntity.ok()
                    .header("Content-Type", "audio/mpeg") // 或其他音频格式
                    .body(streamResponse));

        } catch (Exception ex) {
            String errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": \"invalid_stream_request\"}}",
                    escapeJson(ex.getMessage())
            );
            return Mono.just(ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(errorResponse.getBytes()));
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/v1/audio/speech/status")
    public Mono<ResponseEntity<Object>> getTtsStatus() {
        try {
            var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.TTS);
            var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.TTS);
            var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.TTS);

            var status = new java.util.HashMap<String, Object>();
            status.put("service_type", "TTS");
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