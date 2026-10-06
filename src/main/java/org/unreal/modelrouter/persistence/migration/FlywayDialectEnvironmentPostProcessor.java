package org.unreal.modelrouter.persistence.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按数据源方言决定 schema 管理方式（issue #191 / #192）。
 *
 * <p>「哪个库用版本化迁移」不能靠 Spring profile 表达：本仓库连哪个库只由 {@code DATABASE_URL}
 * 决定（默认值见 {@code config/config-service/core.yml}），同一个 {@code prod} profile 既可能连
 * H2 也可能连 PG。因此这里在配置加载完成后，按解析出的 {@code spring.datasource.url} 判断方言，
 * 并为**两种受支持的方言**都启用 Flyway：
 *
 * <ul>
 *   <li><b>PostgreSQL</b>（{@code jdbc:postgresql:…}）→ 脚本目录 {@code classpath:db/migration/postgres}</li>
 *   <li><b>H2</b>（{@code jdbc:h2:…}）→ 脚本目录 {@code classpath:db/migration/h2}</li>
 * </ul>
 *
 * <p>两者都给 {@code spring.flyway.enabled=true}、{@code baseline-on-migrate=true}、
 * {@code baseline-version=1}，脚本目录各自独立（物理类型不同：H2 是 {@code json} / {@code TEXT} /
 * {@code enum}，PG 是 {@code jsonb} / {@code TEXT} / {@code varchar} + check）。
 *
 * <p><b>只有 PostgreSQL 会把 {@code ddl-auto} 覆盖为 {@code validate}</b>，H2 保留配置里的
 * {@code update}。原因是实测的（issue #192）：H2 在 {@code DATABASE_TO_UPPER=FALSE} 下把对象建成
 * 小写（{@code PUBLIC.api_call_history}），而 Hibernate 的 schema 校验在 JDBC 元数据里按另一侧
 * 大小写去找 schema 名，一律报 {@code Schema-validation: missing table …}（用 JDBC 元数据探针确认：
 * {@code getTables(cat, "PUBLIC", …)} 命中、{@code "public"} 不命中；{@code default_schema=PUBLIC}
 * 与 {@code jdbc_metadata_extraction_strategy=grouped} 都不解决）。同一份脚本在**去掉**该模式的
 * H2 默认大小写下跑得通（实测：迁移 + 校验 + 启动 + 退出码 0），但那会让既有小写 H2 库全部失配、
 * 必须重建 —— 属破坏性变更，本步不做。H2 因此是「Flyway 版本化 + Hibernate {@code update}」，
 * 同样能替掉原来的启动期补丁组件（补丁语义见 {@code h2/V2__legacy_convergence.sql}）。
 *
 * <h2>优先级：本方注入的是兜底默认值，不是强制覆盖</h2>
 * <p>{@code spring.flyway.*} 四个键以 {@code addLast} 注入，因此**任何显式配置都优先**：
 * {@code application-test.yml} 里为了跑内存库而设的 {@code spring.flyway.enabled=false} 会照常生效
 * （切片测试、以及用 {@code @DynamicPropertySource} 后置改数据源 URL 的门控测试都不受影响）。
 * 这一点不能靠 {@code addFirst} 实现 —— 那样会把显式配置压下去，实测代价是 PG 门控测试拿
 * H2 的脚本去建 PG 的库（issue #192 修复过程中踩到）。
 *
 * <p>唯一的例外是 PG 上的 {@code ddl-auto}：必须用 {@code addFirst} 盖过 {@code core.yml} 里既有的
 * {@code update}（那条默认值是嵌入式库的安全网，缺了它 Hibernate 对嵌入式数据源会退化为
 * {@code create-drop}，不能删、只能按方言覆盖）。
 *
 * <p>认不出的方言（或没设数据源）同样以 {@code addLast} 注入
 * {@code spring.flyway.enabled=false} 兜底：Flyway 的自动配置是「classpath 上有依赖且未显式关闭」
 * 即生效，而 {@code db/migration} 下的脚本都是具体方言的，那种库上必须关掉。
 */
public class FlywayDialectEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 判定为目标方言的 URL 前缀 */
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";
    private static final String H2_URL_PREFIX = "jdbc:h2:";

    /** 本类注入的兜底属性来源名（{@code addLast}） */
    static final String SOURCE_NAME = "jairouterSchemaDialect";

    /** 强制覆盖的 {@code ddl-auto} 属性来源名（{@code addFirst}） */
    static final String FORCED_SOURCE_NAME = "jairouterSchemaDialectForced";

    static final String DATASOURCE_URL = "spring.datasource.url";
    static final String FLYWAY_ENABLED = "spring.flyway.enabled";
    static final String FLYWAY_LOCATIONS = "spring.flyway.locations";
    static final String FLYWAY_BASELINE_ON_MIGRATE = "spring.flyway.baseline-on-migrate";
    static final String FLYWAY_BASELINE_VERSION = "spring.flyway.baseline-version";
    static final String DDL_AUTO = "spring.jpa.hibernate.ddl-auto";

    static final String POSTGRES_LOCATIONS = "classpath:db/migration/postgres";
    static final String H2_LOCATIONS = "classpath:db/migration/h2";

    /** 须在配置导入之后运行，才能看到导入文件里的数据源 URL */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment,
                                       final SpringApplication application) {
        final MutablePropertySources sources = environment.getPropertySources();
        final String url = environment.getProperty(DATASOURCE_URL);
        final String locations = migrationLocations(url);

        if (locations == null) {
            sources.addLast(new MapPropertySource(SOURCE_NAME,
                    Map.of(FLYWAY_ENABLED, "false")));
            return;
        }

        final Map<String, Object> dialectDefaults = new LinkedHashMap<>();
        dialectDefaults.put(FLYWAY_ENABLED, "true");
        dialectDefaults.put(FLYWAY_LOCATIONS, locations);
        dialectDefaults.put(FLYWAY_BASELINE_ON_MIGRATE, "true");
        dialectDefaults.put(FLYWAY_BASELINE_VERSION, "1");
        // addLast：显式配置（如 application-test.yml 的 flyway.enabled=false）优先于这些兜底值
        sources.addLast(new MapPropertySource(SOURCE_NAME, dialectDefaults));

        if (locations.equals(POSTGRES_LOCATIONS)) {
            // PostgreSQL 上 Hibernate 只做校验；H2 不上 validate，原因见类注释（实测：H2 的
            // DATABASE_TO_UPPER=FALSE 模式下 Hibernate 的 schema 校验查不到对象）。
            // 这一条必须盖过 core.yml 的 ddl-auto: update，故用 addFirst。
            sources.addFirst(new MapPropertySource(FORCED_SOURCE_NAME, Map.of(DDL_AUTO, "validate")));
        }
    }

    /**
     * 该数据源对应的迁移脚本目录；不是受支持的方言时返回 {@code null}。
     *
     * <p>两侧都做大小写无关的前缀比较：URL 前缀本身大小写固定，但 JDBC URL 里出现大写形式并非不可能。
     */
    static String migrationLocations(final String url) {
        if (url == null) {
            return null;
        }
        final String normalized = url.toLowerCase();
        if (normalized.startsWith(POSTGRES_URL_PREFIX)) {
            return POSTGRES_LOCATIONS;
        }
        if (normalized.startsWith(H2_URL_PREFIX)) {
            return H2_LOCATIONS;
        }
        return null;
    }
}
