package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CompatibilitySchemaMigrator 单元测试
 *
 * <p>验证旧库升级时缺失列的自动补齐逻辑：
 * 表存在 + 缺列 -> ALTER ADD；列齐全 -> 无操作；表不存在 -> 无操作；幂等。
 *
 * @author JAiRouter Team
 * @since 2.9.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilitySchemaMigrator 单元测试")
class CompatibilitySchemaMigratorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments args;

    private CompatibilitySchemaMigrator migrator;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        migrator = new CompatibilitySchemaMigrator(dataSource, jdbcTemplate);
    }

    @Test
    @DisplayName("表存在且缺少 3 列时自动补齐")
    void missingColumnsAreAdded() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("api_call_history")))
                .thenReturn(List.of("id", "trace_id", "request_id", "service_type", "model_name"));

        migrator.run(args);

        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN record_level varchar(20)");
        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN request_body_encrypted CLOB");
        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN response_body_encrypted CLOB");
    }

    @Test
    @DisplayName("列已齐全时不执行 ALTER（幂等）")
    void noAlterWhenColumnsPresent() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("api_call_history")))
                .thenReturn(List.of("id", "trace_id", "record_level",
                        "request_body_encrypted", "response_body_encrypted"));

        migrator.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("表不存在时跳过（新库由 JPA 建表）")
    void skipWhenTableMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(0);

        migrator.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class), anyString());
    }

    @Test
    @DisplayName("部分列缺失时仅补齐缺失列")
    void onlyMissingColumnsAdded() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("api_call_history")))
                .thenReturn(List.of("id", "record_level")); // 仅 record_level 已存在

        migrator.run(args);

        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN request_body_encrypted CLOB");
        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN response_body_encrypted CLOB");
        verify(jdbcTemplate, never())
                .execute("ALTER TABLE api_call_history ADD COLUMN record_level varchar(20)");
    }

    @Test
    @DisplayName("ADD 失败不阻断（日志告警，应用继续启动）")
    void addFailureDoesNotBreak() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("api_call_history")))
                .thenReturn(List.of("id"));
        doThrow(new RuntimeException("db down")).when(jdbcTemplate).execute(anyString());

        migrator.run(args); // 不应抛异常

        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("MySQL 方言下 CLOB 列使用 LONGTEXT")
    void mysqlClobType() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        CompatibilitySchemaMigrator mysqlMigrator = new CompatibilitySchemaMigrator(dataSource, jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("api_call_history")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("api_call_history")))
                .thenReturn(List.of("id"));

        mysqlMigrator.run(args);

        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN request_body_encrypted LONGTEXT");
        verify(jdbcTemplate).execute("ALTER TABLE api_call_history ADD COLUMN response_body_encrypted LONGTEXT");
    }
}
