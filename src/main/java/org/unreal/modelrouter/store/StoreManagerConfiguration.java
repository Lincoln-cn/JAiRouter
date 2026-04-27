package org.unreal.modelrouter.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.jpa.JpaStoreManager;

/**
 * StoreManager配置类 (v1.5.1: 使用 JPA)
 * 用于在Spring环境中配置和创建StoreManager Bean
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "store")
public class StoreManagerConfiguration {

    private String type = "jpa";
    private String path = "./config";

    /**
     * 创建StoreManager Bean (v1.5.1: 使用 JPA)
     * @param jpaStoreManager JPA 存储管理器
     * @return StoreManager实例
     */
    @Bean
    public StoreManager storeManager(final JpaStoreManager jpaStoreManager) {
        log.info("Initializing StoreManager with JPA (v1.5.1)");
        return jpaStoreManager;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }
}