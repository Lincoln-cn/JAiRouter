package org.unreal.modelrouter.monitoring.registry.model;

import io.micrometer.core.instrument.Meter;

import java.util.Map;
import java.util.Objects;

/**
 * 指标注册请求模型
 * 包含注册指标所需的所有信息
 */
public class MetricRegistrationRequest {
    
    private final String name;
    private final String description;
    private final Meter.Type type;
    private final String unit;
    private final Map<String, String> tags;
    private final String category;
    private final boolean enabled;
    private final double samplingRate;
    
    private MetricRegistrationRequest(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.unit = builder.unit;
        this.tags = Map.copyOf(builder.tags);
        this.category = builder.category;
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
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public String getCategory() {
        return category;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricRegistrationRequest that = (MetricRegistrationRequest) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) &&
               Objects.equals(tags, that.tags);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, type, tags);
    }
    
    @Override
    public String toString() {
        return "MetricRegistrationRequest{" +
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
        private Map<String, String> tags = Map.of();
        private String category = "custom";
        private boolean enabled = true;
        private double samplingRate = 1.0;
        
        public Builder(String name, Meter.Type type) {
            this.name = Objects.requireNonNull(name, "Metric name cannot be null");
            this.type = Objects.requireNonNull(type, "Metric type cannot be null");
        }
        
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }
        
        public Builder unit(String unit) {
            this.unit = unit != null ? unit : "";
            return this;
        }
        
        public Builder tags(Map<String, String> tags) {
            this.tags = tags != null ? tags : Map.of();
            return this;
        }
        
        public Builder tag(String key, String value) {
            if (key != null && value != null) {
                Map<String, String> newTags = new java.util.HashMap<>(this.tags);
                newTags.put(key, value);
                this.tags = newTags;
            }
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
        
        public MetricRegistrationRequest build() {
            return new MetricRegistrationRequest(this);
        }
    }
}