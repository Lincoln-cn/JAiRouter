package org.unreal.modelrouter.ratelimit;

/**
 * 简化的限流配置类
 * 移除了冗余的from方法，简化构造和使用
 */
public class RateLimitConfig {
    private boolean enabled = true;
    private String algorithm;
    private long capacity;
    private long rate;
    private String scope;
    private String key;

    /**
     * 默认构造函数
     */
    public RateLimitConfig() {
        this.enabled = false;
    }

    /**
     * 完整构造函数
     */
    public RateLimitConfig(String algorithm, long capacity, long rate, String scope) {
        this.enabled = true;
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.rate = rate;
        this.scope = scope;
    }

    /**
     * 建造者模式构造
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String algorithm = "token-bucket";
        private long capacity = 100L;
        private long rate = 10L;
        private String scope = "service";
        private String key;

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder rate(long rate) {
            this.rate = rate;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public RateLimitConfig build() {
            RateLimitConfig config = new RateLimitConfig(algorithm, capacity, rate, scope);
            config.key = this.key;
            return config;
        }
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRate() {
        return rate;
    }

    public void setRate(long rate) {
        this.rate = rate;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 验证配置的有效性
     */
    public boolean isValid() {
        return enabled &&
                algorithm != null &&
                capacity > 0 &&
                rate > 0 &&
                scope != null;
    }

    /**
     * 复制配置
     */
    public RateLimitConfig copy() {
        RateLimitConfig copy = new RateLimitConfig(algorithm, capacity, rate, scope);
        copy.enabled = this.enabled;
        copy.key = this.key;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("RateLimitConfig{enabled=%s, algorithm='%s', capacity=%d, rate=%d, scope='%s', key='%s'}",
                enabled, algorithm, capacity, rate, scope, key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RateLimitConfig that = (RateLimitConfig) o;

        if (enabled != that.enabled) return false;
        if (capacity != that.capacity) return false;
        if (rate != that.rate) return false;
        if (!java.util.Objects.equals(algorithm, that.algorithm)) return false;
        if (!java.util.Objects.equals(scope, that.scope)) return false;
        return java.util.Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + (algorithm != null ? algorithm.hashCode() : 0);
        result = 31 * result + (int) (capacity ^ (capacity >>> 32));
        result = 31 * result + (int) (rate ^ (rate >>> 32));
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        result = 31 * result + (key != null ? key.hashCode() : 0);
        return result;
    }
}