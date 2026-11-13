package org.unreal.modelrouter.common.health;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 健康详情空值安全的静态守卫。
 *
 * <p>这类缺陷的形态很固定：把 {@code e.getMessage()} 直接塞进 {@code Map.of} 或
 * {@code Health.Builder#withDetail}，而这两者都拒绝 null。触发条件是「异常没有消息」，
 * 单测很难覆盖到每一个调用点，所以这里退一步，直接在主源码里禁止这种写法出现。</p>
 *
 * <p>本用例自带反空转断言：若源码目录路径写错，{@code scanned} 会接近 0，
 * 那时「没有违规」只是假象。</p>
 */
@DisplayName("健康详情空值安全守卫")
class HealthDetailNullSafetyTest {

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

    /** 已确认为危险的写法：异常 message 直接进健康详情。 */
    private static final List<String> FORBIDDEN = List.of(
            "withDetail(\"error\", e.getMessage())",
            "withDetail(\"error\", ex.getMessage())",
            "\"error\", e.getMessage(),",
            "\"error\", ex.getMessage(),"
    );

    @Test
    @DisplayName("主源码里不再把异常消息直接写进健康详情")
    void noRawExceptionMessageInHealthDetails() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "源码目录不存在：" + SOURCE_ROOT.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        List<Path> files;
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            files = stream.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path file : files) {
            scanned++;
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            for (String pattern : FORBIDDEN) {
                if (text.contains(pattern)) {
                    offenders.add(file + " -> " + pattern);
                }
            }
        }

        assertTrue(scanned > 500, "扫描到的 java 文件数异常（" + scanned + "），守卫可能空转");
        assertTrue(offenders.isEmpty(),
                "健康详情必须经由 HealthDetails.safeMessage 取值，发现直接写入 e.getMessage() 的位置："
                        + offenders);
    }
}
