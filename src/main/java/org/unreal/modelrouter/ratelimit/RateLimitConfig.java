// src/main/java/org/unreal/modelrouter/ratelimit/RateLimitConfig.java
package org.unreal.modelrouter.ratelimit;

/**
 * 限流配置类
 */
public class RateLimitConfig {
    private boolean enabled = false;  // 是否启用
    private String algorithm;         // 算法类型: token-bucket, leaky-bucket, sliding-window等
    private long capacity;            // 容量
    private long rate;                // 速率
    private String scope;             // 作用域: service, instance, client-ip等
    private String key;               // 限流键值（可选）

    // 默认构造函数
    public RateLimitConfig() {}

    // 构造函数
    public RateLimitConfig(String algorithm, long capacity, long rate, String scope) {
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.rate = rate;
        this.scope = scope;
    }

    // 从 ModelRouterProperties.RateLimitConfig 转换
    public static RateLimitConfig from(org.unreal.modelrouter.config.ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }

        RateLimitConfig rateLimitConfig = new RateLimitConfig();
        rateLimitConfig.enabled = true;
        rateLimitConfig.algorithm = config.getAlgorithm();
        rateLimitConfig.capacity = config.getCapacity() != null ? config.getCapacity() : 100L;
        rateLimitConfig.rate = config.getRate() != null ? config.getRate() : 10L;
        rateLimitConfig.scope = config.getScope();
        rateLimitConfig.key = config.getKey();
        return rateLimitConfig;
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
}
