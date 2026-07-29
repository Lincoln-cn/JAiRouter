package org.unreal.modelrouter.monitor.callhistory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 调用历史异步记录器
 * 使用有界队列和批量写入，避免阻塞请求处理线程
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.call-history.enabled", havingValue = "true", matchIfMissing = true)
public class ApiCallHistoryRecorder {

    private final ApiCallHistoryService service;
    private final CallHistoryProperties properties;

    private BlockingQueue<CallHistoryRecordDTO> buffer;
    private Thread consumerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedRecords = new AtomicLong(0);
    private final AtomicLong totalRecords = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("API call history recording is disabled");
            return;
        }

        buffer = new ArrayBlockingQueue<>(properties.getBufferSize());
        running.set(true);

        consumerThread = new Thread(this::consumeRecords, "call-history-writer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        log.info("API call history recorder initialized: bufferSize={}, batchSize={}, batchWaitMs={}",
                properties.getBufferSize(), properties.getBatchSize(), properties.getBatchWaitMs());
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 刷新剩余数据
        flushBuffer();
        log.info("API call history recorder stopped: totalRecords={}, droppedRecords={}",
                totalRecords.get(), droppedRecords.get());
    }

    /**
     * 异步记录调用历史
     * 如果缓冲区满，会丢弃记录（debug日志）
     *
     * @param record 调用记录
     */
    public void record(CallHistoryRecordDTO record) {
        if (!running.get() || buffer == null) {
            return;
        }

        totalRecords.incrementAndGet();
        if (!buffer.offer(record)) {
            droppedRecords.incrementAndGet();
            if (log.isDebugEnabled()) {
                log.debug("Call history buffer full, dropping record. model={}, total dropped={}",
                        record.getModelName(), droppedRecords.get());
            }
        }
    }

    /**
     * 同步记录（用于关键调用，会等待缓冲区有空间）
     *
     * @param record 调用记录
     */
    public void recordSync(CallHistoryRecordDTO record) {
        if (!running.get() || buffer == null) {
            return;
        }

        totalRecords.incrementAndGet();
        try {
            buffer.put(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedRecords.incrementAndGet();
            log.debug("Interrupted while waiting to record call history");
        }
    }

    /**
     * 获取缓冲区中的记录数
     */
    public int getBufferSize() {
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * 获取已丢弃的记录数
     */
    public long getDroppedRecords() {
        return droppedRecords.get();
    }

    /**
     * 获取总记录数
     */
    public long getTotalRecords() {
        return totalRecords.get();
    }

    /**
     * 消费记录的线程逻辑
     */
    private void consumeRecords() {
        List<CallHistoryRecordDTO> batch = new ArrayList<>(properties.getBatchSize());
        long lastFlushTime = System.currentTimeMillis();

        while (running.get()) {
            try {
                // 等待记录
                CallHistoryRecordDTO record = buffer.poll(1, TimeUnit.SECONDS);
                if (record != null) {
                    batch.add(record);
                }

                // 批量写入条件：批次满 或 等待超时且有数据
                long now = System.currentTimeMillis();
                boolean batchFull = batch.size() >= properties.getBatchSize();
                boolean waitTimeout = (now - lastFlushTime) >= properties.getBatchWaitMs() && !batch.isEmpty();

                if (batchFull || waitTimeout) {
                    flushBatch(batch);
                    lastFlushTime = now;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Error in call history consumer thread: {}", e.getMessage());
            }
        }

        // 关闭前刷新剩余数据
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    /**
     * 刷新缓冲区中的所有数据
     */
    private void flushBuffer() {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        List<CallHistoryRecordDTO> remaining = new ArrayList<>();
        buffer.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flushBatch(remaining);
        }
    }

    /**
     * 批量写入数据库
     */
    private void flushBatch(List<CallHistoryRecordDTO> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            service.batchRecord(new ArrayList<>(batch));
            log.debug("Flushed {} call history records", batch.size());
            batch.clear();
        } catch (Exception e) {
            log.warn("Failed to flush call history batch: {}", e.getMessage());
            // 不清空batch，下次重试
        }
    }
}
