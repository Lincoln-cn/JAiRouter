package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 指标事件基类
 * 所有异步指标事件的父类
 */
public abstract class MetricsEvent {
    protected final long timestamp = System.currentTimeMillis();

    /**
     * 获取事件时间戳
     * @return 时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }
}
