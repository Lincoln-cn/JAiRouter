package org.unreal.modelrouter.ratelimit;

/**
 * 简化的限流配置类
 * 移除了冗余的from方法，简化构造和使用
 */
public class RateLimitConfig {
    private boolean enabled;
    private String algorithm;
    private long capacity;
    private long rate;
    private String scope;
    private String key;
    private long warmUpPeriod = 600; // 预热期（秒），默认10分钟

    /**
     * 默认构造函数
     */
    public RateLimitConfig() {
        this.enabled = false;
    }

    /**
     * 完整构造函数
     */
    public RateLimitConfig(final String algorithm, final long capacity, final long rate, final String scope) {
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
        private long warmUpPeriod = 600L;

        /**
         * 设置算法
         * @param algorithmParam 算法
         * @return Builder实例
         */
        public Builder algorithm(final String algorithmParam) {
            this.algorithm = algorithmParam;
            return this;
        }

        /**
         * 设置容量
         * @param capacityParam 容量
         * @return Builder实例
         */
        public Builder capacity(final long capacityParam) {
            this.capacity = capacityParam;
            return this;
        }

        /**
         * 设置速率
         * @param rateParam 速率
         * @return Builder实例
         */
        public Builder rate(final long rateParam) {
            this.rate = rateParam;
            return this;
        }

        /**
         * 设置作用域
         * @param scopeParam 作用域
         * @return Builder实例
         */
        public Builder scope(final String scopeParam) {
            this.scope = scopeParam;
            return this;
        }

        /**
         * 设置键
         * @param keyParam 键
         * @return Builder实例
         */
        public Builder key(final String keyParam) {
            this.key = keyParam;
            return this;
        }

        /**
         * 设置预热期
         * @param warmUpPeriodParam 预热期
         * @return Builder实例
         */
        public Builder warmUpPeriod(final long warmUpPeriodParam) {
            this.warmUpPeriod = warmUpPeriodParam;
            return this;
        }

        /**
         * 构建RateLimitConfig实例
         * @return RateLimitConfig实例
         */
        public RateLimitConfig build() {
            RateLimitConfig config = new RateLimitConfig(algorithm, capacity, rate, scope);
            config.key = this.key;
            config.warmUpPeriod = this.warmUpPeriod;
            return config;
        }
    }

    /**
     * 检查是否启用
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     * @param enabled 是否启用
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取算法
     * @return 算法
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * 设置算法
     * @param algorithm 算法
     */
    public void setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * 获取容量
     * @return 容量
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * 设置容量
     * @param capacity 容量
     */
    public void setCapacity(final long capacity) {
        this.capacity = capacity;
    }

    /**
     * 获取速率
     * @return 速率
     */
    public long getRate() {
        return rate;
    }

    /**
     * 设置速率
     * @param rate 速率
     */
    public void setRate(final long rate) {
        this.rate = rate;
    }

    /**
     * 获取作用域
     * @return 作用域
     */
    public String getScope() {
        return scope;
    }

    /**
     * 设置作用域
     * @param scope 作用域
     */
    public void setScope(final String scope) {
        this.scope = scope;
    }

    /**
     * 获取键
     * @return 键
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置键
     * @param key 键
     */
    public void setKey(final String key) {
        this.key = key;
    }
    
    /**
     * 获取预热期
     * @return 预热期
     */
    public long getWarmUpPeriod() {
        return warmUpPeriod;
    }
    
    /**
     * 设置预热期
     * @param warmUpPeriod 预热期
     */
    public void setWarmUpPeriod(final long warmUpPeriod) {
        this.warmUpPeriod = warmUpPeriod;
    }

    /**
     * 验证配置的有效性
     * @return 配置是否有效
     */
    public boolean isValid() {
        return enabled
                && algorithm != null
                && capacity > 0
                && rate > 0
                && scope != null;
    }

    /**
     * 复制配置
     * @return 配置副本
     */
    public RateLimitConfig copy() {
        RateLimitConfig copy = new RateLimitConfig(algorithm, capacity, rate, scope);
        copy.enabled = this.enabled;
        copy.key = this.key;
        return copy;
    }

    /**
     * 转换为字符串表示
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return String.format("RateLimitConfig{enabled=%s, algorithm='%s', capacity=%d, rate=%d, scope='%s', key='%s'}",
                enabled, algorithm, capacity, rate, scope, key);
    }

    /**
     * 比较对象是否相等
     * @param o 比较对象
     * @return 是否相等
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RateLimitConfig that = (RateLimitConfig) o;

        if (enabled != that.enabled) {
            return false;
        }
        if (capacity != that.capacity) {
            return false;
        }
        if (rate != that.rate) {
            return false;
        }
        if (!java.util.Objects.equals(algorithm, that.algorithm)) {
            return false;
        }
        if (!java.util.Objects.equals(scope, that.scope)) {
            return false;
        }
        return java.util.Objects.equals(key, that.key);
    }

    /**
     * 计算哈希码
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + (algorithm != null ? algorithm.hashCode() : 0);
        result = 31 * result + Long.hashCode(capacity);
        result = 31 * result + Long.hashCode(rate);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        result = 31 * result + (key != null ? key.hashCode() : 0);
        return result;
    }
}