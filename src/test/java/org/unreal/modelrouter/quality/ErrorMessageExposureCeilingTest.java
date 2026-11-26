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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端可见错误消息的外显上限守卫（#1197 / #1198）.
 *
 * <p>本仓有大量位置在构造客户端可见响应时手写拼接 {@code Throwable.getMessage()}，把异常原文
 * （类名、SQL 片段、库表名、内部地址、文件路径）一并送给调用方。存量要靠逐个模块收敛（父 issue
 * {@code #1197} 登记了约 75 处），不能一次性改完；清理完成前需要一道守卫保证**不会一边删一边又被
 * 新代码加回去**——否则清理永远做不完，{@code #1193} 的回归（{@code MetricsManagementController}
 * 两处 400 响应）就是这样长出来的。</p>
 *
 * <p><b>判据</b> = 「逐文件不得高于基线」：</p>
 * <ul>
 *   <li>某文件当前处数 &gt; 基线 ⇒ 失败（新增了外显点）；</li>
 *   <li>某文件在基线里没有、当前却有 ⇒ 失败（新文件引入外显点）；</li>
 *   <li>当前处数 &lt; 基线 ⇒ 通过（技术债在减少，这是目标，不该要求同步改基线）。</li>
 * </ul>
 *
 * <p><b>什么叫「一处外显」</b>：在响应构造的实参里读到 {@code getMessage()}。响应构造入口见
 * {@link #SINK}——{@code RouterResponse.error(}、{@code ApiException.of(}、{@code .setMessage(}、
 * {@code .put("error"}、{@code .body(}；实参按括号配平截取，支持跨行，字符串字面量与注释不参与匹配。</p>
 *
 * <p>为什么按「实参内」判而不是按行判：先打日志再返回固定文案是正确的写法（
 * {@code logger.error(..., e.getMessage()); return RouterResponse.error("失败", "500");}），按行判会把
 * 它算成违规，基线里混进大量已经正确的代码，之后没人能分清哪些是真问题。</p>
 *
 * <p>⚠️ 已知边界（如实记录，不装作更强）：</p>
 * <ol>
 *   <li>只扫 {@code src/main/java}；测试代码里的同类写法不在射程。</li>
 *   <li>只认「{@code getMessage()} 直接出现在响应构造实参里」这一种形态。先赋给局部变量再传入
 *       （{@code String m = "x: " + e.getMessage(); return RouterResponse.error(m, …);}）抓不到——
 *       这类写法在存量里很少，且会随逐批收敛一并消失。</li>
 *   <li>不判「这个异常是否 client-safe」。判定语义属代码评审与 {@code ClientErrors} 的职责，本守卫只封顶。</li>
 * </ol>
 *
 * @since 3.10.0
 */
@DisplayName("错误消息外显上限守卫")
class ErrorMessageExposureCeilingTest {

    /** 被扫描的源码根目录（相对项目根；surefire 的工作目录即项目根）。 */
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** 基线文件（{@code 相对路径 处数} 每行一条，另有 {@code TOTAL <n>} 汇总行）。 */
    private static final Path BASELINE =
            Path.of("src", "test", "resources", "techdebt", "error-message-exposure-baseline.txt");

    /**
     * 响应构造入口。从这里开始配平括号、截取实参文本。
     *
     * <p>{@code .body(} 也收了：{@code ResponseEntity.badRequest().body(e.getMessage())} 是最直接的形态
     * （{@code #1193} 的两处就是它）。</p>
     */
    private static final Pattern SINK = Pattern.compile(
            "RouterResponse\\s*\\.\\s*error\\s*\\("
                    + "|ApiException\\s*\\.\\s*of\\s*\\("
                    + "|\\.setMessage\\s*\\("
                    + "|(\\w+)\\s*\\.\\s*put\\s*\\(\\s*\"error\""
                    + "|\\.body\\s*\\(");

    /**
     * {@code put("error", …)} 的接收者若属于追踪事件/结构化日志，就不是客户端可见面。
     *
     * <p>这些 map 只进 OpenTelemetry span event 与结构化日志（{@code CircuitBreakerTracingDelegate}、
     * {@code RateLimiterTracingWrapper}），不构成 HTTP 响应体；把它们算进来会让基线混入非问题项，
     * 之后没人能分清哪些是真外显。</p>
     */
    private static final Pattern TRACING_MAP_OWNER = Pattern.compile("logData|eventAttributes|spanAttributes");

    /** 实参里出现即判定为「外显一处」。 */
    private static final Pattern MESSAGE_READ = Pattern.compile("getMessage\\s*\\(\\s*\\)");

    @Test
    @DisplayName("逐文件外显处数不得高于基线")
    void exposuresStayWithinBaseline() {
        final Map<String, Integer> baseline = readBaseline();
        final Map<String, Integer> current = scanSourceRoot();

        // 反空转：扫描面与基线都必须真的有东西，否则「找不到 ⇒ 没超标」会静默成立。
        // 存量会随 #1197 的分批收敛持续下降（首版 97 处 / 27 文件 ⇒ P0/P1 后 45 处 / 13 文件），
        // 所以这里的下限按「还有保留项」设定，真正防判据失效的是紧随其后的锚点断言。
        assertTrue(baseline.size() >= 2,
                "基线应登记至少 2 个文件，实际 " + baseline.size() + " —— 基线可能被误改或路径变了");
        assertTrue(current.size() >= 2,
                "扫描应命中至少 2 个含外显点的文件，实际 " + current.size()
                        + " —— 扫描路径或判据可能已失效（" + SOURCE_ROOT.toAbsolutePath() + "）");
        assertTrue(total(baseline) >= 9,
                "基线总数应不少于 9 处（首版 97 处；P0/P1/P2/P3 收敛后预期剩 9 处保留项），"
                        + "实际 " + total(baseline));
        // 锚点：中央处理器里 ApiException 分支的输出属「可外显」保留项，会长期存在于扫描结果中。
        // 一旦它从命中里消失，说明判据（sink 正则 / 括号配平 / 注释剥离）已经失效，而不是「都修完了」。
        final String anchor = "org/unreal/modelrouter/common/exceptionhandler/ReactiveGlobalExceptionHandler.java";
        assertTrue(current.containsKey(anchor),
                "扫描结果必须包含 " + anchor + "（ApiException 可外显分支的保留项），实际命中 " + current.keySet());
        assertTrue(baseline.containsKey(anchor),
                "基线必须登记 " + anchor + "，否则逐文件比对会把它当成「新增外显点」而误报红");

        final List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            final String file = entry.getKey();
            final int actual = entry.getValue();
            final Integer allowed = baseline.get(file);
            if (allowed == null) {
                violations.add(file + "：新增文件带 " + actual + " 处外显（基线未登记）");
            } else if (actual > allowed) {
                violations.add(file + "：基线 " + allowed + " 处，当前 " + actual + " 处（+" + (actual - allowed) + "）");
            }
        }

        assertTrue(violations.isEmpty(),
                "响应体里不许新增手写拼接 getMessage()（#1197 正在逐批收敛；确需展示给调用方时，"
                        + "抛 ClientFacingException / ApiException 让异常自带语义，不要在控制器里拼）。超标文件："
                        + String.join("；", violations));
    }

    @Test
    @DisplayName("判据能区分「实参里拼消息」与「先记日志再回固定文案」")
    void detectorDistinguishesLogFromResponse() {
        assertTrue(hasExposure("return RouterResponse.error(\"失败: \" + e.getMessage(), \"500\");"),
                "响应构造实参里拼 getMessage() 应被识别");
        assertTrue(hasExposure("return ResponseEntity.badRequest().body(ex.getMessage());"),
                "body(ex.getMessage()) 应被识别");
        assertTrue(hasExposure("return Mono.error(ApiException.of(\"X\", \"失败: \" + ex.getMessage()));"),
                "ApiException.of 实参里拼 getMessage() 应被识别");
        assertTrue(hasExposure("return RouterResponse.error(\n        \"拉取失败: \" + e.getMessage(),\n        \"500\");"),
                "跨行实参应被识别");

        assertTrue(!hasExposure("logger.error(\"失败\", e);\n    return RouterResponse.error(\"失败\", \"500\");"),
                "先记日志再回固定文案是正确写法，不应算违规");
        assertTrue(!hasExposure("logger.error(\"失败: {}\", e.getMessage());"),
                "只打日志不构造响应，不应算违规");
        assertTrue(!hasExposure("return RouterResponse.error(\"失败\", \"500\");"),
                "固定文案不应算违规");
        assertTrue(!hasExposure("// return RouterResponse.error(\"失败: \" + e.getMessage(), \"500\");"),
                "注释掉的代码不应算违规");
    }

    @Test
    @DisplayName("基线文件必须可读（守卫不能因为文件缺失而静默跳过）")
    void baselineFileIsPresent() {
        assertTrue(Files.isRegularFile(BASELINE), "基线文件应存在：" + BASELINE.toAbsolutePath());
        assertTrue(total(readBaseline()) > 0, "基线不应为空");
    }

    private static int total(final Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 单段源码片段里是否含有「响应构造实参拼 getMessage()」。 */
    private static boolean hasExposure(final String snippet) {
        return countExposures(snippet) > 0;
    }

    /**
     * 读取基线：{@code path count} 行 + {@code TOTAL n} 行（TOTAL 仅作人读，不参与断言）。
     *
     * @return 文件相对路径 → 基线处数
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
     * 扫描 {@code src/main/java}，统计每个文件里的外显处数。
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
                    .sorted()
                    .forEach(path -> {
                        final int count;
                        try {
                            count = countExposures(Files.readString(path, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new UncheckedIOException("读不到源文件 " + path.toAbsolutePath(), e);
                        }
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
     * 统计一段源码里的外显处数。
     *
     * <p>逐个找 sink → 配平括号截取实参 → 实参里读 {@code getMessage()}。命中后从右括号之后继续，
     * 因此嵌套 sink（{@code .body(RouterResponse.error(...))}）只计最外层一处。</p>
     *
     * @param source 源码文本（注释已被剥离）
     * @return 处数
     */
    private static int countExposures(final String source) {
        final String code = stripComments(source);
        final Matcher matcher = SINK.matcher(code);
        int count = 0;
        int from = 0;
        while (matcher.find(from)) {
            final String owner = matcher.group(1);
            if (owner != null && TRACING_MAP_OWNER.matcher(owner).matches()) {
                from = matcher.end();
                continue;
            }
            final int open = code.indexOf('(', matcher.start());
            if (open < 0) {
                break;
            }
            final int close = matchParen(code, open);
            if (close < 0) {
                break;
            }
            if (MESSAGE_READ.matcher(code.substring(open + 1, close)).find()) {
                count++;
            }
            from = close + 1;
        }
        return count;
    }

    /**
     * 从 {@code open} 位置的左括号出发，返回与之配平的右括号下标；找不到返回 {@code -1}。
     *
     * <p>跳过字符串与字符字面量，避免 {@code ")"} 把配平带偏。</p>
     */
    private static int matchParen(final String code, final int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i);
                if (i < 0) {
                    return -1;
                }
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 跳过从 {@code quoteIndex} 开始的字面量（支持 {@code \"} 转义与 {@code """} 文本块），
     * 返回字面量最后一个引号的下标。
     */
    private static int skipLiteral(final String code, final int quoteIndex) {
        final char quote = code.charAt(quoteIndex);
        final boolean textBlock = quote == '"' && code.startsWith("\"\"\"", quoteIndex);
        if (textBlock) {
            final int end = code.indexOf("\"\"\"", quoteIndex + 3);
            return end < 0 ? -1 : end + 2;
        }
        for (int i = quoteIndex + 1; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == quote || c == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 剥离注释（保留换行，字符串字面量内的 {@code //} 不动）。
     *
     * @param source 原始源码
     * @return 等长文本，注释字符被空格替换
     */
    private static String stripComments(final String source) {
        final StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            final char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                final int end = skipLiteral(source, i);
                final int stop = end < 0 ? source.length() : end + 1;
                out.append(source, i, stop);
                i = stop;
                continue;
            }
            if (c == '/' && i + 1 < source.length()) {
                final char next = source.charAt(i + 1);
                if (next == '/') {
                    while (i < source.length() && source.charAt(i) != '\n') {
                        out.append(' ');
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    while (i < source.length() && !(source.charAt(i) == '*' && i + 1 < source.length()
                            && source.charAt(i + 1) == '/')) {
                        out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                        i++;
                    }
                    // 收尾的 "*/"
                    for (int k = 0; k < 2 && i < source.length(); k++, i++) {
                        out.append(' ');
                    }
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
