package org.unreal.modelrouter.config.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default config migration service implementation.
 *
 * @since v2.6.12
 */
@Service
@Slf4j
public class DefaultConfigMigrationService implements ConfigMigrationService {

    private final ServiceConfigRepository serviceConfigRepository;

    @Autowired
    public DefaultConfigMigrationService(ServiceConfigRepository serviceConfigRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
    }

    @Override
    public Mono<MigrationResult> exportConfig(String sourceEnv, String targetEnv, String[] serviceTypes) {
        log.info("Exporting config: sourceEnv={}, targetEnv={}", sourceEnv, targetEnv);

        return Flux.fromIterable(serviceConfigRepository.findAll())
            .filter(config -> matchesServiceType(config, serviceTypes))
            .flatMap(this::convertForMigration)
            .collectList()
            .subscribeOn(Schedulers.boundedElastic())
            .map(migrated -> {
                if (migrated.isEmpty()) {
                    return MigrationResult.failure(sourceEnv, targetEnv, "No configs found for migration");
                }
                log.info("Migration exported: {} configs", migrated.size());
                return MigrationResult.success(sourceEnv, targetEnv, migrated.size());
            });
    }

    @Override
    public Mono<MigrationResult> importConfig(Map<String, Object> backupData, String targetEnv) {
        log.info("Importing config to environment: {}", targetEnv);

        if (backupData == null || backupData.isEmpty()) {
            return Mono.just(MigrationResult.failure("backup", targetEnv, "Empty backup data"));
        }

        return Flux.fromIterable(backupData.entrySet())
            .flatMap(entry -> importServiceConfig(entry.getKey(), entry.getValue()))
            .collectList()
            .subscribeOn(Schedulers.boundedElastic())
            .map(results -> {
                long successCount = results.stream().filter(r -> r).count();
                if (successCount == results.size()) {
                    return MigrationResult.success("backup", targetEnv, (int) successCount);
                }
                return MigrationResult.failure("backup", targetEnv, "Partial failure");
            });
    }

    private boolean matchesServiceType(ServiceConfigEntity config, String[] serviceTypes) {
        if (serviceTypes == null || serviceTypes.length == 0) {
            return true;
        }
        String configType = config.getServiceType();
        for (String type : serviceTypes) {
            if (type.equalsIgnoreCase(configType)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Map<String, Object>> convertForMigration(ServiceConfigEntity config) {
        return Mono.fromCallable(() -> {
            Map<String, Object> migrated = new HashMap<>();
            migrated.put("serviceType", config.getServiceType());
            migrated.put("adapter", config.getAdapter());
            migrated.put("loadBalanceType", config.getLoadBalanceType());
            return migrated;
        });
    }

    private Mono<Boolean> importServiceConfig(String serviceType, Object configData) {
        return Mono.fromCallable(() -> {
            try {
                ServiceConfigEntity existing = serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue(serviceType).orElse(null);
                ServiceConfigEntity entity;
                if (existing != null) {
                    entity = existing;
                } else {
                    entity = new ServiceConfigEntity();
                    entity.setServiceType(serviceType);
                }

                if (configData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) configData;
                    if (data.containsKey("adapter")) {
                        entity.setAdapter((String) data.get("adapter"));
                    }
                    if (data.containsKey("loadBalanceType")) {
                        entity.setLoadBalanceType((String) data.get("loadBalanceType"));
                    }
                }

                serviceConfigRepository.save(entity);
                log.info("Imported config for service: {}", serviceType);
                return true;
            } catch (Exception e) {
                log.error("Failed to import config for service: {}", serviceType, e);
                return false;
            }
        });
    }
}
