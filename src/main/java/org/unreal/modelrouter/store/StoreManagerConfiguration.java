package org.unreal.modelrouter.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * StoreManager配置类
 * 用于在Spring环境中配置和创建StoreManager Bean
 */
@Configuration
@ConfigurationProperties(prefix = "store")
public class StoreManagerConfiguration {

    private String type = "file";
    private String path = "./config-store";

    /**
     * 获取存储类型
     * @return 存储类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置存储类型
     * @param type 存储类型
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * 获取存储路径
     * @return 存储路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置存储路径
     * @param path 存储路径
     */
    public void setPath(final String path) {
        this.path = path;
    }

    /**
     * 创建StoreManager Bean
     * @return StoreManager实例
     */
    @Bean
    @ConditionalOnMissingBean(StoreManager.class)
    public StoreManager storeManager() {
        return StoreManagerFactory.createStoreManager(type, path);
    }
}
