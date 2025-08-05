package org.unreal.modelrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "model")
public class ModelRouterProperties {
    private LoadBalanceConfig loadBalance = new LoadBalanceConfig();
    private String adapter = "normal";
    private Map<String, ServiceConfig> services; // 直接使用Map，不需要嵌套的CapabilityConfig

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

    public static class ServiceConfig {
        private LoadBalanceConfig loadBalance;
        private List<ModelInstance> instances;
        private String adapter;

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
    }
}