package org.unreal.modelrouter.store.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.FileStoreManager;
import org.unreal.modelrouter.store.StoreManager;

import java.util.Map;

/**
 * 配置迁移服务
 * 用于将文件存储的配置迁移到H2数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "store.migration.enabled", havingValue = "true")
public class ConfigMigrationService {

    private final StoreManager storeManager;

    /**
     * 应用启动后自动执行迁移
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        log.info("Starting automatic configuration migration...");
        migrateFromFileToH2("./config");
    }

    /**
     * 从文件存储迁移到H2数据库
     * @param fileStoragePath 文件存储路径
     */
    public void migrateFromFileToH2(String fileStoragePath) {
        try {
            log.info("Starting migration from file storage: {}", fileStoragePath);
            
            // 创建文件存储管理器读取现有数据
            FileStoreManager fileStoreManager = new FileStoreManager(fileStoragePath);
            
            int migratedCount = 0;
            int errorCount = 0;
            
            // 遍历所有配置键
            for (String key : fileStoreManager.getAllKeys()) {
                try {
                    log.debug("Migrating config key: {}", key);
                    
                    // 获取最新配置
                    Map<String, Object> config = fileStoreManager.getConfig(key);
                    if (config != null && !config.isEmpty()) {
                        // 保存到H2数据库
                        storeManager.saveConfig(key, config);
                        migratedCount++;
                        log.debug("Successfully migrated config key: {}", key);
                    }
                    
                    // 迁移历史版本
                    for (Integer version : fileStoreManager.getConfigVersions(key)) {
                        Map<String, Object> versionConfig = fileStoreManager.getConfigByVersion(key, version);
                        if (versionConfig != null && !versionConfig.isEmpty()) {
                            storeManager.saveConfigVersion(key, versionConfig, version);
                            log.debug("Migrated version {} for key: {}", version, key);
                        }
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to migrate config key: {}", key, e);
                }
            }
            
            log.info("Migration completed. Migrated: {}, Errors: {}", migratedCount, errorCount);
            
        } catch (Exception e) {
            log.error("Migration failed", e);
            throw new RuntimeException("Failed to migrate configurations", e);
        }
    }
}
