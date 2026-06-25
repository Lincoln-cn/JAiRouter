package org.unreal.modelrouter.router.loadbalancer.monitor;

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
 * 路由事件记录器
 * 使用环形缓冲区存储最近的采样记录
 * 支持暂停/恢复监控和历史记录导出
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
@Slf4j
@Component
public class RoutingEventRecorder {

    private final RoutingMonitorConfig config;

    /**
     * 按服务类型分组的环形缓冲区
     */
    private final Map<String, RingBuffer<RoutingEvent>> eventBuffers = new ConcurrentHashMap<>();

    /**
     * 各服务类型的采样计数器
     */
    private final Map<String, AtomicLong> samplingCounters = new ConcurrentHashMap<>();

    /**
     * WebSocket 推送回调
     */
    private volatile Consumer<RoutingEvent> eventCallback;

    /**
     * 监控暂停状态（全局）
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * 各服务类型的暂停状态
     */
    private final Map<String, AtomicBoolean> servicePaused = new ConcurrentHashMap<>();

    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoutingEventRecorder(RoutingMonitorConfig config) {
        this.config = config;
        log.info("RoutingEventRecorder initialized: enabled={}, sampleRate={}, historySize={}",
            config.isEnabled(), config.getSampleRate(), config.getHistorySize());
    }

    /**
     * 记录路由事件（带采样判断）
     *
     * @param event 路由事件
     * @return 是否被采样记录
     */
    public boolean record(RoutingEvent event) {
        if (!config.isEnabled()) {
            return false;
        }

        // 检查全局暂停状态
        if (paused.get()) {
            return false;
        }

        // 检查服务级暂停状态
        AtomicBoolean servicePause = servicePaused.get(event.serviceType());
        if (servicePause != null && servicePause.get()) {
            return false;
        }

        // 采样判断
        if (!shouldSample(event.serviceType())) {
            return false;
        }

        String serviceType = event.serviceType();

        // 获取或创建该服务类型的环形缓冲区
        RingBuffer<RoutingEvent> buffer = eventBuffers.computeIfAbsent(
            serviceType,
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

        log.debug("Recorded routing event: {}", event.toCompactString());
        return true;
    }

    /**
     * 判断是否应该采样
     */
    private boolean shouldSample(String serviceType) {
        double sampleRate = config.getSampleRate();

        // 采样率为 1.0 时，全部记录
        if (sampleRate >= 1.0) {
            return true;
        }

        // 采样率为 0.0 时，全部跳过
        if (sampleRate <= 0.0) {
            return false;
        }

        // 使用计数器实现均匀采样
        AtomicLong counter = samplingCounters.computeIfAbsent(
            serviceType,
            k -> new AtomicLong(0)
        );

        long count = counter.incrementAndGet();
        long interval = Math.max(1, Math.round(1.0 / sampleRate));

        return count % interval == 0;
    }

    /**
     * 获取指定服务类型的历史记录
     *
     * @param serviceType 服务类型
     * @param limit 最大数量
     * @return 历史记录列表（按时间倒序）
     */
    public List<RoutingEvent> getHistory(String serviceType, int limit) {
        RingBuffer<RoutingEvent> buffer = eventBuffers.get(serviceType);
        if (buffer == null) {
            return Collections.emptyList();
        }

        List<RoutingEvent> all = buffer.getAll();
        int size = Math.min(limit, all.size());
        List<RoutingEvent> result = new ArrayList<>(all.subList(0, size));
        Collections.reverse(result); // 最新在前
        return result;
    }

    /**
     * 获取所有服务类型的历史记录
     *
     * @param limit 每个服务类型的最大数量
     * @return 按服务类型分组的历史记录
     */
    public Map<String, List<RoutingEvent>> getAllHistory(int limit) {
        Map<String, List<RoutingEvent>> result = new ConcurrentHashMap<>();
        eventBuffers.forEach((serviceType, buffer) -> {
            result.put(serviceType, getHistory(serviceType, limit));
        });
        return result;
    }

    /**
     * 获取指定服务类型的采样数量
     */
    public long getSampledCount(String serviceType) {
        RingBuffer<RoutingEvent> buffer = eventBuffers.get(serviceType);
        return buffer != null ? buffer.size() : 0;
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
     * 清空指定服务类型的历史记录
     */
    public void clearHistory(String serviceType) {
        RingBuffer<RoutingEvent> buffer = eventBuffers.get(serviceType);
        if (buffer != null) {
            buffer.clear();
        }
        samplingCounters.getOrDefault(serviceType, new AtomicLong(0)).set(0);
        log.info("Cleared routing history for service: {}", serviceType);
    }

    /**
     * 清空所有历史记录
     */
    public void clearAllHistory() {
        eventBuffers.clear();
        samplingCounters.clear();
        log.info("Cleared all routing history");
    }

    /**
     * 设置 WebSocket 事件回调
     */
    public void setEventCallback(Consumer<RoutingEvent> callback) {
        this.eventCallback = callback;
    }

    /**
     * 更新缓冲区大小（运行时调整）
     */
    public void updateHistorySize(int newSize) {
        eventBuffers.forEach((serviceType, oldBuffer) -> {
            RingBuffer<RoutingEvent> newBuffer = new RingBuffer<>(newSize);
            for (RoutingEvent event : oldBuffer.getAll()) {
                newBuffer.add(event);
            }
            eventBuffers.put(serviceType, newBuffer);
        });
        log.info("Updated history buffer size to: {}", newSize);
    }

    // ==================== 暂停/恢复监控 ====================

    /**
     * 暂停全局监控
     */
    public void pause() {
        paused.set(true);
        log.info("Routing monitor paused globally");
    }

    /**
     * 恢复全局监控
     */
    public void resume() {
        paused.set(false);
        log.info("Routing monitor resumed globally");
    }

    /**
     * 暂停指定服务类型的监控
     */
    public void pauseService(String serviceType) {
        servicePaused.computeIfAbsent(serviceType, k -> new AtomicBoolean(false)).set(true);
        log.info("Routing monitor paused for service: {}", serviceType);
    }

    /**
     * 恢复指定服务类型的监控
     */
    public void resumeService(String serviceType) {
        AtomicBoolean servicePause = servicePaused.get(serviceType);
        if (servicePause != null) {
            servicePause.set(false);
            log.info("Routing monitor resumed for service: {}", serviceType);
        }
    }

    /**
     * 获取全局暂停状态
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 获取指定服务类型的暂停状态
     */
    public boolean isServicePaused(String serviceType) {
        AtomicBoolean servicePause = servicePaused.get(serviceType);
        return servicePause != null && servicePause.get();
    }

    /**
     * 获取所有暂停的服务类型
     */
    public List<String> getPausedServices() {
        List<String> pausedList = new ArrayList<>();
        servicePaused.forEach((serviceType, paused) -> {
            if (paused.get()) {
                pausedList.add(serviceType);
            }
        });
        return pausedList;
    }

    // ==================== 导出功能 ====================

    /**
     * 导出历史记录为 JSON 格式
     *
     * @param serviceType 服务类型，null 表示所有服务
     * @param limit 每个服务的最大记录数
     * @return JSON 字符串
     */
    public String exportAsJson(String serviceType, int limit) {
        try {
            Map<String, List<RoutingEvent>> history;
            if (serviceType != null && !serviceType.isEmpty()) {
                history = Map.of(serviceType, getHistory(serviceType, limit));
            } else {
                history = getAllHistory(limit);
            }

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
     *
     * @param serviceType 服务类型，null 表示所有服务
     * @param limit 每个服务的最大记录数
     * @return CSV 字符串
     */
    public String exportAsCsv(String serviceType, int limit) {
        StringWriter writer = new StringWriter();

        // CSV 头
        writer.write("timestamp,service_type,strategy,selected_instance,selected_instance_url,client_id,candidate_count,selection_time_ms\n");

        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

        if (serviceType != null && !serviceType.isEmpty()) {
            writeCsvRecords(writer, serviceType, getHistory(serviceType, limit), formatter);
        } else {
            getAllHistory(limit).forEach((svc, events) -> {
                writeCsvRecords(writer, svc, events, formatter);
            });
        }

        return writer.toString();
    }

    private void writeCsvRecords(StringWriter writer, String serviceType, List<RoutingEvent> events, DateTimeFormatter formatter) {
        for (RoutingEvent event : events) {
            writer.write(String.format("%s,%s,%s,%s,%s,%s,%d,%d\n",
                escapeCsv(formatter.format(event.timestamp())),
                escapeCsv(serviceType),
                escapeCsv(event.strategy()),
                escapeCsv(event.selectedInstance()),
                escapeCsv(event.selectedInstanceUrl() != null ? event.selectedInstanceUrl() : ""),
                escapeCsv(event.clientId() != null ? event.clientId() : ""),
                event.candidateCount(),
                event.selectionTimeMs()
            ));
        }
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

    /**
     * 导出数据结构
     */
    public record ExportData(
        String exportTime,
        double sampleRate,
        Map<String, List<RoutingEvent>> history
    ) {}

    /**
     * 环形缓冲区实现
     */
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
