package org.unreal.modelrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "model")
public class ModelRouterProperties {

    private Map<String, ServiceConfig> services;

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services;
    }

    public static class ServiceConfig {
        private LoadBalanceConfig loadBalance;
        private List<ModelInstance> instances;

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
    }

    public static class LoadBalanceConfig {
        private String type = "random"; // 默认随机策略
        private String hashAlgorithm = "md5"; // IP Hash 使用的哈希算法

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
        private String baseUrl;
        private String path;
        private int weight = 1; // 权重，默认为1

        public ModelInstance() {
        }

        public ModelInstance(String name, String baseUrl, String path) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.path = path;
        }

        public ModelInstance(String name, String baseUrl, String path, int weight) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.path = path;
            this.weight = weight;
        }

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

        @Override
        public String toString() {
            return "ModelInstance{" +
                    "name='" + name + '\'' +
                    ", baseUrl='" + baseUrl + '\'' +
                    ", path='" + path + '\'' +
                    ", weight=" + weight +
                    '}';
        }
    }
}