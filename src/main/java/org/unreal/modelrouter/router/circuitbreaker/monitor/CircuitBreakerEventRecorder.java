package org.unreal.modelrouter.router.circuitbreaker.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 熔断器事件记录器
 * 使用环形缓冲区存储最近的采样记录
 * 支持暂停/恢复监控和历史记录导出
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
public class CircuitBreakerEventRecorder {

    private final CircuitBreakerMonitorConfig config;

    /**
     * 按实例ID分组的事件缓冲区
     */
    private final Map<String, RingBuffer<CircuitBreakerEvent>> eventBuffers = new ConcurrentHashMap<>();

    /**
     * 采样计数器
     */
    private final AtomicLong samplingCounter = new AtomicLong(0);

    /**
     * WebSocket 推送回调
     */
    private volatile Consumer<CircuitBreakerEvent> eventCallback;

    /**
     * 监控暂停状态
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CircuitBreakerEventRecorder(CircuitBreakerMonitorConfig config) {
        this.config = config;
        log.info("CircuitBreakerEventRecorder initialized: enabled={}, sampleRate={}, historySize={}",
            config.isEnabled(), config.getSampleRate(), config.getHistorySize());
    }

    /**
     * 记录熔断器事件（带采样判断）
     *
     * @param event 熔断器事件
     * @return 是否被采样记录
     */
    public boolean record(CircuitBreakerEvent event) {
        if (!config.isEnabled()) {
            return false;
        }

        if (paused.get()) {
            return false;
        }

        // 状态变化事件必须记录，不做采样
        boolean shouldSample = event.eventType() == CircuitBreakerEvent.EventType.STATE_CHANGE
                || shouldSample();

        if (!shouldSample) {
            return false;
        }

        String instanceId = event.instanceId();

        // 获取或创建该实例的环形缓冲区
        RingBuffer<CircuitBreakerEvent> buffer = eventBuffers.computeIfAbsent(
            instanceId,
            k -> new RingBuffer<>(config.getHistorySize())
        );

        // 添加到缓冲区
        buffer.add(event);

        // 触发 WebSocket 推送
        if (config.isWebsocketEnabled() && eventCallback != null) {
            try {
                eventCallback.accept(event);
            } catch (Exception e) {
                log.debug("Failed to push event via WebSocket: {}", e.getMessage());
            }
        }

        log.debug("Recorded circuit breaker event: {}", event.toCompactString());
        return true;
    }

    /**
     * 判断是否应该采样
     */
    private boolean shouldSample() {
        double sampleRate = config.getSampleRate();

        if (sampleRate >= 1.0) {
            return true;
        }

        if (sampleRate <= 0.0) {
            return false;
        }

        long count = samplingCounter.incrementAndGet();
        long interval = Math.max(1, Math.round(1.0 / sampleRate));

        return count % interval == 0;
    }

    /**
     * 获取指定实例的历史记录
     */
    public List<CircuitBreakerEvent> getHistory(String instanceId, int limit) {
        RingBuffer<CircuitBreakerEvent> buffer = eventBuffers.get(instanceId);
        if (buffer == null) {
            return Collections.emptyList();
        }

        List<CircuitBreakerEvent> all = buffer.getAll();
        int size = Math.min(limit, all.size());
        List<CircuitBreakerEvent> result = new ArrayList<>(all.subList(0, size));
        Collections.reverse(result);
        return result;
    }

    /**
     * 获取所有实例的历史记录
     */
    public Map<String, List<CircuitBreakerEvent>> getAllHistory(int limit) {
        Map<String, List<CircuitBreakerEvent>> result = new ConcurrentHashMap<>();
        eventBuffers.forEach((instanceId, buffer) -> {
            result.put(instanceId, getHistory(instanceId, limit));
        });
        return result;
    }

    /**
     * 获取总采样数量
     */
    public long getTotalSampledCount() {
        return eventBuffers.values().stream()
            .mapToLong(RingBuffer::size)
            .sum();
    }

    /**
     * 清空所有历史记录
     */
    public void clearAllHistory() {
        eventBuffers.clear();
        samplingCounter.set(0);
        log.info("Cleared all circuit breaker history");
    }

    /**
     * 设置 WebSocket 事件回调
     */
    public void setEventCallback(Consumer<CircuitBreakerEvent> callback) {
        this.eventCallback = callback;
    }

    /**
     * 更新缓冲区大小
     */
    public void updateHistorySize(int newSize) {
        eventBuffers.forEach((instanceId, oldBuffer) -> {
            RingBuffer<CircuitBreakerEvent> newBuffer = new RingBuffer<>(newSize);
            for (CircuitBreakerEvent event : oldBuffer.getAll()) {
                newBuffer.add(event);
            }
            eventBuffers.put(instanceId, newBuffer);
        });
        log.info("Updated history buffer size to: {}", newSize);
    }

    public void pause() {
        paused.set(true);
        log.info("Circuit breaker monitor paused");
    }

    public void resume() {
        paused.set(false);
        log.info("Circuit breaker monitor resumed");
    }

    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 导出历史记录为 JSON 格式
     */
    public String exportAsJson(int limit) {
        try {
            Map<String, List<CircuitBreakerEvent>> history = getAllHistory(limit);

            ExportData exportData = new ExportData(
                Instant.now().toString(),
                config.getSampleRate(),
                history
            );

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData);
        } catch (Exception e) {
            log.error("Failed to export history as JSON: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 导出历史记录为 CSV 格式
     */
    public String exportAsCsv(int limit) {
        StringWriter writer = new StringWriter();

        writer.write("timestamp,instance_id,instance_name,service_type,event_type,previous_state,current_state,failure_count,success_count,trigger_reason\n");

        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

        getAllHistory(limit).forEach((instanceId, events) -> {
            for (CircuitBreakerEvent event : events) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%d,%d,%s\n",
                    escapeCsv(formatter.format(event.timestamp())),
                    escapeCsv(event.instanceId()),
                    escapeCsv(event.instanceName() != null ? event.instanceName() : ""),
                    escapeCsv(event.serviceType() != null ? event.serviceType() : ""),
                    escapeCsv(event.eventType().name()),
                    escapeCsv(event.previousState() != null ? event.previousState() : ""),
                    escapeCsv(event.currentState() != null ? event.currentState() : ""),
                    event.failureCount(),
                    event.successCount(),
                    escapeCsv(event.triggerReason() != null ? event.triggerReason() : "")
                ));
            }
        });

        return writer.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record ExportData(
        String exportTime,
        double sampleRate,
        Map<String, List<CircuitBreakerEvent>> history
    ) {}

    private static class RingBuffer<T> {
        private final CopyOnWriteArrayList<T> buffer;
        private final int maxSize;
        private volatile int head = 0;

        public RingBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.buffer = new CopyOnWriteArrayList<>();
        }

        public synchronized void add(T item) {
            if (buffer.size() < maxSize) {
                buffer.add(item);
            } else {
                buffer.set(head, item);
                head = (head + 1) % maxSize;
            }
        }

        public List<T> getAll() {
            return new ArrayList<>(buffer);
        }

        public int size() {
            return buffer.size();
        }

        public void clear() {
            buffer.clear();
            head = 0;
        }
    }
}
