package org.unreal.modelrouter.jpa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移服务
 * 在应用启动完成后自动执行必要的数据库结构迁移
 * 使用 ApplicationRunner 确保 Hibernate ddl-auto 先执行完成
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)  // 确保在其他 ApplicationRunner 之前执行
public class DatabaseMigrationService implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 应用启动完成后执行数据库迁移
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting database migration check...");

        try {
            // 迁移1: 修改 security_blacklist 表的 expires_at 列允许 NULL
            migrateSecurityBlacklistExpiresAt();

            log.info("Database migration completed successfully");
        } catch (Exception e) {
            log.error("Database migration failed: {}", e.getMessage(), e);
            // 不抛出异常，避免影响应用启动
        }
    }

    /**
     * 迁移 security_blacklist 表的 expires_at 列
     * 将 NOT NULL 改为 NULL，支持永久黑名单
     */
    private void migrateSecurityBlacklistExpiresAt() {
        String tableName = "security_blacklist";
        String columnName = "expires_at";

        log.info("Attempting migration for {}.{}...", tableName, columnName);

        // 直接尝试执行 ALTER TABLE，跳过前置检查
        // H2 MySQL模式下的 INFORMATION_SCHEMA 查询可能不可靠
        
        boolean migrated = false;
        String lastError = null;

        // 尝试多种 SQL 语法
        String[] sqlStatements = {
            // H2 语法 1: ALTER COLUMN ... DROP NOT NULL
            "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP NOT NULL",
            // H2 语法 2: ALTER COLUMN ... SET NULL  
            "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " SET NULL",
            // MySQL 语法: MODIFY COLUMN ... NULL
            "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " TIMESTAMP NULL",
            // 标准 SQL 语法: ALTER COLUMN ... DROP NOT NULL
            "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP CONSTRAINT NOT_NULL"
        };

        for (String sql : sqlStatements) {
            try {
                log.info("Trying: {}", sql);
                jdbcTemplate.execute(sql);
                log.info("Successfully migrated {}.{} with: {}", tableName, columnName, sql);
                migrated = true;
                break;
            } catch (Exception e) {
                lastError = e.getMessage();
                log.debug("SQL failed: {} - Error: {}", sql, lastError);
                
                // 检查是否是表不存在的情况（真正的错误）
                if (lastError != null && 
                    (lastError.contains("Table \"") && lastError.contains("\" not found") ||
                     lastError.contains("Table \"") && lastError.contains("\" doesn't exist"))) {
                    log.info("Table {} does not exist in database, skipping migration", tableName);
                    return;
                }
            }
        }

        if (!migrated) {
            // 如果所有语法都失败，可能是列已经允许 NULL 或其他问题
            if (lastError != null && lastError.contains("already")) {
                log.info("Column {}.{} may already allow NULL, migration not needed", tableName, columnName);
            } else {
                log.warn("All migration attempts failed for {}.{}: {}", tableName, columnName, lastError);
            }
        }
    }
}