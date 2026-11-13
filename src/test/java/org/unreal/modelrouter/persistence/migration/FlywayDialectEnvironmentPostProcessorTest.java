package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // baseline-version 取脚本目录里的最新版本，不是固定 1（issue #1046）
        assertEquals(FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion(
                        FlywayDialectEnvironmentPostProcessor.POSTGRES_LOCATIONS),
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_VERSION));
        assertEquals("validate", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    @Test
    @DisplayName("H2：启用 Flyway 基线迁移（脚本目录 h2），并把 ddl-auto 覆盖为 none")
    void h2UsesH2Migrations() {
        MockEnvironment environment = environmentWithDefaults(H2_URL);

        processor.postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("classpath:db/migration/h2",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_LOCATIONS));
        assertEquals("true",
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_ON_MIGRATE));
        // baseline-version 取脚本目录里的最新版本，不是固定 1（issue #1046）
        assertEquals(FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion(
                        FlywayDialectEnvironmentPostProcessor.H2_LOCATIONS),
                environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_BASELINE_VERSION));
        // H2 上既不上 validate（DATABASE_TO_UPPER=FALSE 下 Hibernate 的 schema 校验查不到对象，
        // 实测证据在 issue #192），也不留 update（第二次启动会逐表重发 CREATE TABLE、被 H2 以
        // already exists 拒绝，实测证据在 issue #1037）—— 建表完全交给 Flyway。
        assertEquals("none", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO));
    }

    /**
     * 反向条款：测试环境用内存库 + {@code create-drop}，Flyway 被显式关掉。此时若还把
     * {@code ddl-auto} 覆盖成 {@code none}，一张表都不会被建出来，整个测试套件会先崩在装配阶段。
     * 所以「覆盖」必须以「Flyway 确实在跑」为前提。
     */
    @Test
    @DisplayName("H2 上显式关闭 Flyway 时不覆盖 ddl-auto（否则 create-drop 的测试库没有表）")
    void h2KeepsUpdateWhenFlywayExplicitlyDisabled() {
        MockEnvironment environment = environmentWithDefaults(H2_URL);
        environment.getPropertySources().addFirst(new MapPropertySource("testYml", Map.of(
                FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED, "false")));

        processor.postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(FlywayDialectEnvironmentPostProcessor.FLYWAY_ENABLED));
        assertEquals("update", environment.getProperty(FlywayDialectEnvironmentPostProcessor.DDL_AUTO),
                "Flyway 被关掉时表只能由 Hibernate 建，不能覆盖成 none");
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

    /**
     * 扫描确实读到了真实脚本目录（issue #1046）。
     *
     * <p>这条同时是反空转断言：若 {@code resolveLatestMigrationVersion} 退化成「永远返回 1」，
     * {@code assertNotEquals("1", …)} 会红，而不是被「两边都返回 1 所以相等」蒙过去。
     *
     * <p>新增迁移脚本后这里需要同步更新 —— 这正是它的价值：让人回头确认 baseline 也跟着走了。
     */
    @Test
    @DisplayName("baseline-version 等于脚本目录里的最新版本（H2 与 PG 都是 12）")
    void latestMigrationVersionIsScanned() {
        for (String locations : new String[]{
                FlywayDialectEnvironmentPostProcessor.H2_LOCATIONS,
                FlywayDialectEnvironmentPostProcessor.POSTGRES_LOCATIONS}) {
            String latest = FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion(locations);
            assertNotEquals(FlywayDialectEnvironmentPostProcessor.DEFAULT_BASELINE_VERSION, latest,
                    locations + " 上必须扫到脚本；扫不到说明扫描逻辑失效，会退回固定 1 的老行为");
            assertEquals("12", latest, locations + " 的最新迁移版本");
        }
    }

    /** 扫不到时刻意回退到固定 1，而不是让启动失败。 */
    @Test
    @DisplayName("扫不到脚本目录时回退到 baseline-version=1")
    void missingScriptsFallBackToBaselineOne() {
        assertEquals(FlywayDialectEnvironmentPostProcessor.DEFAULT_BASELINE_VERSION,
                FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion(
                        "classpath:db/migration/does-not-exist"));
        assertEquals(FlywayDialectEnvironmentPostProcessor.DEFAULT_BASELINE_VERSION,
                FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion(null));
        assertEquals(FlywayDialectEnvironmentPostProcessor.DEFAULT_BASELINE_VERSION,
                FlywayDialectEnvironmentPostProcessor.resolveLatestMigrationVersion("file:/tmp/migrations"));
    }

    /** 版本号按数值比较，不能按字符串：「V10」比「V9」新。 */
    @Test
    @DisplayName("迁移版本号按数值比较")
    void migrationVersionsCompareNumerically() {
        assertEquals("6", FlywayDialectEnvironmentPostProcessor.versionOf("V6__jwt_account_soft_delete.sql"));
        assertEquals("11", FlywayDialectEnvironmentPostProcessor.versionOf("V11__cache_cross_tenant_audit.sql"));
        assertNull(FlywayDialectEnvironmentPostProcessor.versionOf("baseline.sql"));
        assertNull(FlywayDialectEnvironmentPostProcessor.versionOf("V__broken.sql"));
        assertNull(FlywayDialectEnvironmentPostProcessor.versionOf(null));

        assertTrue(FlywayDialectEnvironmentPostProcessor.isNewer("10", "9"),
                "字符串比较会把 10 排在 9 前面，这里必须按数值");
        assertTrue(FlywayDialectEnvironmentPostProcessor.isNewer("2", null));
        assertFalse(FlywayDialectEnvironmentPostProcessor.isNewer("9", "10"));
        assertFalse(FlywayDialectEnvironmentPostProcessor.isNewer(null, "1"));
        assertFalse(FlywayDialectEnvironmentPostProcessor.isNewer("11", "11"));
    }
}
