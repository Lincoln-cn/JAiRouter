package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 按方言决定 schema 管理方式（issue #191 / #192）。
 *
 * <p>这里钉住两组行为：PostgreSQL 与 H2 都启用 Flyway 基线迁移、各自指向自己的脚本目录，
 * 并把 {@code ddl-auto} 覆盖为 {@code validate}；认不出的方言（或没设数据源）关闭 Flyway 且
 * 不碰 {@code ddl-auto}，行为与引入本类之前一致。</p>
 */
@DisplayName("schema 方言判定（issue #191 / #192）")
class FlywayDialectEnvironmentPostProcessorTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://db:5432/jairouter";
    private static final String H2_URL =
            "jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";

    private final FlywayDialectEnvironmentPostProcessor processor =
            new FlywayDialectEnvironmentPostProcessor();

    /** 模拟 config/config-service/core.yml：数据源默认 H2，ddl-auto 为 update */
    private static MockEnvironment environmentWithDefaults(final String url) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("coreYml", Map.of(
                FlywayDialectEnvironmentPostProcessor.DATASOURCE_URL, url,
                FlywayDialectEnvironmentPostProcessor.DDL_AUTO, "update")));
        return environment;
    }

    @Test
    @DisplayName("PostgreSQL：启用 Flyway 基线迁移（脚本目录 postgres），ddl-auto 覆盖为 validate")
    void postgresUsesPostgresMigrations() {
        MockEnvironment environment = environmentWithDefaults(POSTGRES_URL);

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("classpath:db/migration/postgres",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_LOCATIONS));
        assertEquals("true",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_ON_MIGRATE));
        assertEquals("1",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_VERSION));
        assertEquals("validate", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("H2：启用 Flyway 基线迁移（脚本目录 h2），但**不**改 ddl-auto（保留 update）")
    void h2UsesH2Migrations() {
        MockEnvironment environment = environmentWithDefaults(H2_URL);

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("classpath:db/migration/h2",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_LOCATIONS));
        assertEquals("true",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_ON_MIGRATE));
        assertEquals("1",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_VERSION));
        // H2 不上 validate：见处理器类注释（DATABASE_TO_UPPER=FALSE 下 Hibernate 的 schema 校验
        // 查不到对象，实测证据在 issue #192）
        assertEquals("update", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("未设置数据源：关闭 Flyway，且不碰 ddl-auto")
    void treatsMissingUrlAsUnsupported() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertNull(environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("认不出的方言（MySQL）：关闭 Flyway，不碰 ddl-auto")
    void treatsUnknownDialectAsUnsupported() {
        MockEnvironment environment = environmentWithDefaults("jdbc:mysql://db:3306/jairouter");

        processor.postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("update", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("认不出的方言上，显式 Flyway 开关优先于兜底值")
    void explicitFlywaySwitchWinsOverFallback() {
        MockEnvironment environment = environmentWithDefaults("jdbc:mysql://db:3306/jairouter");
        environment.getPropertySources().addFirst(new MapPropertySource("explicit", Map.of(
                FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED, "true")));

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
    }

    /**
     * 回归（issue #192 修复过程中踩到）：方言判定发生在**配置加载之后、{@code @DynamicPropertySource}
     * 之前**，所以门控测试（如 {@code PostgresCompatibilityIntegrationTest}）在处理器眼里看到的仍是
     * {@code application-test.yml} 的 H2 URL。此时必须让测试环境显式设的
     * {@code spring.flyway.enabled=false} 生效 —— 曾用 {@code addFirst} 注入，把它压下去了，
     * 结果 Flyway 拿着 **H2 的脚本**去建 PG 的库（{@code type "enum" does not exist}），
     * 整个类 10 个用例全红。
     */
    @Test
    @DisplayName("受支持方言上，显式 flyway.enabled=false 仍优先于方言兜底值（PG 门控测试的保命条款）")
    void explicitFlywaySwitchWinsOverDialectDefaults() {
        for (String url : new String[]{POSTGRES_URL, H2_URL}) {
            MockEnvironment environment = environmentWithDefaults(url);
            environment.getPropertySources().addFirst(new MapPropertySource("testYml", Map.of(
                    FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED, "false")));

            processor.postProcessEnvironment(environment, null);

            assertEquals("false", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED),
                    url + " 上显式关闭 Flyway 必须生效");
        }
    }

    /**
     * 与上一条相对：{@code ddl-auto} 是**唯一**被强制覆盖的键 —— 它必须压过 {@code core.yml} 的
     * {@code update}，否则 PG 路径下 Hibernate 会继续自己改表。
     */
    @Test
    @DisplayName("PostgreSQL 上 ddl-auto 被强制覆盖为 validate，压过 core.yml 的 update")
    void postgresForcesValidateOverExplicitUpdate() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("explicit", Map.of(
                FlywayDialectEnvironmentPostProcessor.DATASOURCE_URL, POSTGRES_URL,
                FlywayDialectEnvironmentPostProcessor.DDL_AUTO, "update")));

        processor.postProcessEnvironment(environment, null);

        assertEquals("validate", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }
}
