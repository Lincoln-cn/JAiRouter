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
 * 按数据源方言决定 schema 管理方式（issue #191）。
 *
 * <p>「PostgreSQL 走 Flyway 版本化迁移、H2 保持 {@code ddl-auto: update}」无法用 Spring profile
 * 表达：本仓库连哪个库只由 {@code DATABASE_URL} 决定（默认值见
 * {@code config/config-service/core.yml}），同一个 {@code prod} profile 既可能连 H2 也可能连 PG。
 * 因此这里在配置加载完成后，按解析出的 {@code spring.datasource.url} 判定方言：
 *
 * <ul>
 *   <li><b>PostgreSQL</b>：注入 {@code spring.flyway.enabled=true}、
 *       {@code baseline-on-migrate=true}、{@code baseline-version=1}、
 *       {@code locations=classpath:db/migration}，并把 {@code ddl-auto} 覆盖为 {@code validate}
 *       —— 建表职责交给 Flyway，Hibernate 只校验。</li>
 *   <li><b>其它（H2 / 未设置）</b>：只注入 {@code spring.flyway.enabled=false} 作为最低优先级的兜底。
 *       Flyway 的自动配置是「类路径上有依赖且未显式关闭」即生效，而 {@code db/migration} 下的脚本
 *       是 PostgreSQL 方言的，H2 上必须关掉。{@code ddl-auto} 保持 {@code core.yml} 里的
 *       {@code update}，H2 路径因此与本类引入之前逐字一致。</li>
 * </ul>
 *
 * <p>预置值只用于兜底：注入 <b>后</b> 不覆盖任何显式配置（H2 分支用 {@code addLast}，
 * 优先级最低）。PG 分支用 {@code addFirst}，因为要在 {@code core.yml} 既有的
 * {@code ddl-auto: update} 之上生效 —— 那条默认值是嵌入式库的安全网（缺了它 Hibernate 对
 * 嵌入式数据源会退化为 {@code create-drop}），不能删，只能按方言覆盖。
 */
public class PostgresFlywayEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 判定为目标方言的 URL 前缀 */
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    /** 本类注入的属性来源名 */
    static final String SOURCE_NAME = "jairouterSchemaDialect";

    static final String DATASOURCE_URL = "spring.datasource.url";
    static final String FLYWAY_ENABLED = "spring.flyway.enabled";
    static final String FLYWAY_LOCATIONS = "spring.flyway.locations";
    static final String FLYWAY_BASELINE_ON_MIGRATE = "spring.flyway.baseline-on-migrate";
    static final String FLYWAY_BASELINE_VERSION = "spring.flyway.baseline-version";
    static final String DDL_AUTO = "spring.jpa.hibernate.ddl-auto";

    /** 须在配置导入之后运行，才能看到导入文件里的数据源 URL */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment,
                                       final SpringApplication application) {
        final MutablePropertySources sources = environment.getPropertySources();
        if (isPostgres(environment.getProperty(DATASOURCE_URL))) {
            final Map<String, Object> dialectDefaults = new LinkedHashMap<>();
            dialectDefaults.put(FLYWAY_ENABLED, "true");
            dialectDefaults.put(FLYWAY_LOCATIONS, "classpath:db/migration");
            dialectDefaults.put(FLYWAY_BASELINE_ON_MIGRATE, "true");
            dialectDefaults.put(FLYWAY_BASELINE_VERSION, "1");
            dialectDefaults.put(DDL_AUTO, "validate");
            sources.addFirst(new MapPropertySource(SOURCE_NAME, dialectDefaults));
        } else {
            sources.addLast(new MapPropertySource(SOURCE_NAME,
                    Map.of(FLYWAY_ENABLED, "false")));
        }
    }

    private static boolean isPostgres(final String url) {
        return url != null && url.toLowerCase().startsWith(POSTGRES_URL_PREFIX);
    }
}
