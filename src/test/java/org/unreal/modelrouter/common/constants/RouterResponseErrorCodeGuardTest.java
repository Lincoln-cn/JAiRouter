package org.unreal.modelrouter.common.constants;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失败响应必须带错误码的守卫（issue #413）。
 *
 * <p><b>判据</b>：{@code src/main/java} 里**不允许**再出现单参调用
 * {@code RouterResponse.error(message)} —— 该重载把 {@code errorCode} 写成 {@code null}
 * （{@code RouterResponse.error(msg)} 的实现就是 {@code error(message, null)}），
 * 客户端拿到 {@code "errorCode": null} 后无法按码分支。</p>
 *
 * <p><b>为什么需要一条结构性守卫</b>：{@code ErrorCodeLiteralGuardTest}（#370 阶段 3）只守
 * 「注册表里的码不得写裸字面量」——它对**根本没有码**的调用点是盲的；{@code check-error-codes.py}
 * 也只对账文档与注册表。issue #413 的实测口径正是「单参调用计数归零」，本测试把该口径钉死：
 * 谁再把某个发出点写回单参形式（或新写一处），这里立刻红。</p>
 *
 * <p>实现要点：先用 {@link ErrorCodeLiteralGuardTest#stripComments(String)} 去注释（字符串内容保留），
 * 再对 {@code RouterResponse.error(} 做**括号配平**扫描并数**顶层逗号**（跳过字符串与嵌套括号），
 * 顶层逗号数为 0 ⇒ 单参。因此多行实参、字符串里的逗号、嵌套方法调用都不会误判。</p>
 */
@DisplayName("失败响应必须带 errorCode 守卫（issue #413）")
class RouterResponseErrorCodeGuardTest {

    /** 主源码根目录（Maven 的 surefire 以项目根为工作目录）。 */
    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** 单参调用：RouterResponse.error( ... ) */
    private static final Pattern ERROR_CALL = Pattern.compile("RouterResponse\\s*\\.\\s*error\\s*\\(");

    @Test
    @DisplayName("src/main/java 里不得出现单参 RouterResponse.error(msg)（计数归零）")
    void noSingleArgumentErrorCallRemains() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "找不到主源码目录，扫描会空转: " + SOURCE_ROOT.toAbsolutePath());

        final List<String> singleArgumentSites = new ArrayList<>();
        int totalCalls = 0;

        final List<Path> files;
        try (Stream<Path> walked = Files.walk(SOURCE_ROOT)) {
            files = walked.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (final Path file : files) {
            final String source = ErrorCodeLiteralGuardTest.stripComments(
                    Files.readString(file, StandardCharsets.UTF_8));
            final Matcher matcher = ERROR_CALL.matcher(source);
            while (matcher.find()) {
                totalCalls++;
                final int argumentCount = countTopLevelArguments(source, matcher.end());
                if (argumentCount < 2) {
                    final String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                    singleArgumentSites.add(relative + ":" + lineOf(source, matcher.start())
                            + " 实参个数=" + argumentCount);
                }
            }
        }

        // 反空转：扫描必须真的看到调用点（否则「没找到」会静默变成通过）
        assertTrue(totalCalls > 50,
                "扫描结果异常（反空转）：只看到 " + totalCalls + " 处 RouterResponse.error 调用");
        assertTrue(singleArgumentSites.isEmpty(),
                "单参 RouterResponse.error 会把 errorCode 写成 null，客户端无法按码分支（issue #413）。"
                        + "必须改为 RouterResponse.error(message, ErrorCode.X.code())。命中:\n  "
                        + String.join("\n  ", singleArgumentSites));
    }

    /**
     * 数实参个数：从 {@code openParenEnd}（左括号之后）开始括号配平，返回顶层逗号数 + 1。
     */
    static int countTopLevelArguments(final String text, final int openParenEnd) {
        int depth = 1;
        int commas = 0;
        boolean inString = false;
        int i = openParenEnd;
        while (i < text.length() && depth > 0) {
            final char ch = text.charAt(i);
            if (inString) {
                if (ch == '\\') {
                    i += 2;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 1) {
                commas++;
            }
            i++;
        }
        return commas + 1;
    }

    private static int lineOf(final String text, final int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
