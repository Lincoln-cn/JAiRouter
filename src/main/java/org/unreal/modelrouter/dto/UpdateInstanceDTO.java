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

        public ModelRouterProperties.ModelInstance covertTo() {
            ModelRouterProperties.ModelInstance modelInstance = new ModelRouterProperties.ModelInstance();
            modelInstance.setName(name);
            modelInstance.setBaseUrl(baseUrl);
            modelInstance.setPath(path);
            modelInstance.setWeight(weight);
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
