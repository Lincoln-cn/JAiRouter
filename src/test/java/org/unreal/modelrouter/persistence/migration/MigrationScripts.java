package org.unreal.modelrouter.persistence.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 迁移脚本的**唯一来源**（测试侧）。
 *
 * <p>为什么要有这个类：版本号此前散落在三个地方硬编码 ——
 * {@code MigrateOnlyEntryProcessTest} 的期望历史、{@code H2FlywayMigrationEquivalenceTest}
 * 的 fresh/legacy 两条期望链。每加一个 {@code Vn__*.sql} 就要同步改三处，漏一处就红一次
 * （V18 落地时正是这样连红两轮）。这里把它们统一成「从目录读」，硬编码只剩
 * <em>这一份解析逻辑</em>，并由 {@link MigrationScriptsSingleSourceTest} 守着
 * 「别再写死一份」。</p>
 *
 * <p>目录定位：surefire 与 IDE 都以模块根为工作目录，所以优先读源码目录；
 * 若以打包后的 classpath 跑，则回退到 {@code target/classes}。两处都不存在时直接失败 ——
 * 静默返回空列表会让「期望为空 ⇒ 断言恒真」变成假绿。</p>
 */
final class MigrationScripts {

    private static final Pattern SCRIPT = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private MigrationScripts() {
    }

    /**
     * 迁移脚本根目录。
     *
     * @return 目录路径（保证存在）
     */
    static Path baseDir() {
        List<Path> candidates = List.of(
                Paths.get("src", "main", "resources", "db", "migration"),
                Paths.get("target", "classes", "db", "migration"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到迁移脚本目录，已尝试: " + candidates
                + "（当前工作目录: " + Paths.get("").toAbsolutePath() + "）");
    }

    /**
     * 某一方言目录下的脚本文件名（排序后）。
     *
     * @param dialect {@code h2} 或 {@code postgres}
     * @return 文件名列表（按文件名字典序）
     */
    static List<String> fileNames(final String dialect) {
        Path dir = baseDir().resolve(dialect);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("找不到方言目录: " + dir);
        }
        try (var stream = Files.list(dir)) {
            return stream.map(path -> path.getFileName().toString())
                    .filter(name -> SCRIPT.matcher(name).matches())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("读取迁移脚本目录失败: " + dir, e);
        }
    }

    /**
     * 某一方言的版本号（升序，去重）。
     *
     * @param dialect {@code h2} 或 {@code postgres}
     * @return 版本号列表
     */
    static List<Integer> versions(final String dialect) {
        Set<Integer> sorted = new TreeSet<>();
        for (String name : fileNames(dialect)) {
            Matcher matcher = SCRIPT.matcher(name);
            if (matcher.matches()) {
                sorted.add(Integer.parseInt(matcher.group(1)));
            }
        }
        if (sorted.isEmpty()) {
            throw new IllegalStateException("方言 " + dialect + " 下没有解析到任何迁移脚本");
        }
        return new ArrayList<>(sorted);
    }

    /**
     * H2 方言的版本号，供各测试构造期望值。
     *
     * @return 版本号列表
     */
    static List<Integer> h2Versions() {
        return versions("h2");
    }
}
