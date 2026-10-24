package org.unreal.modelrouter.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 裸 {@code catch (Exception x)} 计数守卫（#604 技术债批次 A）.
 *
 * <p>本仓 {@code src/main/java} 里「捕获 {@link Exception} 再统一降级」被用作兜底，历史存量很大
 * （#604 记账时的总量见
 * {@code src/test/resources/techdebt/bare-catch-baseline.txt} 的 {@code TOTAL} 行）。这批存量要靠逐个目录
 * 收窄（改成具体异常类型 + 让不该被吞的异常继续冒泡）才能下降，不能一次性改完；在清理完成前，
 * 需要一道守卫保证**不会一边删一边又被新代码加回去**（否则清理永远做不完）。</p>
 *
 * <p>判据 =「逐文件不得高于基线」，而不是「不得出现」：</p>
 * <ul>
 *   <li>某文件当前计数 &gt; 基线 ⇒ 失败（新增了裸捕获）；</li>
 *   <li>某文件在基线里没有、当前却有裸捕获 ⇒ 失败（新文件引入裸捕获）；</li>
 *   <li>当前计数 &lt; 基线 ⇒ 通过（技术债在减少，这是目标，不该要求同步改基线 ——
 *       否则每清理一处就要改一次守卫文件，清理反而变难）。</li>
 * </ul>
 *
 * <p>为什么不用「总数 &lt;= N」：那样「删掉 3 处、又新增 3 处」能静默通过，而新增点往往正好在热路径上。
 * 逐文件对比能把「新位置」直接指出来。</p>
 *
 * <p>⚠️ 已知边界（如实记录，不装作更强）：</p>
 * <ol>
 *   <li>只扫 {@code src/main/java}；测试代码里的裸捕获不在射程（测试用宽捕获做兜底断言是合理的）。</li>
 *   <li>只认「捕获类型恰好是 {@code Exception}」这一种写法；{@code catch (Throwable)}、
 *       {@code catch (RuntimeException)} 不在此守卫内（前者更宽，理论上也应受限，但存量与语义不同，
 *       需要单独定口径，见 #604）。</li>
 *   <li>注释行（以 {@code //}、{@code /*}、{@code *} 开头）不计入，避免 javadoc 里举例被算成代码。</li>
 * </ol>
 */
@DisplayName("裸 catch(Exception) 计数守卫")
class BareCatchCeilingTest {

    /**
     * 判据正则：捕获类型恰为 {@code Exception}（允许 {@code final} 修饰）。
     */
    private static final Pattern BARE_CATCH =
            Pattern.compile("catch\\s*\\(\\s*(?:final\\s+)?Exception\\s+\\w+");

    /**
     * 被扫描的源码根目录（相对项目根；surefire 的工作目录即项目根）。
     */
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /**
     * 基线文件（{@code 相对路径 计数} 每行一条，另有 {@code TOTAL <n>} 汇总行）。
     */
    private static final Path BASELINE = Path.of("src", "test", "resources", "techdebt", "bare-catch-baseline.txt");

    @Test
    @DisplayName("逐文件裸捕获计数不得高于基线")
    void bareCatchCountsStayWithinBaseline() {
        final Map<String, Integer> baseline = readBaseline();
        final Map<String, Integer> current = scanSourceRoot();

        // 反空转：扫描面必须真的找到东西，否则「找不到 ⇒ 没超标」会静默成立
        assertTrue(baseline.size() >= 200,
                "基线应登记至少 200 个文件，实际 " + baseline.size() + " —— 基线文件可能被误改或路径变了");
        assertTrue(current.size() >= 200,
                "扫描应命中至少 200 个含裸捕获的文件，实际 " + current.size()
                        + " —— 扫描路径或正则可能已失效（" + SOURCE_ROOT.toAbsolutePath() + "）");

        final List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            final String file = entry.getKey();
            final int actual = entry.getValue();
            final Integer allowed = baseline.get(file);
            if (allowed == null) {
                violations.add(file + "：新增文件带 " + actual + " 处裸捕获（基线未登记）");
            } else if (actual > allowed) {
                violations.add(file + "：基线 " + allowed + " 处，当前 " + actual + " 处（+" + (actual - allowed) + "）");
            }
        }

        assertTrue(violations.isEmpty(),
                "裸 catch(Exception) 不许新增（#604 正在逐目录收窄，先收窄再删基线；确实必须吞掉时，"
                        + "改成具体异常类型或把异常继续抛出）。超标文件：" + String.join("；", violations));
    }

    @Test
    @DisplayName("判据正则本身能区分宽捕获与具体异常")
    void regexDetectsOnlyBareExceptionCatch() {
        assertTrue(BARE_CATCH.matcher("} catch (Exception e) {").find(), "应识别 catch (Exception e)");
        assertTrue(BARE_CATCH.matcher("catch (final Exception ex) {").find(), "应识别 final 修饰写法");
        assertTrue(BARE_CATCH.matcher("    } catch(Exception ignored) {").find(), "应识别无空格写法");
        assertTrue(!BARE_CATCH.matcher("catch (RuntimeException e) {").find(), "不应把 RuntimeException 算进来");
        assertTrue(!BARE_CATCH.matcher("catch (IllegalStateException | IOException e) {").find(),
                "不应把多类型捕获算进来");
        // 注释行的过滤责任在调用方（按行首判定），正则本身不区分代码与注释
        assertTrue(BARE_CATCH.matcher("// catch (Exception e) 旧写法见 #604").find(),
                "正则本身会命中注释里的示例 —— 注释过滤由 countBareCatches 按行首判定负责");
    }

    /**
     * 读取基线：{@code path count} 行 + {@code TOTAL n} 行（TOTAL 仅作人读，不参与断言）。
     *
     * @return 文件相对路径 → 基线计数
     */
    private static Map<String, Integer> readBaseline() {
        final Map<String, Integer> baseline = new TreeMap<>();
        final List<String> lines;
        try {
            lines = Files.readAllLines(BASELINE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到基线文件 " + BASELINE.toAbsolutePath(), e);
        }
        for (String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("TOTAL ")) {
                continue;
            }
            final int idx = line.lastIndexOf(' ');
            assertTrue(idx > 0, "基线行格式应为 `路径 计数`，实际：" + raw);
            baseline.put(line.substring(0, idx), Integer.parseInt(line.substring(idx + 1).trim()));
        }
        return baseline;
    }

    /**
     * 扫描 {@code src/main/java}，统计每个文件里的裸捕获处数（跳过纯注释行）。
     *
     * @return 文件相对路径（{@code /} 分隔）→ 处数
     */
    private static Map<String, Integer> scanSourceRoot() {
        final Map<String, Integer> result = new TreeMap<>();
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException("源码根目录不存在：" + SOURCE_ROOT.toAbsolutePath());
        }
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        final int count = countBareCatches(path);
                        if (count > 0) {
                            final String rel = SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
                            result.put(rel, count);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("扫描源码失败：" + SOURCE_ROOT.toAbsolutePath(), e);
        }
        return result;
    }

    /**
     * 统计单个文件里的裸捕获处数。
     *
     * @param path 源文件
     * @return 处数
     */
    private static int countBareCatches(final Path path) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到源文件 " + path.toAbsolutePath(), e);
        }
        int count = 0;
        for (String raw : lines) {
            final String line = raw.trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                continue;
            }
            if (BARE_CATCH.matcher(line).find()) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("基线文件必须可读（守卫不能因为文件缺失而静默跳过）")
    void baselineFileIsPresent() {
        assertNotNull(readBaseline(), "基线应被解析出来");
        assertTrue(Files.isRegularFile(BASELINE), "基线文件应存在：" + BASELINE.toAbsolutePath());
    }
}
