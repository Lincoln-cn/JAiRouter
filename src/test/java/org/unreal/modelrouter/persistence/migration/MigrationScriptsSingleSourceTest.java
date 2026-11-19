package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移脚本的单一来源守卫。
 *
 * <p>守两件事：</p>
 * <ol>
 *   <li><b>两个方言必须成对</b>：H2 与 PostgreSQL 的脚本文件名集合完全一致。只给一边加迁移
 *       会让另一半部署在启动时缺表 —— 这类问题本地（只跑 H2）永远发现不了。</li>
 *   <li><b>版本号不得再被硬编码</b>：{@code MigrateOnlyEntryProcessTest} 与
 *       {@code H2FlywayMigrationEquivalenceTest} 里的期望链改由 {@link MigrationScripts}
 *       从目录读。若有人又把 {@code "1|SQL", "2|SQL", ...} 写回去，这个用例会红 ——
 *       否则「加一个迁移要改三处」的坑会原样回来（V18 落地时正是连红两轮）。</li>
 * </ol>
 */
@DisplayName("迁移脚本单一来源守卫")
class MigrationScriptsSingleSourceTest {

    /** 形如 {@code "18|SQL"} / {@code "18|SQL|true"} 的历史断言字面量 */
    private static final Pattern HARDCODED_HISTORY = Pattern.compile("\"\\d+\\|(?:SQL|BASELINE)(?:\\|true)?\"");

    @Test
    @DisplayName("H2 与 PostgreSQL 的迁移脚本必须一一对应")
    void dialectsHaveIdenticalScripts() {
        List<String> h2 = MigrationScripts.fileNames("h2");
        List<String> postgres = MigrationScripts.fileNames("postgres");

        List<String> onlyInH2 = new ArrayList<>(h2);
        onlyInH2.removeAll(postgres);
        List<String> onlyInPostgres = new ArrayList<>(postgres);
        onlyInPostgres.removeAll(h2);

        assertTrue(onlyInH2.isEmpty() && onlyInPostgres.isEmpty(),
                "两个方言的迁移脚本必须同名同版本；仅 H2 有: " + onlyInH2 + "，仅 PostgreSQL 有: " + onlyInPostgres);
        assertFalse(h2.isEmpty(), "H2 目录下应至少有基线迁移");
    }

    @Test
    @DisplayName("版本号连续且从 1 开始，无空洞")
    void versionsAreContiguous() {
        List<Integer> versions = MigrationScripts.h2Versions();
        assertEquals(versions.size(), versions.get(versions.size() - 1),
                "版本号必须从 1 连续到最大值，中间不允许空洞；实际: " + versions);
        assertEquals(1, versions.get(0), "必须有 V1 基线；实际: " + versions);
    }

    @Test
    @DisplayName("测试源码里不得再硬编码迁移历史（版本号只从目录读）")
    void historyAssertionsAreNotHardcoded() throws IOException {
        Path testDir = Path.of("src", "test", "java", "org", "unreal", "modelrouter",
                "persistence", "migration");
        assertTrue(Files.isDirectory(testDir), "找不到迁移测试目录: " + testDir.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(testDir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList())) {
                // 守卫自身要引用这个正则与示例字面量，跳过它自己
                if (file.getFileName().toString().equals("MigrationScriptsSingleSourceTest.java")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = HARDCODED_HISTORY.matcher(source);
                int hits = 0;
                while (matcher.find()) {
                    hits++;
                }
                if (hits >= 3) {
                    violations.add(file.getFileName() + "（" + hits + " 处）");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "迁移历史断言必须由 MigrationScripts 从 db/migration 目录构造，"
                        + "不要在测试里写死一份版本列表（加迁移时会红灯且易漏改）: " + violations);
    }
}
