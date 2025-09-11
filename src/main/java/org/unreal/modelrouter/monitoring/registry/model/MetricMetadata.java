package org.unreal.modelrouter.monitoring.registry.model;

import io.micrometer.core.instrument.Meter;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 指标元数据模型
 * 包含指标的描述信息、类型、标签等元数据
 */
public class MetricMetadata {
    
    private final String name;
    private final String description;
    private final Meter.Type type;
    private final String unit;
    private final Map<String, String> baseTags;
    private final String category;
    private final Instant createdAt;
    private final Instant lastUpdatedAt;
    private final boolean enabled;
    private final double samplingRate;
    
    private MetricMetadata(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.unit = builder.unit;
        this.baseTags = Map.copyOf(builder.baseTags);
        this.category = builder.category;
        this.createdAt = builder.createdAt;
        this.lastUpdatedAt = builder.lastUpdatedAt;
        this.enabled = builder.enabled;
        this.samplingRate = builder.samplingRate;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Meter.Type getType() {
        return type;
    }
    
    public String getUnit() {
        return unit;
    }
    
    public Map<String, String> getBaseTags() {
        return baseTags;
    }
    
    public String getCategory() {
        return category;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public double getSamplingRate() {
        return samplingRate;
    }
    
    public static Builder builder(String name, Meter.Type type) {
        return new Builder(name, type);
    }
    
    public Builder toBuilder() {
        return new Builder(this);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricMetadata that = (MetricMetadata) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) &&
               Objects.equals(baseTags, that.baseTags);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, type, baseTags);
    }
    
    @Override
    public String toString() {
        return "MetricMetadata{" +
               "name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", type=" + type +
               ", unit='" + unit + '\'' +
               ", category='" + category + '\'' +
               ", enabled=" + enabled +
               ", samplingRate=" + samplingRate +
               '}';
    }
    
    public static class Builder {
        private final String name;
        private final Meter.Type type;
        private String description = "";
        private String unit = "";
        private Map<String, String> baseTags = Map.of();
        private String category = "custom";
        private Instant createdAt = Instant.now();
        private Instant lastUpdatedAt = Instant.now();
        private boolean enabled = true;
        private double samplingRate = 1.0;
        
        public Builder(String name, Meter.Type type) {
            this.name = Objects.requireNonNull(name, "Metric name cannot be null");
            this.type = Objects.requireNonNull(type, "Metric type cannot be null");
        }
        
        public Builder(MetricMetadata metadata) {
            this.name = metadata.name;
            this.type = metadata.type;
            this.description = metadata.description;
            this.unit = metadata.unit;
            this.baseTags = metadata.baseTags;
            this.category = metadata.category;
            this.createdAt = metadata.createdAt;
            this.lastUpdatedAt = Instant.now();
            this.enabled = metadata.enabled;
            this.samplingRate = metadata.samplingRate;
        }
        
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }
        
        public Builder unit(String unit) {
            this.unit = unit != null ? unit : "";
            return this;
        }
        
        public Builder baseTags(Map<String, String> baseTags) {
            this.baseTags = baseTags != null ? baseTags : Map.of();
            return this;
        }
        
        public Builder category(String category) {
            this.category = category != null ? category : "custom";
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder samplingRate(double samplingRate) {
            if (samplingRate < 0.0 || samplingRate > 1.0) {
                throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0");
            }
            this.samplingRate = samplingRate;
            return this;
        }
        
        public MetricMetadata build() {
            return new MetricMetadata(this);
        }
    }
}