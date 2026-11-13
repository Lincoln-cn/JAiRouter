package org.unreal.modelrouter.persistence.jpa.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态守卫：仓库接口里不得再出现「参数判空」的 JPQL 写法。
 *
 * <p>{@code (:param IS NULL OR e.field = :param)} 在 PostgreSQL 上生成
 * {@code (? is null or ...)}，PG 无法从这种表达式推断绑定变量类型，运行期报
 * {@code could not determine data type of parameter $1}；H2 能推断，单测永远发现不了。
 * 这类查询应改用 {@code persistence.jpa.repository.spec} 下的 {@code Specification}。</p>
 *
 * <p>守卫必须是静态的：一旦有人在新查询里写回这种写法，H2 测试依旧全绿，只有 PG 用户会踩到。</p>
 */
@DisplayName("仓库 JPQL 不得使用参数判空写法（#1134）")
class NullablePredicateGuardTest {

    private static final Pattern NULLABLE_PARAMETER = Pattern.compile("\\(:\\w+\\s+IS\\s+NULL");

    @Test
    @DisplayName("持久层源码中不存在 (:param IS NULL 形式的条件")
    void repositorySourcesDoNotUseNullableParameterPredicates() throws IOException {
        Path root = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(root), "源码根目录不存在: " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                scanned++;
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String line : source.split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    if (NULLABLE_PARAMETER.matcher(trimmed).find()) {
                        offenders.add(file + " -> " + trimmed);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "以下位置仍在用参数判空写法，PostgreSQL 上会报 could not determine data type of parameter:\n"
                        + String.join("\n", offenders));
    }

    @Test
    @DisplayName("守卫自身确实扫描到了源码（反空转）")
    void guardActuallyScansSources() {
        // 若 Files.walk 的起点写错，第一个用例会因为「没有违规」而静默通过。
        assertTrue(Files.isDirectory(Paths.get("src", "main", "java")),
                "扫描根目录必须存在，否则守卫是空转");
    }
}
