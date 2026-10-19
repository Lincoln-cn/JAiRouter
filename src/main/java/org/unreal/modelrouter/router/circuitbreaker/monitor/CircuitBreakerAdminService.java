package org.unreal.modelrouter.router.circuitbreaker.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.CircuitBreakerStateHistoryDTO;
import org.unreal.modelrouter.config.service.CircuitBreakerConfigService;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.persistence.store.persistence.adapter.CircuitBreakerStatePersistenceAdapter;
import org.unreal.modelrouter.router.circuitbreaker.AdaptiveThresholdManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 熔断器运维服务：实例视图合并与手动重置编排。
 *
 * <p>手动重置与 {@code CircuitBreakerManager#resetCircuitBreaker(String, String)} 的区别：
 * 后者把实例从注册表移除（供配置变更后重建），本服务用就地重置，并且把重置做成一次完整体：
 * <ol>
 *   <li>内存状态机：{@code state → CLOSED}、失败/成功计数清零；</li>
 *   <li>持久化层：删除该实例的状态记录，避免重启后被 {@code StateRecoveryService} 恢复成 OPEN；</li>
 *   <li>历史记录：写一条 {@code MANUAL_RESET} 状态变更（可审计）；</li>
 *   <li>监控事件：写一条监控采样，运维在事件流里能看到这次操作；</li>
 *   <li>自适应阈值：清零该实例的采样计数，避免旧失败率继续影响阈值。</li>
 * </ol>
 *
 * <p>刻意<b>不</b>发布 {@code CircuitBreakerStateChangeEvent}：该事件的监听器会再次把状态写回持久化层，
 * 与第 2 步的删除相互抵消。
 *
 * @author JAiRouter Team
 * @since 3.3.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerAdminService {

    /** 历史记录里 {@code triggerReason} 为枚举名，操作者备注只进日志。 */
    private static final String MANUAL_RESET = CircuitBreakerStateHistoryDTO.TriggerReason.MANUAL_RESET.name();

    private final CircuitBreakerManager circuitBreakerManager;
    private final AdaptiveThresholdManager adaptiveThresholdManager;
    private final CircuitBreakerConfigService circuitBreakerConfigService;
    private final CircuitBreakerMonitorService monitorService;
    private final CircuitBreakerStatePersistenceAdapter persistenceAdapter;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ServiceConfigRepository serviceConfigRepository;

    /**
     * 列出当前内存中所有熔断器，并用实例表补齐实例名、服务类型与上游地址。
     *
     * @return 按服务类型、实例名排序的视图
     */
    public List<CircuitBreakerInstanceView> listInstances() {
        Map<Long, String> serviceTypeByConfigId = new HashMap<>();
        for (ServiceConfigEntity config : serviceConfigRepository.findAll()) {
            serviceTypeByConfigId.put(config.getId(), config.getServiceType());
        }
        Map<String, ServiceInstanceEntity> instanceByInstanceId = new HashMap<>();
        for (ServiceInstanceEntity entity : serviceInstanceRepository.findAll()) {
            if (entity.getInstanceId() != null && !entity.getInstanceId().isBlank()) {
                instanceByInstanceId.putIfAbsent(entity.getInstanceId(), entity);
            }
        }
        List<CircuitBreakerInstanceView> views = new ArrayList<>();
        circuitBreakerManager.getAllCircuitBreakerDetails().forEach((key, detail) -> {
            views.add(toView(key, detail, instanceByInstanceId.get(key), serviceTypeByConfigId));
        });
        views.sort(Comparator
                .comparing(CircuitBreakerInstanceView::serviceType, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CircuitBreakerInstanceView::instanceName, Comparator.nullsLast(Comparator.naturalOrder())));
        return views;
    }

    /**
     * 手动重置单个实例。
     *
     * @param instanceId 实例 UUID
     * @param reason 操作者备注（可为空），仅写入日志，不落库
     * @return 重置结果；该实例当前没有熔断器时返回空 Mono（控制器据此回 404）
     */
    public Mono<CircuitBreakerResetResult> reset(final String instanceId, final String reason) {
        Optional<ServiceInstanceEntity> entity = serviceInstanceRepository.findByInstanceId(instanceId);
        CircuitBreaker.State previousState = circuitBreakerManager.resetCircuitBreakerInPlace(instanceId);
        if (previousState == null) {
            return Mono.empty();
        }

        String instanceName = entity.map(ServiceInstanceEntity::getInstanceName).orElse(null);
        String serviceType = entity.map(this::resolveServiceType).orElse(null);
        String note = reason == null || reason.isBlank() ? "未填写" : reason.trim();

        recordHistory(instanceId, instanceName, serviceType, previousState, note);
        resetAdaptiveMetrics(instanceId);

        return persistenceAdapter.deleteCircuitBreakerState(instanceId)
                .map(deleted -> resultOf(instanceId, instanceName, serviceType, previousState, deleted))
                .defaultIfEmpty(resultOf(instanceId, instanceName, serviceType, previousState, false))
                .onErrorResume(error -> {
                    log.warn("手动重置后删除持久化状态失败: instanceId={}, error={}", instanceId, error.getMessage());
                    return Mono.just(resultOf(instanceId, instanceName, serviceType, previousState, false));
                });
    }

    /**
     * 批量手动重置：显式给定的实例优先；未给实例时按服务类型与状态过滤当前内存中的熔断器。
     *
     * @param instanceIds 显式实例 UUID 列表（可为空）
     * @param serviceType 服务类型过滤（可为空）
     * @param states 状态过滤（可为空）
     * @param reason 操作者备注（可为空）
     * @return 命中数与逐个结果
     */
    public Mono<CircuitBreakerBatchResetResult> resetBatch(final List<String> instanceIds,
                                                           final String serviceType,
                                                           final List<String> states,
                                                           final String reason) {
        List<String> targets = resolveTargets(instanceIds, serviceType, states);
        if (targets.isEmpty()) {
            return Mono.just(new CircuitBreakerBatchResetResult(0, List.of()));
        }
        return Flux.fromIterable(targets)
                .concatMap(instanceId -> reset(instanceId, reason))
                .collectList()
                .map(results -> new CircuitBreakerBatchResetResult(targets.size(), results));
    }

    private List<String> resolveTargets(final List<String> instanceIds, final String serviceType, final List<String> states) {
        if (instanceIds != null && !instanceIds.isEmpty()) {
            return instanceIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        }
        if ((serviceType == null || serviceType.isBlank()) && (states == null || states.isEmpty())) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (CircuitBreakerInstanceView view : listInstances()) {
            if (serviceType != null && !serviceType.isBlank() && !serviceType.equals(view.serviceType())) {
                continue;
            }
            if (states != null && !states.isEmpty() && !states.contains(view.state())) {
                continue;
            }
            targets.add(view.instanceId());
        }
        return targets;
    }

    private void recordHistory(final String instanceId, final String instanceName, final String serviceType,
                               final CircuitBreaker.State previousState, final String note) {
        try {
            circuitBreakerConfigService.recordStateChange(instanceId, instanceName, serviceType,
                    previousState, CircuitBreaker.State.CLOSED, MANUAL_RESET, 0, 0);
        } catch (Exception error) {
            log.warn("手动重置写入历史记录失败: instanceId={}, error={}", instanceId, error.getMessage());
        }
        try {
            monitorService.recordStateChange(instanceId, instanceName, serviceType,
                    previousState.name(), CircuitBreaker.State.CLOSED.name(), 0, 0, MANUAL_RESET);
        } catch (Exception error) {
            log.warn("手动重置写入监控事件失败: instanceId={}, error={}", instanceId, error.getMessage());
        }
        log.info("手动重置熔断器: instanceId={}, {} -> CLOSED, operatorReason={}", instanceId, previousState, note);
    }

    private void resetAdaptiveMetrics(final String instanceId) {
        AdaptiveThresholdManager.InstanceMetrics metrics = adaptiveThresholdManager.getMetrics(instanceId);
        if (metrics != null) {
            metrics.reset();
        }
    }

    private String resolveServiceType(final ServiceInstanceEntity entity) {
        if (entity.getServiceConfigId() == null) {
            return null;
        }
        return serviceConfigRepository.findById(entity.getServiceConfigId())
                .map(ServiceConfigEntity::getServiceType)
                .orElse(null);
    }

    private CircuitBreakerInstanceView toView(final String key, final Map<String, Object> detail,
                                              final ServiceInstanceEntity entity,
                                              final Map<Long, String> serviceTypeByConfigId) {
        String instanceName = entity != null ? entity.getInstanceName() : null;
        String serviceType = entity != null && entity.getServiceConfigId() != null
                ? serviceTypeByConfigId.get(entity.getServiceConfigId()) : null;
        String baseUrl = entity != null ? joinUrl(entity.getBaseUrl(), entity.getPath()) : null;
        Long lastFailureTime = asLong(detail.get("lastFailureTime"));
        return new CircuitBreakerInstanceView(
                key,
                instanceName,
                serviceType,
                baseUrl,
                String.valueOf(detail.getOrDefault("state", CircuitBreaker.State.CLOSED.name())),
                asInt(detail.get("failureCount")),
                asInt(detail.get("successCount")),
                asInt(detail.get("failureThreshold")),
                asInt(detail.get("successThreshold")),
                asLongValue(detail.get("timeout")),
                lastFailureTime
        );
    }

    private CircuitBreakerResetResult resultOf(final String instanceId, final String instanceName,
                                               final String serviceType, final CircuitBreaker.State previousState,
                                               final boolean persistedStateDeleted) {
        return new CircuitBreakerResetResult(instanceId, instanceName, serviceType, previousState.name(),
                CircuitBreaker.State.CLOSED.name(), persistedStateDeleted, MANUAL_RESET);
    }

    private static String joinUrl(final String baseUrl, final String path) {
        if (baseUrl == null) {
            return null;
        }
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        return baseUrl.endsWith("/") || path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    private static int asInt(final Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long asLongValue(final Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Long asLong(final Object value) {
        if (value instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        return null;
    }
}
