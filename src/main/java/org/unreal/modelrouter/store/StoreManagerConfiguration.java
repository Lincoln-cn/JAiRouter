package org.unreal.modelrouter.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * StoreManager配置类
 * 用于在Spring环境中配置和创建StoreManager Bean
 */
@Configuration
@ConfigurationProperties(prefix = "store")
public class StoreManagerConfiguration {

    private String type = "file";
    private String path = "./config-store";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Bean
    @ConditionalOnMissingBean(StoreManager.class)
    public StoreManager storeManager() {
        return StoreManagerFactory.createStoreManager(type, path);
    }
}
