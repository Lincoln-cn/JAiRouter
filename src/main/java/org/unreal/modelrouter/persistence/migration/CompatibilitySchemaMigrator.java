package org.unreal.modelrouter.persistence.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库 schema 兼容性迁移器（v2.9.4）
 *
 * <p>解决旧版本升级（≤v2.9.1）后实体新增列未落库的问题：
 * H2 在 {@code DATABASE_TO_UPPER=FALSE} + {@code MODE=MySQL} 配置下，
 * Hibernate 6.6 的 {@code ddl-auto: update} 因元数据大小写匹配异常
 * 无法给已存在的表追加新列（表现为 CREATE 报 "Table already exists" 后跳过），
 * 导致升级后实体查询报 "Column xxx not found" 500。
 *
 * <p>本组件在应用启动完成、JPA 建表之后执行幂等迁移：
 * <ul>
 *   <li>检查目标表是否已存在（不存在则跳过，新库由 JPA 建表）</li>
 *   <li>查询 INFORMATION_SCHEMA 现有列，对缺失列执行 {@code ALTER TABLE ... ADD COLUMN}</li>
 *   <li>列类型按数据库方言选择（H2=CLOB / MySQL=LONGTEXT / PostgreSQL=TEXT）</li>
 *   <li>重复启动安全（存在性检查保证幂等）</li>
 * </ul>
 *
 * <p>新增实体列时，在 {@link #MIGRATIONS} 清单中登记即可自动兼容旧库升级。
 *
 * @author JAiRouter Team
 * @since 2.9.4
 */
@Slf4j
@Component
public class CompatibilitySchemaMigrator implements ApplicationRunner {

    /** 迁移清单：表名 + 缺失时需补的列定义（名称 + H2 类型） */
    private static final List<TableMigration> MIGRATIONS = List.of(
            new TableMigration("api_call_history", List.of(
                    new ColumnDef("record_level", "varchar(20)"),
                    new ColumnDef("request_body_encrypted", "CLOB"),
                    new ColumnDef("response_body_encrypted", "CLOB")
            )),
            // v2.9.7: ServiceInstanceEntity 新增 tags JSON 列,旧库需补齐
            new TableMigration("service_instance", List.of(
                    new ColumnDef("tags", "CLOB")
            ))
    );

    private final JdbcTemplate jdbcTemplate;
    private final String clobType;

    public CompatibilitySchemaMigrator(final DataSource dataSource, final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.clobType = resolveClobType(dataSource);
    }

    @Override
    public void run(final ApplicationArguments args) {
        for (TableMigration migration : MIGRATIONS) {
            migrateTable(migration);
        }
    }

    private void migrateTable(final TableMigration migration) {
        String table = migration.table();
        if (!tableExists(table)) {
            log.debug("Schema 迁移: 表 {} 不存在（新库由 JPA 建表），跳过", table);
            return;
        }
        Set<String> existing = existingColumns(table);
        List<String> applied = new ArrayList<>();
        for (ColumnDef column : migration.columns()) {
            if (existing.contains(column.name().toLowerCase())) {
                continue;
            }
            String type = column.name().equals("record_level") ? column.type()
                    : column.type().equals("CLOB") ? clobType : column.type();
            try {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column.name() + " " + type);
                applied.add(column.name() + " " + type);
                log.info("Schema 迁移: 表 {} 已补列 {} {}", table, column.name(), type);
            } catch (Exception e) {
                // 并发启动/重复执行等场景下 ADD 失败不阻断应用
                log.warn("Schema 迁移: 表 {} 补列 {} 失败: {}", table, column.name(), e.getMessage());
            }
        }
        if (applied.isEmpty()) {
            log.debug("Schema 迁移: 表 {} 列已齐全，无需迁移", table);
        }
    }

    private boolean tableExists(final String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                    Integer.class, table.toLowerCase());
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Schema 迁移: 检查表 {} 存在性失败: {}", table, e.getMessage());
            return false;
        }
    }

    private Set<String> existingColumns(final String table) {
        try {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = ?",
                    String.class, table.toLowerCase());
            Set<String> result = new HashSet<>();
            for (String column : columns) {
                result.add(column.toLowerCase());
            }
            return result;
        } catch (Exception e) {
            log.warn("Schema 迁移: 读取表 {} 列清单失败: {}", table, e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 按数据库产品选择 CLOB 等价类型（H2=CLOB / MySQL=LONGTEXT / PostgreSQL=TEXT）
     */
    private static String resolveClobType(final DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName().toLowerCase();
            if (product.contains("mysql") || product.contains("mariadb")) {
                return "LONGTEXT";
            }
            if (product.contains("postgres")) {
                return "TEXT";
            }
            return "CLOB";
        } catch (Exception e) {
            log.warn("Schema 迁移: 无法识别数据库类型，默认使用 CLOB: {}", e.getMessage());
            return "CLOB";
        }
    }

    /** 表迁移定义 */
    private record TableMigration(String table, List<ColumnDef> columns) {
    }

    /** 列定义（H2 类型） */
    private record ColumnDef(String name, String type) {
    }
}
