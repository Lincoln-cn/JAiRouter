package org.unreal.modelrouter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.SttDTO;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AudioTranscriptionsController {


    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;

    public AudioTranscriptionsController(ModelServiceRegistry registry, ServerChecker serverChecker) {
        this.registry = registry;
        this.serverChecker = serverChecker;
    }


    @PostMapping("/v1/audio/transcriptions")
    public Mono<ResponseEntity<String>> transcribeAudio(
            @RequestHeader("Authorization") String authorization,
            SttDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (!serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.stt.name())) {
            ErrorResponse errorResponse = ErrorResponse.builder().code("error").type("audio_transcriptions")
                    .message("stt service is currently unavailable").build();
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        }

        // 获取客户端IP用于负载均衡
        String clientIp = IpUtils.getClientIp(httpRequest);

        // 使用负载均衡选择实例
        ModelRouterProperties.ModelInstance selectedInstance = registry.selectInstance(
                ModelServiceRegistry.ServiceType.stt,
                request.model(),
                clientIp
        );

        // 获取WebClient和路径
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.stt, request.model(), clientIp);
        String path = registry.getModelPath(ModelServiceRegistry.ServiceType.stt, request.model());
        return DataBufferUtils.join(request.file().content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(fileBytes -> {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("model", request.model());
                    builder.part("language", request.language());
                    builder.part("file", fileBytes)
                            .filename(request.file().filename())
                            .contentType(MediaType.APPLICATION_OCTET_STREAM);

                    return client.post()
                            .uri(path)
                            .header("Authorization", authorization)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .bodyValue(builder.build())
                            .retrieve()
                            .toEntity(String.class);
                })
                .doFinally(signalType -> {
                    registry.recordCallComplete(ModelServiceRegistry.ServiceType.stt, selectedInstance);
                })
                .onErrorResume(Exception.class, ex -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("audio_transcriptions")
                            .message(ex.getMessage())
                            .build();
                    ex.printStackTrace();
                    return Mono.just(ResponseEntity.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/v1/audio/transcriptions/status")
    public Mono<ResponseEntity<Object>> getEmbeddingStatus() {
        var availableModels = registry.getAvailableModels(ModelServiceRegistry.ServiceType.stt);
        var loadBalanceStrategy = registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.stt);
        var allInstances = registry.getAllInstances().get(ModelServiceRegistry.ServiceType.stt);
        var isServiceHealthy = serverChecker.isServiceHealthy(ModelServiceRegistry.ServiceType.stt.name());

        // 添加每个实例的健康状态
        List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
        if (allInstances != null) {
            for (ModelRouterProperties.ModelInstance instance : allInstances) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("name", instance.getName());
                instanceInfo.put("baseUrl", instance.getBaseUrl());
                instanceInfo.put("path", instance.getPath());
                instanceInfo.put("weight", instance.getWeight());
                instanceInfo.put("healthy", serverChecker.isInstanceHealthy(ModelServiceRegistry.ServiceType.stt.name(), instance));
                instancesWithHealth.add(instanceInfo);
            }
        }
        var status = new java.util.HashMap<String, Object>();
        status.put("service_type", ModelServiceRegistry.ServiceType.stt.name());
        status.put("load_balance_strategy", loadBalanceStrategy);
        status.put("available_models", availableModels);
        status.put("total_instances", allInstances != null ? allInstances.size() : 0);
        status.put("service_healthy", isServiceHealthy);
        status.put("instances", instancesWithHealth);
        status.put("timestamp", java.time.Instant.now().toString());

        return Mono.just(ResponseEntity.ok(status));
    }
}
