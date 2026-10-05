package org.unreal.modelrouter.persistence.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补丁只作用于**当前 schema**（issue #216）。
 *
 * <p>原实现的 INFORMATION_SCHEMA 查询没有 schema 约束，于是同一数据库里只要还有**第二个
 * 含同名表的 schema**，补列判定读到的就是跨 schema 的并集 —— 影子表里有的列会被当成
 * 「当前表也有」，缺列**静默不补**，应用随后报 {@code Column xxx not found}。
 *
 * <p>这里用一个**真 H2 库**（两个 schema 各一张同名 {@code api_call_history}）钉住该行为。
 * 用 H2 而不是 mock：要测的是「SQL 到底查到了哪些行」，mock 掉 JdbcTemplate 就把问题本身
 * 测没了。也刻意不门控 —— 它是本地与 CI 都会跑的常驻回归网。
 *
 * <p>URL 参数与生产默认值一致（{@code MODE=MySQL;DATABASE_TO_UPPER=FALSE}），因为大小写行为
 * 正是这个组件历史上踩坑的地方。
 */
@DisplayName("补丁只作用于当前 schema（issue #216）")
class CompatibilitySchemaMigratorSchemaScopeTest {

    private static final String URL =
            "jdbc:h2:mem:schema_scope;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";
    private static final String SHADOW_SCHEMA = "shadow_probe";
    private static final List<String> PATCHED_COLUMNS =
            List.of("record_level", "request_body_encrypted", "response_body_encrypted");

    private JdbcTemplate jdbcTemplate;
    private CompatibilitySchemaMigrator migrator;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(URL);
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");

        // 当前 schema：旧库形态的 api_call_history（缺三个补丁列）
        jdbcTemplate.execute("CREATE TABLE api_call_history (id bigint, trace_id varchar(100))");
        // 影子 schema：同名表，但三个补丁列齐全
        jdbcTemplate.execute("CREATE SCHEMA " + SHADOW_SCHEMA);
        jdbcTemplate.execute("CREATE TABLE " + SHADOW_SCHEMA + ".api_call_history ("
                + "id bigint, trace_id varchar(100), record_level varchar(20), "
                + "request_body_encrypted CLOB, response_body_encrypted CLOB)");

        migrator = new CompatibilitySchemaMigrator(dataSource, jdbcTemplate);
    }

    @Test
    @DisplayName("影子 schema 里存在的列，不得让当前 schema 漏补")
    void shadowSchemaDoesNotHideMissingColumns() {
        migrator.run(null);

        for (String column : PATCHED_COLUMNS) {
            assertTrue(columnsOf("api_call_history", currentSchema()).contains(column),
                    "当前 schema 应补上 " + column + "；实际列 = " + columnsOf("api_call_history", currentSchema())
                            + "（若为空说明补列判定读到了影子 schema 的并集）");
        }
    }

    @Test
    @DisplayName("影子 schema 的同名表不被改动")
    void shadowSchemaTableIsLeftAlone() {
        List<String> before = columnsOf("api_call_history", SHADOW_SCHEMA);

        migrator.run(null);

        assertEquals(before, columnsOf("api_call_history", SHADOW_SCHEMA),
                "补丁只该改当前 schema 的表，不能碰别的 schema 的同名表");
    }

    private String currentSchema() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_SCHEMA", String.class);
    }

    private List<String> columnsOf(final String table, final String schema) {
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = ? AND LOWER(TABLE_SCHEMA) = LOWER(?) "
                        + "ORDER BY COLUMN_NAME",
                String.class, table.toLowerCase(), schema);
    }
}
