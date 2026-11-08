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
 * <p><b>两种方言的 {@code ddl-auto} 都会被这里强制覆盖，但覆盖成不同的值</b>：
 * PostgreSQL 用 {@code validate}，H2 用 {@code none}。H2 不能上 {@code validate}，原因是实测的
 * （issue #192）：H2 在 {@code DATABASE_TO_UPPER=FALSE} 下把对象建成小写
 * （{@code PUBLIC.api_call_history}），而 Hibernate 的 schema 校验在 JDBC 元数据里按另一侧
 * 大小写去找 schema 名，一律报 {@code Schema-validation: missing table …}（用 JDBC 元数据探针确认：
 * {@code getTables(cat, "PUBLIC", …)} 命中、{@code "public"} 不命中；{@code default_schema=PUBLIC}
 * 与 {@code jdbc_metadata_extraction_strategy=grouped} 都不解决）。同一份脚本在**去掉**该模式的
 * H2 默认大小写下跑得通（实测：迁移 + 校验 + 启动 + 退出码 0），但那会让既有小写 H2 库全部失配、
 * 必须重建 —— 属破坏性变更，本步不做。
 *
 * <p>H2 此前保留 {@code update}，是把它当「Flyway 之外的兜底」：万一某张表的迁移脚本缺失，
 * Hibernate 会补上。代价是 {@code update} 在同一库的**第二次启动**会逐表重新发 {@code CREATE TABLE}，
 * 被 H2 以 {@code already exists} 拒绝，每次启动刷十余条 WARN 与完整堆栈（issue #1037）——
 * 这些噪音正是排障时最容易淹没真问题的地方。实测确认 H2 的 V1–V11 脚本与 24 个 {@code @Entity}
 * 表一一对应之后，这里改为 {@code none}：建表完全交给 Flyway，Hibernate 不再尝试改表。
 * 关掉 Flyway 的场景（测试用内存库 + {@code create-drop}）不覆盖，保持原有行为。
 *
 * <h2>优先级：本方注入的是兜底默认值，不是强制覆盖</h2>
 * <p>{@code spring.flyway.*} 四个键以 {@code addLast} 注入，因此**任何显式配置都优先**：
 * {@code application-test.yml} 里为了跑内存库而设的 {@code spring.flyway.enabled=false} 会照常生效
 * （切片测试、以及用 {@code @DynamicPropertySource} 后置改数据源 URL 的门控测试都不受影响）。
 * 这一点不能靠 {@code addFirst} 实现 —— 那样会把显式配置压下去，实测代价是 PG 门控测试拿
 * H2 的脚本去建 PG 的库（issue #192 修复过程中踩到）。
 *
 * <p>唯一的例外是方言受支持时的 {@code ddl-auto}：必须用 {@code addFirst} 盖过 {@code core.yml} 里既有的
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

    /** H2 上的 {@code ddl-auto} 取值：建表与演进全部交给 Flyway（issue #1037） */
    static final String DDL_AUTO_NONE = "none";

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
        // 必须在注入方言兜底值之前读：注入之后这里读到的就是自己写的 "true"，分不清是否被显式关掉
        final String explicitFlywayEnabled = environment.getProperty(FLYWAY_ENABLED);
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
            // PostgreSQL 上 Hibernate 只做校验。
            // 这一条必须盖过 core.yml 的 ddl-auto: update，故用 addFirst。
            sources.addFirst(new MapPropertySource(FORCED_SOURCE_NAME, Map.of(DDL_AUTO, "validate")));
        } else if (!flywayExplicitlyDisabled(explicitFlywayEnabled)) {
            // H2 上的建表同样交给 Flyway，Hibernate 不参与：update 会在同一库的第二次启动时
            // 逐表重发 CREATE TABLE、被 H2 以 already exists 拒绝（issue #1037）；validate 又查不到
            // 小写对象（issue #192）。既然上不了 validate，none 是唯一没有副作用的选择。
            // 显式关掉 Flyway 的场景不覆盖，原因见 explicitFlywayEnabled 的取值时机。
            sources.addFirst(new MapPropertySource(FORCED_SOURCE_NAME, Map.of(DDL_AUTO, DDL_AUTO_NONE)));
        }
    }

    /**
     * 显式配置是否把 Flyway 关掉了。
     *
     * <p>只有显式配置会被认出来（在注入方言兜底值之前取值）。测试环境的
     * {@code spring.flyway.enabled=false} 属于这一类 —— 那种场景下 H2 的表由 Hibernate 的
     * {@code create-drop} 建，不能再把 {@code ddl-auto} 覆盖成 {@code none}，否则一张表都不会有。
     */
    static boolean flywayExplicitlyDisabled(final String explicitFlywayEnabled) {
        return "false".equalsIgnoreCase(explicitFlywayEnabled);
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
