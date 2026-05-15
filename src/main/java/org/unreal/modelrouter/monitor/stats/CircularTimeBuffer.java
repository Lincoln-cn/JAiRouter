package org.unreal.modelrouter.monitor.stats;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 环形时间缓冲区
 *
 * 用于高效计算时间窗口内的请求数量（如 QPS 计算）。
 * 相比 LinkedList 方案：
 * - 内存分配固定，无 GC 压力
 * - 时间复杂度 O(1) 插入，O(1) 查询
 * - 使用细粒度锁而非 synchronized
 *
 * @author JAiRouter Team
 * @since 2.7.2
 */
public class CircularTimeBuffer {

    /**
     * 时间槽数组，每个槽存储该时间段内的请求数
     */
    private final TimeSlot[] slots;

    /**
     * 时间槽大小（毫秒）
     */
    private final long slotSizeMs;

    /**
     * 总时间窗口大小（毫秒）
     */
    private final long windowSizeMs;

    /**
     * 槽数量
     */
    private final int slotCount;

    /**
     * 总请求数
     */
    private final AtomicLong totalCount = new AtomicLong(0);

    /**
     * 用于更新操作的锁
     */
    private final ReentrantLock updateLock = new ReentrantLock();

    /**
     * 创建环形时间缓冲区
     *
     * @param windowSizeMs 时间窗口大小（毫秒）
     * @param slotCount 槽数量
     */
    public CircularTimeBuffer(final long windowSizeMs, final int slotCount) {
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("windowSizeMs must be positive");
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }

        this.windowSizeMs = windowSizeMs;
        this.slotCount = slotCount;
        this.slotSizeMs = windowSizeMs / slotCount;
        this.slots = new TimeSlot[slotCount];

        // 初始化所有槽
        for (int i = 0; i < slotCount; i++) {
            slots[i] = new TimeSlot();
        }
    }

    /**
     * 使用默认配置创建（60秒窗口，60个槽）
     */
    public CircularTimeBuffer() {
        this(60000, 60);
    }

    /**
     * 记录一次请求
     */
    public void record() {
        record(1);
    }

    /**
     * 记录多次请求
     *
     * @param count 请求数量
     */
    public void record(final int count) {
        if (count <= 0) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int slotIndex = calculateSlotIndex(currentTime);

        updateLock.lock();
        try {
            TimeSlot slot = slots[slotIndex];

            // 如果时间戳过期，重置槽
            if (currentTime - slot.timestamp >= slotSizeMs) {
                slot.reset(currentTime);
            }

            slot.count += count;
            totalCount.addAndGet(count);
        } finally {
            updateLock.unlock();
        }
    }

    /**
     * 获取当前时间窗口内的请求数
     *
     * @return 请求数量
     */
    public long getCount() {
        cleanupExpiredSlots();
        return totalCount.get();
    }

    /**
     * 获取当前 QPS（每秒请求数）
     *
     * @return QPS
     */
    public double getQps() {
        long count = getCount();
        return count * 1000.0 / windowSizeMs;
    }

    /**
     * 清理过期的时间槽
     */
    private void cleanupExpiredSlots() {
        long currentTime = System.currentTimeMillis();
        long expiredThreshold = currentTime - windowSizeMs;

        // 遍历所有槽，清理过期的
        for (TimeSlot slot : slots) {
            if (slot.timestamp > 0 && slot.timestamp < expiredThreshold && slot.count > 0) {
                updateLock.lock();
                try {
                    // 双重检查
                    if (slot.timestamp < expiredThreshold && slot.count > 0) {
                        totalCount.addAndGet(-slot.count);
                        slot.count = 0;
                        slot.timestamp = 0;
                    }
                } finally {
                    updateLock.unlock();
                }
            }
        }
    }

    /**
     * 计算时间槽索引
     */
    private int calculateSlotIndex(final long timestamp) {
        return (int) ((timestamp / slotSizeMs) % slotCount);
    }

    /**
     * 重置缓冲区
     */
    public void reset() {
        updateLock.lock();
        try {
            for (TimeSlot slot : slots) {
                slot.reset(0);
            }
            totalCount.set(0);
        } finally {
            updateLock.unlock();
        }
    }

    /**
     * 获取配置信息
     */
    public long getWindowSizeMs() {
        return windowSizeMs;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public long getTotalCount() {
        return totalCount.get();
    }

    /**
     * 时间槽数据结构
     */
    private static class TimeSlot {
        volatile long timestamp;
        volatile int count;

        void reset(final long newTimestamp) {
            this.timestamp = newTimestamp;
            this.count = 0;
        }
    }
}
