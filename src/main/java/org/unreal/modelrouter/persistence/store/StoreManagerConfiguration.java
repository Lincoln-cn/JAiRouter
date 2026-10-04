package org.unreal.modelrouter.persistence.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.unreal.modelrouter.persistence.jpa.JpaStoreManager;

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
     *
     * <p>{@code @Primary}（issue #196）：本 bean 与 {@code JpaStoreManager}（组件名
     * {@code jpaStoreManager}）是**同一个实例**的两个 bean 名，类型相同。此前未标 primary，
     * 未限定注入能否解析取决于**参数名是否恰好等于某个 bean 名** —— 参数名不匹配的注入点
     * （例如开启 jwt.persistence.redis.enabled 后被装配的
     * {@code RedisJwtTokenPersistenceServiceImpl}）会直接报"候选不唯一"而启动失败。
     */
    @Bean
    @Primary
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