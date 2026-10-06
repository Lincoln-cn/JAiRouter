package org.unreal.modelrouter.persistence.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 两条路径的 schema 等价性（issue #192）。
 *
 * <p>要证明的是这件事：<b>空库从头建</b>与<b>老库经版本化迁移收敛</b>最终落在**同一套 schema**上。
 * 这是把「启动期条件式补丁」转成「确定序列脚本」后唯一真正的验证 —— 转错了就会出现
 * 「新库对、老库缺列」，而两者各自的单测都发现不了。</p>
 *
 * <p>三个 schema 各自跑一遍真实 Flyway（用 {@code classpath:db/migration/postgres} 里的生产脚本）：</p>
 * <ol>
 *   <li>{@code fresh}：空 schema，Flyway 从 V1 建起</li>
 *   <li>{@code legacy_missing}：先手工用 V1 建好，再删掉五个补丁列并把 {@code expires_at}
 *       改回 NOT NULL —— 即「≤v2.9.1 升级上来的库」的形态</li>
 *   <li>{@code legacy_divergent}：在上一类基础上，把 {@code tags} / {@code headers} 造成
 *       {@code text} —— 即 issue #190 说的「早期补成 TEXT」的类型分歧形态</li>
 * </ol>
 *
 * <p>后两者是非空 schema 且无历史表，正对应生产里的 {@code baseline-on-migrate=true} 路径：
 * Flyway 记一条基线 1、不重放 V1，然后执行 V2 收敛。</p>
 *
 * <p>门控与仓库既有的 {@code PostgresCompatibilityIntegrationTest} 一致（{@code PG_TEST=true}），
 * CI 里由 {@code java-tests.yml} 的「校验门控测试未被跳过」一并看住。</p>
 */
@EnabledIfEnvironmentVariable(named = "PG_TEST", matches = "true")
@DisplayName("Flyway 两条路径 schema 等价（PG_TEST=true 时执行，issue #192）")
class PostgresFlywayMigrationEquivalenceTest {

    private static final String URL =
            env("PG_TEST_URL", "jdbc:postgresql://127.0.0.1:15432/postgres");
    private static final String USERNAME = env("PG_TEST_USERNAME", "postgres");
    private static final String PASSWORD = env("PG_TEST_PASSWORD", "postgres");

    private static final String FRESH = "flyway_probe_fresh";
    private static final String LEGACY_MISSING = "flyway_probe_legacy_missing";
    private static final String LEGACY_DIVERGENT = "flyway_probe_legacy_divergent";
    private static final String HISTORY_TABLE = "flyway_schema_history";

    /** 「≤v2.9.1 升级上来的库」：五个补丁列缺失、expires_at 仍是 NOT NULL */
    private static final String UN_CONVERGE_MISSING = """
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS record_level;
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS request_body_encrypted;
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS response_body_encrypted;
            ALTER TABLE service_instance DROP COLUMN IF EXISTS tags;
            ALTER TABLE service_instance DROP COLUMN IF EXISTS headers;
            ALTER TABLE security_blacklist ALTER COLUMN expires_at SET NOT NULL;
            """;

    /** 同上，且 tags / headers 是早期补成的 text（类型分歧形态，issue #190） */
    private static final String UN_CONVERGE_DIVERGENT = """
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS record_level;
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS request_body_encrypted;
            ALTER TABLE api_call_history DROP COLUMN IF EXISTS response_body_encrypted;
            ALTER TABLE service_instance DROP COLUMN IF EXISTS tags;
            ALTER TABLE service_instance ADD COLUMN tags text;
            ALTER TABLE service_instance ALTER COLUMN headers TYPE text USING headers::text;
            ALTER TABLE security_blacklist ALTER COLUMN expires_at SET NOT NULL;
            """;

    @Test
    @DisplayName("空库与两类老库经 Flyway 收敛后 schema 完全一致")
    void bothPathsConvergeToTheSameSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            dropSchema(connection, FRESH);
            dropSchema(connection, LEGACY_MISSING);
            dropSchema(connection, LEGACY_DIVERGENT);

            migrateEmptySchema(connection, FRESH);
            migrateLegacySchema(connection, LEGACY_MISSING, UN_CONVERGE_MISSING);
            migrateLegacySchema(connection, LEGACY_DIVERGENT, UN_CONVERGE_DIVERGENT);

            Map<String, List<String>> fresh = snapshot(connection, FRESH);
            for (String legacy : List.of(LEGACY_MISSING, LEGACY_DIVERGENT)) {
                Map<String, List<String>> other = snapshot(connection, legacy);
                for (String section : fresh.keySet()) {
                    assertEquals(fresh.get(section), other.get(section),
                            "「" + FRESH + "」与「" + legacy + "」的 " + section + " 不一致");
                }
                // 反向也断言一次，避免只有一边为空时的假等价
                assertEquals(fresh.keySet(), other.keySet(), "快照分节不同");
            }

            // 迁移历史：空库 = V1 + V2 都是 SQL；老库 = 基线 1 + V2
            assertEquals(List.of("1|SQL|true", "2|SQL|true"), history(connection, FRESH),
                    "空库应顺序执行 V1、V2");
            for (String legacy : List.of(LEGACY_MISSING, LEGACY_DIVERGENT)) {
                assertEquals(List.of("1|BASELINE|true", "2|SQL|true"), history(connection, legacy),
                        "老库应记为基线 1 且不重放 V1，再执行 V2");
            }
        }
    }

    /**
     * 退出时必须清掉三个探针 schema，这不只是整洁问题：它们各自包含一套与 {@code public}
     * 同名的表，留下影子表会让同一库上运行的其它门控测试（如 {@code PostgresCompatibilityIntegrationTest}）
     * 读到不该读的对象而失败 —— 这是 issue #216 当时暴露出来的教训，清理仍是必须的。
     */
    @AfterAll
    static void dropProbeSchemas() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            for (String schema : List.of(FRESH, LEGACY_MISSING, LEGACY_DIVERGENT)) {
                dropSchema(connection, schema);
            }
        }
    }

    // ==================== 迁移执行 ====================

    /** 空 schema：Flyway 从 V1 建起（对应生产里连到全新数据库） */
    private static void migrateEmptySchema(final Connection connection, final String schema)
            throws SQLException {
        createSchema(connection, schema);
        flyway(schema).migrate();
    }

    /** 老库：先手工用 V1 建好并「退化」成改造前的形态，再让 Flyway 走 baseline-on-migrate */
    private static void migrateLegacySchema(final Connection connection, final String schema,
                                            final String unConvergeSql) throws SQLException {
        createSchema(connection, schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute(baselineScript());
            statement.execute(unConvergeSql);
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO public");
            }
        }
        flyway(schema).migrate();
    }

    private static Flyway flyway(final String schema) {
        return Flyway.configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .schemas(schema)
                .defaultSchema(schema)
                // 让脚本里的非限定表名落到被测 schema，而不是连接的默认 schema
                .initSql("SET search_path TO " + schema)
                .locations("classpath:db/migration/postgres")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private static String baselineScript() {
        try (InputStream in = PostgresFlywayMigrationEquivalenceTest.class
                .getResourceAsStream("/db/migration/postgres/V1__baseline.sql")) {
            assertTrue(in != null, "classpath 上找不到 V1__baseline.sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 V1 基线脚本失败", e);
        }
    }

    // ==================== 结构快照 ====================

    private static Map<String, List<String>> snapshot(final Connection connection, final String schema)
            throws SQLException {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("tables", query(connection,
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name <> ? "
                        + "ORDER BY table_name", schema, HISTORY_TABLE));
        sections.put("columns", query(connection,
                "SELECT table_name || '.' || column_name || ' ' || data_type || '/' || udt_name"
                        + " || ' null=' || is_nullable || ' len=' || coalesce(character_maximum_length, -1)"
                        + " || ' prec=' || coalesce(numeric_precision, -1)"
                        + " || ' scale=' || coalesce(numeric_scale, -1)"
                        + " || ' dtprec=' || coalesce(datetime_precision, -1)"
                        + " || ' identity=' || is_identity "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name <> ? "
                        + "ORDER BY table_name, column_name", schema, HISTORY_TABLE));
        sections.put("indexes", query(connection,
                "SELECT indexname || ' :: ' || replace(replace(indexdef, '\"" + schema + "\".', ''), '"
                        + schema + ".', '') "
                        + "FROM pg_indexes WHERE schemaname = ? AND tablename <> ? "
                        + "ORDER BY indexname", schema, HISTORY_TABLE));
        sections.put("checkConstraints", query(connection,
                "SELECT tc.table_name || '.' || cc.constraint_name || ' ' || cc.check_clause "
                        + "FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.check_constraints cc "
                        + "  ON cc.constraint_name = tc.constraint_name "
                        + " AND cc.constraint_schema = tc.constraint_schema "
                        + "WHERE tc.table_schema = ? AND tc.constraint_type = 'CHECK' AND tc.table_name <> ? "
                        // PostgreSQL 把 NOT NULL 也暴露成 check 约束，其自动名里带表 OID
                        // （形如 17077_17088_11_not_null），跨 schema 不可比 —— 而可空性已由
                        // columns 分节的 is_nullable 覆盖，这里只比有稳定名字的枚举 CHECK。
                        + "  AND cc.check_clause NOT LIKE '% IS NOT NULL' "
                        + "ORDER BY 1", schema, HISTORY_TABLE));
        return sections;
    }

    private static List<String> history(final Connection connection, final String schema)
            throws SQLException {
        return query(connection,
                "SELECT version || '|' || type || '|' || success FROM " + schema + "." + HISTORY_TABLE
                        + " ORDER BY installed_rank");
    }

    private static List<String> query(final Connection connection, final String sql, final Object... args)
            throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var prepared = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                prepared.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = prepared.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString(1));
                }
            }
        }
        return rows;
    }

    // ==================== 辅助 ====================

    private static void createSchema(final Connection connection, final String schema)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
    }

    private static void dropSchema(final Connection connection, final String schema)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static String env(final String name, final String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
