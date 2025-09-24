package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.unreal.modelrouter.model.ModelRouterProperties;

/**
 * {
 * "instanceId":"test-model@http://test.example.com",
 * "data": {
 * "name": "test-model",
 * "baseUrl": "http://www.example.com",
 * "path": "/v1/chat/completions",
 * "weight": 1
 * }
 * }
 */
public class UpdateInstanceDTO {

    private String instanceId;
    private Data instance;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {
        private String name;
        private String baseUrl;
        private String path;
        private Integer weight;
        private String status; // 添加status字段
        private ModelRouterProperties.RateLimitConfig rateLimit; // 使用ModelRouterProperties中的限流配置字段
        private ModelRouterProperties.CircuitBreakerConfig circuitBreaker; // 使用ModelRouterProperties中的熔断配置字段

        // Getters and setters
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

        public Integer getWeight() {
            return weight;
        }

        public void setWeight(Integer weight) {
            this.weight = weight;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public ModelRouterProperties.RateLimitConfig getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(ModelRouterProperties.RateLimitConfig rateLimit) {
            this.rateLimit = rateLimit;
        }

        public ModelRouterProperties.CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(ModelRouterProperties.CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public ModelRouterProperties.ModelInstance covertTo() {
            ModelRouterProperties.ModelInstance modelInstance = new ModelRouterProperties.ModelInstance();
            modelInstance.setName(name);
            modelInstance.setBaseUrl(baseUrl);
            modelInstance.setPath(path);
            modelInstance.setWeight(weight);
            // 添加status字段设置
            if (status != null) {
                modelInstance.setStatus(status);
            }
            // 添加限流配置设置
            if (rateLimit != null) {
                ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
                // 只有当字段不为null时才设置值
                if (rateLimit.getEnabled() != null) {
                    rateLimitConfig.setEnabled(rateLimit.getEnabled());
                }
                if (rateLimit.getAlgorithm() != null) {
                    rateLimitConfig.setAlgorithm(rateLimit.getAlgorithm());
                }
                if (rateLimit.getCapacity() != null) {
                    rateLimitConfig.setCapacity(rateLimit.getCapacity());
                }
                if (rateLimit.getRate() != null) {
                    rateLimitConfig.setRate(rateLimit.getRate());
                }
                if (rateLimit.getScope() != null) {
                    rateLimitConfig.setScope(rateLimit.getScope());
                }
                if (rateLimit.getKey() != null) {
                    rateLimitConfig.setKey(rateLimit.getKey());
                }
                if (rateLimit.getClientIpEnable() != null) {
                    rateLimitConfig.setClientIpEnable(rateLimit.getClientIpEnable());
                }
                modelInstance.setRateLimit(rateLimitConfig);
            } else {
                // 即使rateLimit为null，也要创建一个空的配置对象，以避免验证错误
                modelInstance.setRateLimit(new ModelRouterProperties.RateLimitConfig());
            }
            // 添加熔断配置设置
            if (circuitBreaker != null) {
                ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig = new ModelRouterProperties.CircuitBreakerConfig();
                // 只有当字段不为null时才设置值
                if (circuitBreaker.getEnabled() != null) {
                    circuitBreakerConfig.setEnabled(circuitBreaker.getEnabled());
                }
                if (circuitBreaker.getFailureThreshold() != null) {
                    circuitBreakerConfig.setFailureThreshold(circuitBreaker.getFailureThreshold());
                }
                if (circuitBreaker.getTimeout() != null) {
                    circuitBreakerConfig.setTimeout(circuitBreaker.getTimeout());
                }
                if (circuitBreaker.getSuccessThreshold() != null) {
                    circuitBreakerConfig.setSuccessThreshold(circuitBreaker.getSuccessThreshold());
                }
                modelInstance.setCircuitBreaker(circuitBreakerConfig);
            } else {
                // 即使circuitBreaker为null，也要创建一个空的配置对象，以避免验证错误
                modelInstance.setCircuitBreaker(new ModelRouterProperties.CircuitBreakerConfig());
            }
            return modelInstance;
        }
    }

    // Getters and setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Data getInstance() {
        return instance;
    }

    public void setInstance(Data instance) {
        this.instance = instance;
    }
}