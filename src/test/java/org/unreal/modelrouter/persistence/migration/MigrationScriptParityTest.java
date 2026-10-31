package org.unreal.modelrouter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 守卫「两套迁移脚本必须成对维护」。
 *
 * <p>H2 与 PostgreSQL 各自维护一份 Flyway 脚本（{@code db/migration/h2} 与 {@code db/migration/postgres}）。
 * 历史上出现过「只给 H2 加了迁移」的漂移风险：#868 的 V7 就必须同时补两侧，否则生产 PostgreSQL 上的
 * {@code security_audit.event_type} 约束不会更新，审计事件继续写库失败。
 *
 * <p>本测试只做静态比对：版本号集合、描述、以及版本号不重复。它不能替代「真在 PostgreSQL 上跑一遍」
 * （那是 CI 里门控的 {@code PostgresCompatibilityIntegrationTest}），但能挡住「忘了写另一侧」这一类。
 */
class MigrationScriptParityTest {

    private static final Pattern VERSIONED_FILE = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

    @Test
    @DisplayName("H2 与 PostgreSQL 的迁移脚本版本号必须一一对应")
    void bothDialectsCarryTheSameVersions() throws IOException, URISyntaxException {
        Map<Integer, String> h2 = readMigrations("h2");
        Map<Integer, String> postgres = readMigrations("postgres");

        // 反空转：两侧都必须真的扫到脚本，否则「都为空」会让下面两条断言恒真
        assertThat(h2).as("H2 迁移脚本数量（扫描失败会先在此失败）").hasSizeGreaterThanOrEqualTo(6);
        assertThat(postgres).as("PostgreSQL 迁移脚本数量").hasSizeGreaterThanOrEqualTo(6);

        assertThat(h2.keySet())
                .as("H2 与 PostgreSQL 的迁移版本号集合必须一致；缺失一侧会导致该方言上的库结构落后")
                .isEqualTo(postgres.keySet());

        for (Map.Entry<Integer, String> entry : h2.entrySet()) {
            assertThat(postgres.get(entry.getKey()))
                    .as("V%s 在两套方言里的描述必须一致，避免把不同变更当成同一次迁移", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("同一方言内不允许重复版本号")
    void versionsAreUniquePerDialect() throws IOException, URISyntaxException {
        for (String dialect : List.of("h2", "postgres")) {
            Path dir = migrationDir(dialect);
            List<Integer> versions = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                files.map(path -> path.getFileName().toString())
                        .map(VERSIONED_FILE::matcher)
                        .filter(Matcher::matches)
                        .forEach(matcher -> versions.add(Integer.parseInt(matcher.group(1))));
            }
            versions.sort(Comparator.naturalOrder());
            assertThat(versions)
                    .as("%s 侧不应出现重复版本号（Flyway 会直接拒绝启动）", dialect)
                    .doesNotHaveDuplicates();
        }
    }

    private static Map<Integer, String> readMigrations(final String dialect) throws IOException, URISyntaxException {
        Path dir = migrationDir(dialect);
        Map<Integer, String> versions = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString())
                    .map(VERSIONED_FILE::matcher)
                    .filter(Matcher::matches)
                    .forEach(matcher -> versions.put(Integer.parseInt(matcher.group(1)), matcher.group(2)));
        }
        return versions;
    }

    private static Path migrationDir(final String dialect) throws URISyntaxException {
        URL resource = MigrationScriptParityTest.class.getResource("/db/migration/" + dialect);
        assertThat(resource).as("classpath:/db/migration/%s 必须存在", dialect).isNotNull();
        return Path.of(resource.toURI());
    }
}
