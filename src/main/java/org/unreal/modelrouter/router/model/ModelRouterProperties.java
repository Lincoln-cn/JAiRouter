package org.unreal.modelrouter.router.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.unreal.modelrouter.common.util.SecurityUtils;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "model")
public class ModelRouterProperties {
    private LoadBalanceConfig loadBalance = new LoadBalanceConfig();
    private String adapter = "normal";
    private Map<String, ServiceConfig> services;
    private RateLimitConfig rateLimit = new RateLimitConfig(); // 全局限流配置
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig(); // 全局熔断器配置
    private FallbackConfig fallback = new FallbackConfig(); // 全局降级配置

    public LoadBalanceConfig getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(final LoadBalanceConfig loadBalance) {
        this.loadBalance = loadBalance;
    }

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(final String adapter) {
        this.adapter = adapter;
    }

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(final Map<String, ServiceConfig> services) {
        this.services = services;
    }

    public RateLimitConfig getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(final RateLimitConfig rateLimit) {
        this.rateLimit = rateLimit;
    }

    public CircuitBreakerConfig getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(final CircuitBreakerConfig circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }
    
    public FallbackConfig getFallback() {
        return fallback;
    }
    
    public void setFallback(final FallbackConfig fallback) {
        this.fallback = fallback;
    }
    public static class ServiceConfig {
        private LoadBalanceConfig loadBalance;
        private List<ModelInstance> instances;
        private String adapter;
        private RateLimitConfig rateLimit; // 服务级别限流配置
        private CircuitBreakerConfig circuitBreaker; // 服务级别熔断器配置
        private FallbackConfig fallback; // 服务级别降级配置

        public LoadBalanceConfig getLoadBalance() {
            return loadBalance;
        }

        public void setLoadBalance(final LoadBalanceConfig loadBalance) {
            this.loadBalance = loadBalance;
        }

        public List<ModelInstance> getInstances() {
            return instances;
        }

        public void setInstances(final List<ModelInstance> instances) {
            this.instances = instances;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(final String adapter) {
            this.adapter = adapter;
        }

        public RateLimitConfig getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(final RateLimitConfig rateLimit) {
            this.rateLimit = rateLimit;
        }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(final CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
        
        public FallbackConfig getFallback() {
            return fallback;
        }
        
        public void setFallback(final FallbackConfig fallback) {
            this.fallback = fallback;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoadBalanceConfig {
        private String type = "random";
        private String hashAlgorithm = "md5"; // 注意这里要用驼峰命名
        private Integer virtualNodes = 150; // 一致性哈希虚拟节点数

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        public void setHashAlgorithm(final String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
        }

        public Integer getVirtualNodes() {
            return virtualNodes;
        }

        public void setVirtualNodes(final Integer virtualNodes) {
            this.virtualNodes = virtualNodes;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelInstance {
        private String id; // 实例唯一标识
        private String name;
        private String baseUrl; // 注意驼峰命名
        private String path;
        private int weight = 1;
        private String status = "active"; // 添加状态字段，默认为active
        private String instanceId; // 添加唯一ID字段
        private String adapter; // 适配器配置
        private Map<String, String> headers; // 添加请求头配置
        private RateLimitConfig rateLimit; // 实例级别限流配置
        private CircuitBreakerConfig circuitBreaker; // 实例级别熔断器配置
        private Boolean healthy = true; // 实例健康状态

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPath() {
            return path;
        }

        public void setPath(final String path) {
            this.path = path;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(final int weight) {
            this.weight = weight;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(final String status) {
            this.status = status;
        }
        
        public String getInstanceId() {
            // v2.x: 直接返回 instanceId，不再动态生成
            // 动态生成会导致每次重启应用熔断器 key 不一致
            // 如果 instanceId 为空，调用方应使用 baseUrl 或其他唯一标识
            return instanceId;
        }
        
        public void setInstanceId(final String instanceId) {
            this.instanceId = instanceId;
        }

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public Boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(final Boolean healthy) {
            this.healthy = healthy;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(final String adapter) {
            this.adapter = adapter;
        }

        public RateLimitConfig getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(final RateLimitConfig rateLimit) {
            this.rateLimit = rateLimit;
        }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(final CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(final Map<String, String> headers) {
            this.headers = headers;
        }
    }

    // 限流配置类
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RateLimitConfig {
        private Boolean enabled = false;     // 是否启用限流
        private String algorithm = "token-bucket"; // 算法类型: token-bucket, leaky-bucket, sliding-window等
        private Long capacity = 100L;        // 容量
        private Long rate = 10L;             // 速率
        private String scope = "service";    // 作用域: service, instance, client-ip等
        private String key;                  // 限流键值（可选）
        private Boolean clientIpEnable = false; // 是否启用客户端IP级别的限流

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(final Boolean enabled) {
            this.enabled = enabled;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(final String algorithm) {
            this.algorithm = algorithm;
        }

        public Long getCapacity() {
            return capacity;
        }

        public void setCapacity(final Long capacity) {
            this.capacity = capacity;
        }

        public Long getRate() {
            return rate;
        }

        public void setRate(final Long rate) {
            this.rate = rate;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(final String scope) {
            this.scope = scope;
        }

        public String getKey() {
            return key;
        }

        public void setKey(final String key) {
            this.key = key;
        }

        public Boolean getClientIpEnable() {
            return clientIpEnable;
        }

        public void setClientIpEnable(final Boolean clientIpEnable) {
            this.clientIpEnable = clientIpEnable;
        }

        public org.unreal.modelrouter.router.ratelimit.RateLimitConfig covertTo() {
            if (!Boolean.TRUE.equals(this.enabled)) {
                return new org.unreal.modelrouter.router.ratelimit.RateLimitConfig();
            }
            return org.unreal.modelrouter.router.ratelimit.RateLimitConfig.builder()
                    .algorithm(this.algorithm)
                    .capacity(this.capacity)
                    .rate(this.rate)
                    .scope(this.scope)
                    .key(this.key)
                    .build();
        }
    }

    // 熔断器配置类
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CircuitBreakerConfig {
        private Boolean enabled = false;         // 是否启用熔断器
        private Integer failureThreshold = 5;   // 失败阈值
        private Long timeout = 60000L;          // 超时时间(毫秒)
        private Integer successThreshold = 2;   // 成功阈值

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(final Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(final Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Long getTimeout() {
            return timeout;
        }

        public void setTimeout(final Long timeout) {
            this.timeout = timeout;
        }

        public Integer getSuccessThreshold() {
            return successThreshold;
        }

        public void setSuccessThreshold(final Integer successThreshold) {
            this.successThreshold = successThreshold;
        }
    }
    
    // 降级配置类
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FallbackConfig {
        private Boolean enabled = false;           // 是否启用降级
        private String strategy = "default";       // 降级策略: default, cache等
        private Integer cacheSize = 100;           // 缓存大小（仅在strategy为cache时有效）
        private Long cacheTtl = 300000L;           // 缓存过期时间（毫秒）

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(final Boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(final String strategy) {
            this.strategy = strategy;
        }

        public Integer getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(final Integer cacheSize) {
            this.cacheSize = cacheSize;
        }

        public Long getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(final Long cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }
}