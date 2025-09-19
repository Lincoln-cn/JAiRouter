package org.unreal.modelrouter.dto;

import org.unreal.modelrouter.model.ModelRouterProperties;

/**
 * {
 *     "instanceId":"test-model@http://test.example.com",
 *     "data": {
 *         "name": "test-model",
 *         "baseUrl": "http://www.example.com",
 *         "path": "/v1/chat/completions",
 *         "weight": 1
 *     }
 * }
 */
public class UpdateInstanceDTO {

    private String instanceId;
    private Data instance;

    public static class Data {
        private String name;
        private String baseUrl;
        private String path;
        private Integer weight;
        private String status; // 添加status字段

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
    
    /**
     * 构建新的实例ID
     * @return 新的实例ID
     */
    public String getNewInstanceId() {
        if (instance != null && instance.getName() != null && instance.getBaseUrl() != null) {
            return instance.getName() + "@" + instance.getBaseUrl();
        }
        return instanceId; // 如果无法构建新的ID，则返回原始ID
    }
}