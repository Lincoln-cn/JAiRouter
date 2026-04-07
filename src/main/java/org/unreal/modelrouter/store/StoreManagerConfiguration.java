package org.unreal.modelrouter.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.jpa.JpaStoreManager;

/**
 * StoreManager配置类 (v1.5.1: 使用 JPA 替代 R2DBC)
 * 用于在Spring环境中配置和创建StoreManager Bean
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "store")
public class StoreManagerConfiguration {

    private String type = "jpa";  // v1.5.1: 默认使用 JPA
    private String path = "./config";
    private boolean autoMerge = true;

    // JWT持久化相关配置
    private String fallbackStorage = "memory";

    // 数据库配置 (v1.5.1: 改为 JDBC URL)
    private String h2Url = "jdbc:h2:file:./data/config";

    // 迁移配置
    private MigrationConfig migration = new MigrationConfig();
    private SecurityMigrationConfig securityMigration = new SecurityMigrationConfig();

    @Autowired
    private JpaStoreManager jpaStoreManager;

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
     * 获取是否启用自动合并功能
     * @return 是否启用自动合并功能
     */
    public boolean isAutoMerge() {
        return autoMerge;
    }

    /**
     * 设置是否启用自动合并功能
     * @param autoMerge 是否启用自动合并功能
     */
    public void setAutoMerge(boolean autoMerge) {
        this.autoMerge = autoMerge;
    }

    /**
     * 获取JWT备用存储类型
     * @return JWT备用存储类型
     */
    public String getFallbackStorage() {
        return fallbackStorage;
    }

    /**
     * 设置JWT备用存储类型
     * @param fallbackStorage JWT备用存储类型
     */
    public void setFallbackStorage(String fallbackStorage) {
        this.fallbackStorage = fallbackStorage;
    }

    /**
     * 获取H2数据库URL
     * @return H2数据库URL
     */
    public String getH2Url() {
        return h2Url;
    }

    /**
     * 设置H2数据库URL
     * @param h2Url H2数据库URL
     */
    public void setH2Url(String h2Url) {
        this.h2Url = h2Url;
    }

    /**
     * 获取迁移配置
     * @return 迁移配置
     */
    public MigrationConfig getMigration() {
        return migration;
    }

    /**
     * 设置迁移配置
     * @param migration 迁移配置
     */
    public void setMigration(MigrationConfig migration) {
        this.migration = migration;
    }

    /**
     * 获取安全迁移配置
     * @return 安全迁移配置
     */
    public SecurityMigrationConfig getSecurityMigration() {
        return securityMigration;
    }

    /**
     * 设置安全迁移配置
     * @param securityMigration 安全迁移配置
     */
    public void setSecurityMigration(SecurityMigrationConfig securityMigration) {
        this.securityMigration = securityMigration;
    }

    /**
     * 迁移配置内部类
     */
    public static class MigrationConfig {
        private boolean enabled = true;
        private String sourcePath;
        private String targetPath;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }
    }

    /**
     * 安全迁移配置内部类
     */
    public static class SecurityMigrationConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 创建StoreManager Bean (v1.5.1: 使用 JPA)
     * @return StoreManager实例
     */
    @Bean
    @ConditionalOnMissingBean(StoreManager.class)
    public StoreManager storeManager() {
        try {
            log.info("Initializing StoreManager with JPA (v1.5.1)");
            log.info("StoreManager type: {}", type);

            StoreManager storeManager;

            if ("jpa".equalsIgnoreCase(type) || "h2".equalsIgnoreCase(type)) {
                // v1.5.1: 使用 JPA StoreManager
                log.info("Creating JPA StoreManager");
                storeManager = jpaStoreManager;
            } else if ("file".equalsIgnoreCase(type)) {
                storeManager = StoreManagerFactory.createFileStoreManager(path);
            } else if ("memory".equalsIgnoreCase(type)) {
                storeManager = StoreManagerFactory.createMemoryStoreManager();
            } else {
                log.warn("Unknown storage type: {}, falling back to file storage", type);
                storeManager = StoreManagerFactory.createFileStoreManager(path);
            }

            log.info("Successfully initialized StoreManager: {}", storeManager.getClass().getSimpleName());
            return storeManager;

        } catch (Exception e) {
            log.error("Failed to initialize primary storage: {}", e.getMessage(), e);
            log.warn("Falling back to memory storage");
            return StoreManagerFactory.createMemoryStoreManager();
        }
    }
}
