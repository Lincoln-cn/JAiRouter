package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪导出指标事件
 */
public class TraceExportMetricsEvent extends MetricsEvent {
    private final String exporterType;
    private final long duration;
    private final boolean success;
    private final int batchSize;

    public TraceExportMetricsEvent(final String exporterType, final long duration, final boolean success, final int batchSize) {
        this.exporterType = exporterType;
        this.duration = duration;
        this.success = success;
        this.batchSize = batchSize;
    }

    public String getExporterType() {
        return exporterType;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
