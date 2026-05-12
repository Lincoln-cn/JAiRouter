package org.unreal.modelrouter.config.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.util.JacksonHelper;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Default instance config update service implementation.
 *
 * @since v2.6.12
 */
@Service
@Slf4j
public class DefaultInstanceConfigUpdateService implements InstanceConfigUpdateService {

    private final ServiceInstanceRepository instanceRepository;

    @Autowired
    public DefaultInstanceConfigUpdateService(ServiceInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    @Override
    public Mono<UpdateResult> updateInstanceConfig(String instanceId, Map<String, Object> config) {
        log.info("Updating instance config: instanceId={}", instanceId);

        return Mono.fromCallable(() -> instanceRepository.findByInstanceName(instanceId))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(instanceOpt -> {
                if (instanceOpt.isEmpty()) {
                    log.warn("Instance not found: {}", instanceId);
                    return Mono.just(UpdateResult.failure(instanceId, "Instance not found"));
                }

                ServiceInstanceEntity instance = instanceOpt.get();
                try {
                    String configJson = JacksonHelper.getObjectMapper().writeValueAsString(config);
                    instance.setErrorMessage(configJson);
                    instanceRepository.save(instance);
                    log.info("Instance config updated successfully: instanceId={}", instanceId);
                    return Mono.just(UpdateResult.success(instanceId));
                } catch (Exception e) {
                    log.error("Failed to update instance config: instanceId={}, error={}",
                        instanceId, e.getMessage(), e);
                    return Mono.just(UpdateResult.failure(instanceId, e.getMessage()));
                }
            });
    }

    @Override
    public Mono<BatchUpdateResult> batchUpdate(List<InstanceConfigUpdate> updates) {
        log.info("Batch updating instance config: count={}", updates.size());

        return Flux.fromIterable(updates)
            .flatMap(update ->
                updateInstanceConfig(update.instanceId(), update.config())
                    .onErrorResume(e -> Mono.just(
                        UpdateResult.failure(update.instanceId(), e.getMessage()))))
            .collectList()
            .map(BatchUpdateResult::of)
            .subscribeOn(Schedulers.boundedElastic());
    }
}
