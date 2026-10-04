package org.unreal.modelrouter.persistence.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
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
 *   <li>列类型按数据库方言选择（CLOB 类：H2=CLOB / MySQL=LONGTEXT / PostgreSQL=TEXT）</li>
 *   <li>JSON 列（issue #190）：PostgreSQL=jsonb / MySQL、H2=json，并对旧库已有的非 JSON 列做收敛</li>
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
            // issue #190：JSON 列不再补成 CLOB —— 那是与新库不一致的物理类型（PG 上新库曾是 json、
            // 旧库升级路径补成 CLOB→TEXT）。这里登记为 "JSON"，由方言产出（PG=jsonb / 其它=json），
            // 并对已存在但类型不符的列做收敛。
            new TableMigration("service_instance", List.of(
                    new ColumnDef("tags", "JSON"),
                    new ColumnDef("headers", "JSON")
            ))
    );

    private final JdbcTemplate jdbcTemplate;
    private final String clobType;
    private final String jsonType;
    private final boolean postgres;

    public CompatibilitySchemaMigrator(final DataSource dataSource, final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        final String product = detectProduct(dataSource);
        this.clobType = clobTypeFor(product);
        this.jsonType = jsonTypeFor(product);
        this.postgres = product.contains("postgres");
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
                convergeJsonColumn(table, column);
                continue;
            }
            String type = resolveColumnType(column);
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
     * 读取数据库产品名（小写）；失败返回空串
     */
    private static String detectProduct(final DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            log.warn("Schema 迁移: 无法识别数据库类型: {}", e.getMessage());
            return "";
        }
    }

    /**
     * CLOB 等价类型（H2=CLOB / MySQL=LONGTEXT / PostgreSQL=TEXT）
     */
    static String clobTypeFor(final String product) {
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "LONGTEXT";
        }
        if (product.contains("postgres")) {
            return "TEXT";
        }
        return "CLOB";
    }

    /**
     * JSON 列的物理类型（issue #190）：PostgreSQL=jsonb，其余（H2 / MySQL）=json。
     *
     * <p>取 jsonb 而不是 TEXT 的原因：实体用 {@code @JdbcTypeCode(SqlTypes.JSON)} 映射这两列，
     * PostgreSQL 方言下 JSON 的参数绑定是 json/jsonb 语义，落到 text 列会类型不匹配；
     * 且 jsonb 正是 Hibernate 在 PG 上为 JSON 类型选定的默认物理类型 —— 把手工补列/收敛
     * 对齐到框架自己的选择，才能让后续 {@code ddl-auto: validate} 对得上。
     */
    static String jsonTypeFor(final String product) {
        return product.contains("postgres") ? "jsonb" : "json";
    }

    /**
     * 按登记的类型标记解析出实际列类型（"CLOB"/"JSON" 是标记，不是字面类型）
     */
    private String resolveColumnType(final ColumnDef column) {
        if ("CLOB".equals(column.type())) {
            return clobType;
        }
        if ("JSON".equals(column.type())) {
            return jsonType;
        }
        return column.type();
    }

    /**
     * 把旧库里类型不符的 JSON 列收敛为方言约定的 JSON 类型（issue #190）。
     *
     * <p>旧库的 {@code tags}/{@code headers} 可能是早期兼容迁移补的 TEXT/CLOB；不收敛的话，
     * 将来切 {@code ddl-auto: validate} 会因物理类型与实体声明不一致而启动失败。
     *
     * <p>仅 PostgreSQL 自动收敛（{@code ALTER COLUMN ... TYPE ... USING col::type} 是 PG 语法）；
     * 其它库（H2 开发库）只告警，请删除重建。
     */
    private void convergeJsonColumn(final String table, final ColumnDef column) {
        if (!"JSON".equals(column.type())) {
            return;
        }
        try {
            String current = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?",
                    String.class, table.toLowerCase(), column.name().toLowerCase());
            if (current == null || current.equalsIgnoreCase(jsonType)) {
                return;
            }
            if (!postgres) {
                log.warn("Schema 迁移: 表 {} 列 {} 当前为 {}，与约定的 {} 不一致；"
                                + "非 PostgreSQL 库不自动收敛，请删除重建该库",
                        table, column.name(), current, jsonType);
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column.name()
                    + " TYPE " + jsonType + " USING " + column.name() + "::" + jsonType);
            log.info("Schema 迁移: 表 {} 列 {} 已收敛为 {}（原 {}）", table, column.name(), jsonType, current);
        } catch (Exception e) {
            log.warn("Schema 迁移: 表 {} 列 {} 收敛为 {} 失败: {}",
                    table, column.name(), jsonType, e.getMessage());
        }
    }

    /** 表迁移定义 */
    private record TableMigration(String table, List<ColumnDef> columns) {
    }

    /** 列定义（H2 类型） */
    private record ColumnDef(String name, String type) {
    }
}
