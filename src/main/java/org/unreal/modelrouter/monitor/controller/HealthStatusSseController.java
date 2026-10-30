package org.unreal.modelrouter.monitor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 健康状态SSE推送控制器
 * 提供Server-Sent Events方式的实时健康状态推送
 */
@Slf4j
@RestController
@RequestMapping("/api/health-status")
public class HealthStatusSseController {

    @Autowired
    private ServiceStateManager serviceStateManager;

    @Autowired(required = false)
    private ServiceInstanceRepository serviceInstanceRepository;

    @Autowired(required = false)
    private ServiceConfigRepository serviceConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 快照生成次数，仅用于测试断言「多个订阅者是否共享同一次计算」 */
    private final AtomicLong snapshotComputations = new AtomicLong();

    /** 周期快照间隔（毫秒），默认 5 秒 */
    @Value("${jairouter.health-status.stream-interval-ms:5000}")
    private long streamIntervalMs = 5_000L;

    /**
     * 周期快照流：所有 SSE 连接共享同一条热点流。
     *
     * <p>此前每个连接各自 `Flux.interval` 并各自调用 {@link #generateHealthStatusData()}，
     * 每次都要两次全表扫描（服务配置表 + 实例表）⇒ N 个控制台标签页就是每 5 秒 2N 次全表查询。
     * 改为 `share()`（refCount(1)）后：无订阅者时定时器不跑，有订阅者时每轮只算一次、广播给所有连接。</p>
     */
    private volatile Flux<ServerSentEvent<String>> sharedPeriodicUpdates;

    // 监控推送：有界缓冲（256），溢出由 tryEmitNext 返回失败并丢弃，避免内存无界增长
    private final Sinks.Many<ServerSentEvent<String>> eventSink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    /**
     * 建立SSE连接，推送实时健康状态更新
     *
     * @return Flux<ServerSentEvent<String>>事件流
     */
    @GetMapping(path = "/stream")
    public Flux<ServerSentEvent<String>> streamHealthStatus() {
        log.debug("SSE连接已建立");

        // 定时快照：JPA 为阻塞 API，必须在 boundedElastic 上生成，避免卡住 Netty EventLoop
        final Flux<ServerSentEvent<String>> periodicUpdates = periodicUpdates();

        // 主动推送：慢消费者时丢弃积压 tick，避免无界缓冲
        Flux<ServerSentEvent<String>> manualUpdates = eventSink.asFlux()
                .onBackpressureLatest();

        return Flux.merge(periodicUpdates, manualUpdates)
                .doOnCancel(() -> log.debug("SSE连接已取消"))
                .doOnComplete(() -> log.debug("SSE连接已完成"));
    }

    /**
     * 取共享的周期快照流（首次使用时构建）。
     */
    private Flux<ServerSentEvent<String>> periodicUpdates() {
        Flux<ServerSentEvent<String>> current = sharedPeriodicUpdates;
        if (current == null) {
            synchronized (this) {
                if (sharedPeriodicUpdates == null) {
                    sharedPeriodicUpdates = buildPeriodicUpdates();
                }
                current = sharedPeriodicUpdates;
            }
        }
        return current;
    }

    private Flux<ServerSentEvent<String>> buildPeriodicUpdates() {
        final long intervalMs = Math.max(100L, streamIntervalMs);
        return Flux.interval(Duration.ofMillis(intervalMs))
                .onBackpressureLatest()
                .concatMap(sequence -> Mono.fromCallable(() -> createHealthUpdateEvent(sequence, "periodic"))
                        .subscribeOn(Schedulers.boundedElastic()))
                // refCount(1)：最后一个订阅者离开时停表，新连接到来时重新开始
                .share();
    }

    /**
     * 已生成的快照次数（供测试断言共享行为）。
     */
    long snapshotComputations() {
        return snapshotComputations.get();
    }

    /**
     * 创建健康状态更新事件
     */
    private ServerSentEvent<String> createHealthUpdateEvent(final long sequence, final String eventType) {
        Map<String, Object> data = generateHealthStatusData();
        try {
            return ServerSentEvent.<String>builder()
                    .event("health-update")
                    .id(String.valueOf(sequence))
                    .data(objectMapper.writeValueAsString(data))
                    .build();
        } catch (Exception e) {
            log.error("序列化健康状态数据失败", e);
            // 发送错误事件
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .id(String.valueOf(sequence))
                    .data("{\"error\":\"序列化数据失败\", \"eventType\":\"" + eventType + "\"}")
                    .build();
        }
    }

    /**
     * 生成健康状态数据
     * v1.7.1: 从数据库获取健康状态
     * key 格式为 serviceType:instanceId (UUID)
     */
    private Map<String, Object> generateHealthStatusData() {
        snapshotComputations.incrementAndGet();
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("type", "health-update");

        Map<String, Boolean> instanceHealthStatus = new HashMap<>();

        if (serviceInstanceRepository != null && serviceConfigRepository != null) {
            try {
                // 一次拉取服务类型映射，避免每个实例 findById 造成 N+1 阻塞
                Map<Long, String> serviceTypeByConfigId = new HashMap<>();
                serviceConfigRepository.findAll()
                        .forEach(config -> {
                            if (config.getId() != null && config.getServiceType() != null) {
                                serviceTypeByConfigId.put(config.getId(), config.getServiceType());
                            }
                        });
                List<ServiceInstanceEntity> allInstances = serviceInstanceRepository.findAll();
                for (ServiceInstanceEntity entity : allInstances) {
                    String serviceType = serviceTypeByConfigId.get(entity.getServiceConfigId());
                    if (serviceType != null) {
                        // 使用 instanceId 作为 key 的第二部分
                        String instanceId = entity.getInstanceId();
                        if (instanceId == null || instanceId.isEmpty()) {
                            // 如果没有 instanceId，使用 instanceName 作为后备
                            instanceId = entity.getInstanceName();
                        }
                        String key = serviceType + ":" + instanceId;
                        boolean isHealthy = "HEALTHY".equals(entity.getHealthStatus());
                        instanceHealthStatus.put(key, isHealthy);
                    }
                }
                log.debug("从数据库生成健康状态数据，实例数量: {}", instanceHealthStatus.size());
            } catch (Exception e) {
                log.warn("从数据库获取健康状态失败，使用内存缓存: {}", e.getMessage());
                instanceHealthStatus = serviceStateManager.getAllInstanceHealthStatus();
            }
        } else {
            instanceHealthStatus = serviceStateManager.getAllInstanceHealthStatus();
        }

        data.put("instanceHealth", instanceHealthStatus);
        return data;
    }

    /**
     * 当实例健康状态发生变化时，主动推送更新给所有连接的客户端
     * 该方法供ServiceStateManager调用
     */
    public void notifyHealthStatusChange() {
        try {
            ServerSentEvent<String> event = createHealthUpdateEvent(System.currentTimeMillis(), "manual");
            Sinks.EmitResult result = eventSink.tryEmitNext(event);
            // 无订阅者 / 背压拒绝时不抛出，避免健康检查回调打崩调用方
            if (result.isFailure()) {
                log.debug("健康状态SSE推送丢弃: {}", result);
            }
        } catch (Exception e) {
            log.warn("健康状态SSE通知失败: {}", e.getMessage());
        }
    }
}