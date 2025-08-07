package org.unreal.modelrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "model")
public class ModelRouterProperties {
    private LoadBalanceConfig loadBalance = new LoadBalanceConfig();
    private String adapter = "normal";
    private Map<String, ServiceConfig> services;
    private RateLimitConfig rateLimit = new RateLimitConfig(); // 全局限流配置

    public LoadBalanceConfig getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(LoadBalanceConfig loadBalance) {
        this.loadBalance = loadBalance;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services;
    }

    public RateLimitConfig getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitConfig rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class ServiceConfig {
        private LoadBalanceConfig loadBalance;
        private List<ModelInstance> instances;
        private String adapter;
        private RateLimitConfig rateLimit; // 服务级别限流配置

        public LoadBalanceConfig getLoadBalance() {
            return loadBalance;
        }

        public void setLoadBalance(LoadBalanceConfig loadBalance) {
            this.loadBalance = loadBalance;
        }

        public List<ModelInstance> getInstances() {
            return instances;
        }

        public void setInstances(List<ModelInstance> instances) {
            this.instances = instances;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public RateLimitConfig getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(RateLimitConfig rateLimit) {
            this.rateLimit = rateLimit;
        }
    }

    public static class LoadBalanceConfig {
        private String type = "random";
        private String hashAlgorithm = "md5"; // 注意这里要用驼峰命名

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        public void setHashAlgorithm(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
        }
    }

    public static class ModelInstance {
        private String name;
        private String baseUrl; // 注意驼峰命名
        private String path;
        private int weight = 1;
        private RateLimitConfig rateLimit; // 实例级别限流配置

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public RateLimitConfig getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(RateLimitConfig rateLimit) {
            this.rateLimit = rateLimit;
        }

        // 获取实例唯一标识
        public String getInstanceId() {
            return name + "@" + baseUrl;
        }
    }

    // 限流配置类
    public static class RateLimitConfig {
        private Boolean enabled = false;     // 是否启用限流
        private String algorithm = "token-bucket"; // 算法类型: token-bucket, leaky-bucket, sliding-window等
        private Long capacity = 100L;        // 容量
        private Long rate = 10L;             // 速率
        private String scope = "service";    // 作用域: service, instance, client-ip等
        private String key;                  // 限流键值（可选）

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public Long getCapacity() {
            return capacity;
        }

        public void setCapacity(Long capacity) {
            this.capacity = capacity;
        }

        public Long getRate() {
            return rate;
        }

        public void setRate(Long rate) {
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
}
