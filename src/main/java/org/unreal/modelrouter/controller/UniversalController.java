package org.unreal.modelrouter.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.adapter.ServiceCapability;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.response.ErrorResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用控制器 - 处理所有AI服务的统一入口
 */
@RestController
@RequestMapping("/v1")
public class UniversalController {

    private final ModelServiceRegistry registry;
    private final ServerChecker serverChecker;
    private final AdapterRegistry adapterRegistry;

    public UniversalController(ModelServiceRegistry registry, ServerChecker serverChecker, AdapterRegistry adapterRegistry) {
        this.registry = registry;
        this.serverChecker = serverChecker;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 聊天完成接口
     */
    @PostMapping("/chat/completions")
    public Mono<? extends ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.chat,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)
                        .chat(request, authorization, httpRequest)
        );
    }

    /**
     * 文本嵌入接口
     */
    @PostMapping("/embeddings")
    public Mono<? extends ResponseEntity<?>> embeddings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody EmbeddingDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.embedding,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.embedding)
                        .embedding(request, authorization, httpRequest)
        );
    }

    /**
     * 重排序接口
     */
    @PostMapping("/rerank")
    public Mono<? extends ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RerankDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.rerank,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.rerank)
                        .rerank(request, authorization, httpRequest)
        );
    }

    /**
     * 文本转语音接口
     */
    @PostMapping("/audio/speech")
    public Mono<? extends ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.tts,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.tts)
                        .tts(request, authorization, httpRequest)
        );
    }

    /**
     * 语音转文本接口
     */
    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<? extends ResponseEntity<?>> speechToText(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            SttDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.stt,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.stt)
                        .stt(request, authorization, httpRequest)
        );
    }

    /**
     * 通用服务请求处理器
     */
    private Mono<? extends ResponseEntity<?>> handleServiceRequest(
            ModelServiceRegistry.ServiceType serviceType,
            ServiceRequestSupplier requestSupplier) {

        // 检查服务健康状态
        if (!serverChecker.isServiceHealthy(serviceType.name())) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("service_unavailable")
                    .type(serviceType.name())
                    .message(serviceType.name() + " service is currently unavailable")
                    .build();
            return Mono.just(ResponseEntity.status(503)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        }

        try {
            // 委托给适配器处理
            return requestSupplier.get();

        } catch (UnsupportedOperationException e) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("unsupported_operation")
                    .type(serviceType.name())
                    .message("Service not supported by current adapter: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.status(501)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        } catch (IllegalArgumentException e) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("adapter_error")
                    .type(serviceType.name())
                    .message("Adapter configuration error: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        } catch (Exception e) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("internal_error")
                    .type(serviceType.name())
                    .message("Internal server error: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.internalServerError()
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toJson()));
        }
    }

    /**
     * 获取所有可用模型
     */
    @GetMapping("/models")
    public Mono<ResponseEntity<Object>> getModels() {
        List<Map<String, Object>> allModels = new ArrayList<>();

        // 收集所有服务类型的模型
        for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
            var availableModels = registry.getAvailableModels(serviceType);

            for (String modelName : availableModels) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("id", modelName);
                modelInfo.put("object", "model");
                modelInfo.put("created", System.currentTimeMillis() / 1000);
                modelInfo.put("owned_by", "model-router");
                modelInfo.put("service_type", serviceType.name());

                // 获取适配器信息
                try {
                    ServiceCapability adapter = adapterRegistry.getAdapter(serviceType);
                    modelInfo.put("adapter", adapter.getClass().getSimpleName());
                } catch (Exception e) {
                    modelInfo.put("adapter", "unknown");
                }

                allModels.add(modelInfo);
            }
        }

        var response = new HashMap<String, Object>();
        response.put("object", "list");
        response.put("data", allModels);
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 获取特定服务的状态
     */
    @GetMapping("/status/{serviceType}")
    public Mono<ResponseEntity<Object>> getServiceStatus(@PathVariable String serviceType) {
        try {
            ModelServiceRegistry.ServiceType type = ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase());

            var availableModels = registry.getAvailableModels(type);
            var loadBalanceStrategy = registry.getLoadBalanceStrategy(type);
            var allInstances = registry.getAllInstances().get(type);
            var isServiceHealthy = serverChecker.isServiceHealthy(type.name());

            // 实例健康状态
            List<Map<String, Object>> instancesWithHealth = new ArrayList<>();
            if (allInstances != null) {
                for (var instance : allInstances) {
                    Map<String, Object> instanceInfo = new HashMap<>();
                    instanceInfo.put("name", instance.getName());
                    instanceInfo.put("baseUrl", instance.getBaseUrl());
                    instanceInfo.put("path", instance.getPath());
                    instanceInfo.put("weight", instance.getWeight());
                    instanceInfo.put("healthy", serverChecker.isInstanceHealthy(type.name(), instance));
                    instancesWithHealth.add(instanceInfo);
                }
            }

            // 获取当前适配器
            String currentAdapter = "unknown";
            try {
                ServiceCapability adapter = adapterRegistry.getAdapter(type);
                currentAdapter = adapter.getClass().getSimpleName();
            } catch (Exception e) {
                currentAdapter = "error: " + e.getMessage();
            }

            var status = new HashMap<String, Object>();
            status.put("service_type", type.name());
            status.put("adapter", currentAdapter);
            status.put("load_balance_strategy", loadBalanceStrategy);
            status.put("available_models", availableModels);
            status.put("total_instances", allInstances != null ? allInstances.size() : 0);
            status.put("service_healthy", isServiceHealthy);
            status.put("instances", instancesWithHealth);
            status.put("timestamp", java.time.Instant.now().toString());

            return Mono.just(ResponseEntity.ok(status));

        } catch (IllegalArgumentException e) {
            var error = new HashMap<String, Object>();
            error.put("error", "Invalid service type: " + serviceType);
            error.put("valid_types", ModelServiceRegistry.ServiceType.values());
            return Mono.just(ResponseEntity.badRequest().body(error));
        }
    }

    /**
     * 获取系统总体状态
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Object>> getSystemStatus() {
        var systemStatus = new HashMap<String, Object>();
        var serviceStatuses = new HashMap<String, Object>();

        // 收集所有服务状态
        for (ModelServiceRegistry.ServiceType serviceType : ModelServiceRegistry.ServiceType.values()) {
            var serviceStatus = new HashMap<String, Object>();

            var availableModels = registry.getAvailableModels(serviceType);
            var allInstances = registry.getAllInstances().get(serviceType);
            var isServiceHealthy = serverChecker.isServiceHealthy(serviceType.name());

            serviceStatus.put("healthy", isServiceHealthy);
            serviceStatus.put("model_count", availableModels.size());
            serviceStatus.put("instance_count", allInstances != null ? allInstances.size() : 0);

            // 获取适配器信息
            try {
                ServiceCapability adapter = adapterRegistry.getAdapter(serviceType);
                serviceStatus.put("adapter", adapter.getClass().getSimpleName());
            } catch (Exception e) {
                serviceStatus.put("adapter", "error");
            }

            serviceStatuses.put(serviceType.name(), serviceStatus);
        }

        systemStatus.put("services", serviceStatuses);
        systemStatus.put("timestamp", java.time.Instant.now().toString());
        systemStatus.put("version", "1.0.0");
        systemStatus.put("name", "Model Router Gateway");

        return Mono.just(ResponseEntity.ok(systemStatus));
    }

    /**
     * 函数式接口用于延迟执行服务请求
     */
    @FunctionalInterface
    private interface ServiceRequestSupplier {
        Mono<? extends ResponseEntity<?>> get() throws Exception;
    }
}