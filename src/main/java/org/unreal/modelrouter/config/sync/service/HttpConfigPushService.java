package org.unreal.modelrouter.config.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * HTTP config push service implementation.
 *
 * @since v2.6.12
 */
@Service
@Slf4j
public class HttpConfigPushService implements ConfigPushService {

    private final ServiceConfigRepository serviceConfigRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private WebClient webClient;

    private static final String PUSH_ENDPOINT = "/internal/config/push";

    @Autowired
    public HttpConfigPushService(
            ServiceConfigRepository serviceConfigRepository,
            ServiceInstanceRepository serviceInstanceRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public Mono<PushResult> pushToService(String targetService, Map<String, Object> config) {
        log.info("Pushing config to service: {}", targetService);

        return Mono.fromCallable(() -> {
            var configOpt = serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(targetService);
            if (configOpt.isEmpty()) {
                return null;
            }
            Long configId = configOpt.get().getId();
            return serviceInstanceRepository.findByServiceConfigId(configId);
        })
        .flatMap(instances -> {
            if (instances == null || instances.isEmpty()) {
                log.warn("No instances found for service: {}", targetService);
                return Mono.just(PushResult.failure(targetService, "No instances found"));
            }

            var activeInstance = instances.stream()
                .filter(instance -> "ACTIVE".equals(instance.getStatus()))
                .findFirst();

            if (activeInstance.isPresent()) {
                return pushToInstance(activeInstance.get().getBaseUrl(), targetService, config);
            } else {
                return Mono.just(PushResult.failure(targetService, "No active instance"));
            }
        })
        .defaultIfEmpty(PushResult.failure(targetService, "Service not found"));
    }

    @Override
    public Mono<BroadcastResult> broadcastConfig(Map<String, Object> config) {
        log.info("Broadcasting config to all services");

        return Flux.fromIterable(serviceConfigRepository.findAll())
            .flatMap(serviceConfig ->
                pushToService(serviceConfig.getServiceType(), config)
                    .onErrorResume(e -> Mono.just(PushResult.failure(
                        serviceConfig.getServiceType(), e.getMessage()))))
            .collectList()
            .map(results -> {
                long successCount = results.stream().filter(PushResult::success).count();
                long failureCount = results.size() - successCount;

                if (failureCount == 0) {
                    return BroadcastResult.success((int) successCount);
                } else if (successCount == 0) {
                    return new BroadcastResult(false, 0, (int) failureCount, "All pushes failed");
                } else {
                    return BroadcastResult.partial((int) successCount, (int) failureCount);
                }
            });
    }

    private Mono<PushResult> pushToInstance(String baseUrl, String serviceType, Map<String, Object> config) {
        String pushUrl = baseUrl + PUSH_ENDPOINT;
        log.debug("Pushing config to: {}", pushUrl);

        try {
            String jsonBody = JacksonHelper.getObjectMapper().writeValueAsString(config);

            return webClient.post()
                .uri(pushUrl)
                .bodyValue(jsonBody)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    int statusCode = response.getStatusCode().value();
                    if (statusCode >= 200 && statusCode < 300) {
                        return PushResult.success(serviceType);
                    } else {
                        return PushResult.failure(serviceType, "HTTP " + statusCode);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to push config to {}: {}", pushUrl, e.getMessage());
                    return Mono.just(PushResult.failure(serviceType, e.getMessage()));
                });
        } catch (Exception e) {
            log.warn("Failed to serialize config: {}", e.getMessage());
            return Mono.just(PushResult.failure(serviceType, e.getMessage()));
        }
    }
}
