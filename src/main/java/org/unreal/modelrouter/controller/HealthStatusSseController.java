package org.unreal.modelrouter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.checker.ServiceStateManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 使用Sinks.Many来支持主动推送更新
    private final Sinks.Many<ServerSentEvent<String>> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 建立SSE连接，推送实时健康状态更新
     * 
     * @return Flux<ServerSentEvent<String>>事件流
     */
    @GetMapping(path = "/stream")
    public Flux<ServerSentEvent<String>> streamHealthStatus() {
        log.info("SSE连接已建立");
        
        // 合并定时更新和主动推送的事件流
        Flux<ServerSentEvent<String>> periodicUpdates = Flux.interval(Duration.ofSeconds(5))
                .map(sequence -> createHealthUpdateEvent(sequence, "periodic"));
        
        // 合并主动推送的更新
        Flux<ServerSentEvent<String>> manualUpdates = eventSink.asFlux();
        
        // 返回合并后的事件流
        return Flux.merge(periodicUpdates, manualUpdates)
                .doOnCancel(() -> log.info("SSE连接已取消"))
                .doOnComplete(() -> log.info("SSE连接已完成"));
    }
    
    /**
     * 创建健康状态更新事件
     */
    private ServerSentEvent<String> createHealthUpdateEvent(long sequence, String eventType) {
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
     */
    private Map<String, Object> generateHealthStatusData() {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("type", "health-update");
        
        // 添加实例健康状态信息
        Map<String, Boolean> instanceHealthStatus = serviceStateManager.getAllInstanceHealthStatus();
        data.put("instanceHealth", instanceHealthStatus);
        
        log.debug("生成健康状态数据，实例数量: {}", instanceHealthStatus.size());
        
        return data;
    }
    
    /**
     * 当实例健康状态发生变化时，主动推送更新给所有连接的客户端
     * 该方法供ServiceStateManager调用
     */
    public void notifyHealthStatusChange() {
        ServerSentEvent<String> event = createHealthUpdateEvent(System.currentTimeMillis(), "manual");
        // 使用Sinks.Many来向所有订阅者推送事件
        eventSink.tryEmitNext(event).orThrow();
    }
}