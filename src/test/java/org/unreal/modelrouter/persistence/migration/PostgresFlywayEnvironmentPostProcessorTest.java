package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 按方言决定 schema 管理方式（issue #191）。
 *
 * <p>这里钉住两组行为：PostgreSQL 上启用 Flyway 并把 {@code ddl-auto} 覆盖为 {@code validate}；
 * 非 PostgreSQL（H2 / 未设置数据源）上关闭 Flyway 且不碰 {@code ddl-auto}，
 * 使 H2 路径与本类引入之前一致。
 */
@DisplayName("schema 方言判定（issue #191）")
class PostgresFlywayEnvironmentPostProcessorTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://db:5432/jairouter";
    private static final String H2_URL = "jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1";

    private final PostgresFlywayEnvironmentPostProcessor processor =
            new PostgresFlywayEnvironmentPostProcessor();

    /** 模拟 config/config-service/core.yml：数据源默认 H2，ddl-auto 为 update */
    private static MockEnvironment environmentWithDefaults(final String url) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("coreYml", Map.of(
                PostgresFlywayEnvironmentPostProcessor.DATASOURCE_URL, url,
                PostgresFlywayEnvironmentPostProcessor.DDL_AUTO, "update")));
        return environment;
    }

    @Test
    @DisplayName("PostgreSQL：启用 Flyway 基线迁移，并把 ddl-auto 覆盖为 validate")
    void enablesBaselineFlywayOnPostgres() {
        MockEnvironment environment = environmentWithDefaults(POSTGRES_URL);

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("classpath:db/migration",
                environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_LOCATIONS));
        assertEquals("true",
                environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_BASELINE_ON_MIGRATE));
        assertEquals("1",
                environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_BASELINE_VERSION));
        assertEquals("validate", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("H2：关闭 Flyway，且 ddl-auto 保持 update（路径行为不变）")
    void disablesFlywayOnH2() {
        MockEnvironment environment = environmentWithDefaults(H2_URL);

        processor.postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("update", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("未设置数据源：按非 PostgreSQL 处理")
    void treatsMissingUrlAsNonPostgres() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertNull(environment.getProperty(PostgresFlywayEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("非 PostgreSQL 上的显式 Flyway 开关优先于兜底值")
    void explicitFlywaySwitchWinsOverFallback() {
        MockEnvironment environment = environmentWithDefaults(H2_URL);
        environment.getPropertySources().addFirst(new MapPropertySource("explicit", Map.of(
                PostgresFlywayEnvironmentPostProcessor.FLYWAY_ENABLED, "true")));

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(PostgresFlywayEnvironmentPostProcessor.FLYWAY_ENABLED));
    }
}
