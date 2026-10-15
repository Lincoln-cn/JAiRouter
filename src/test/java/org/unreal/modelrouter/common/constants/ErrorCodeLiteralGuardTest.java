package org.unreal.modelrouter.common.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误码字面量守卫（issue #370 方案 A 阶段 3）。
 *
 * <p><b>判据</b>：注册表（{@link ErrorCode}）是错误码的单一事实来源 ⇒ 在 {@code src/main/java} 里，
 * **注册表里的码不得以裸字符串字面量出现**（发出点必须写 {@code ErrorCode.X.code()}），
 * 只有下列位置例外，且例外清单必须**恰好**等于本测试登记的期望值：</p>
 *
 * <ul>
 *   <li>{@code common/exception/ApiException.java}：历史规则链 {@code legacyResolveStatus}
 *       —— 它**就是**命名约定本身（精确匹配表 + 前缀规则），必须保留字面量；</li>
 *   <li>{@code auth/security/health/JwtPersistenceHealthEndpoint.java}：{@code "NOT_FOUND"} 是**健康状态串**
 *       （{@code componentHealth.put("status", "NOT_FOUND")}），不是错误码；</li>
 *   <li>{@code router/adapter/test/AdapterTestResult.java}：{@code "AUTH_FAILED"} 是**适配器测试状态串**
 *       （{@code STATUS_AUTH_FAILED}），不是错误码。</li>
 * </ul>
 *
 * <p>用法与收益：将来任何人在发出点写裸字面量、或把码硬编码进 JSON/日志，本测试立刻红；
 * 由此「文档 ↔ 注册表」（{@code scripts/docs/check-error-codes.py}，双向）与「注册表 ↔ 代码」
 * （本测试）合成闭环。</p>
 */
@DisplayName("错误码字面量守卫（issue #370 阶段 3）")
class ErrorCodeLiteralGuardTest {

    /** 主源码根目录（Maven 的 surefire 以项目根为工作目录）。 */
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path REGISTRY_FILE =
            SOURCE_ROOT.resolve("org/unreal/modelrouter/common/constants/ErrorCode.java");

    /** 注册表定义里的枚举项：NAME("CODE", ...) */
    private static final Pattern REGISTRY_ENTRY =
            Pattern.compile("^\\s*[A-Z][A-Z0-9_]*\\(\\s*\"([A-Z][A-Z0-9_]+)\"\\s*,", Pattern.MULTILINE);

    /** 任意「码形状」的字符串字面量 */
    private static final Pattern CODE_LITERAL =
            Pattern.compile("\"([A-Z][A-Z0-9_]{2,})\"");

    /**
     * 允许出现裸码字面量的位置（文件相对路径 → 该文件里允许出现的码）。
     *
     * <p>断言写成「**恰好等于**」而不是「包含于」：新增例外、或某处不再需要例外，都必须来改这张表。</p>
     */
    private static final Map<String, Set<String>> ALLOWED = allowedLiterals();

    private static Map<String, Set<String>> allowedLiterals() {
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        allowed.put("org/unreal/modelrouter/common/exception/ApiException.java", Set.of(
                "NOT_FOUND", "INVALID_REQUEST", "INTERNAL_ERROR", "BUILTIN_READONLY",
                "RESTART_REQUIRED", "LOGIN_FAILED", "SERVICE_NOT_AVAILABLE", "FORBIDDEN"));
        allowed.put("org/unreal/modelrouter/auth/security/health/JwtPersistenceHealthEndpoint.java",
                Set.of("NOT_FOUND"));
        allowed.put("org/unreal/modelrouter/router/adapter/test/AdapterTestResult.java",
                Set.of("AUTH_FAILED"));
        return allowed;
    }

    @Test
    @DisplayName("注册表里的码不得以裸字面量出现在 src/main/java（例外清单必须恰好相等）")
    void registryCodesAppearOnlyViaTheRegistry() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "找不到主源码目录，扫描会空转: " + SOURCE_ROOT.toAbsolutePath());
        assertTrue(Files.isRegularFile(REGISTRY_FILE), "找不到注册表文件: " + REGISTRY_FILE);

        Set<String> registry = new TreeSet<>();
        Matcher registryMatcher = REGISTRY_ENTRY.matcher(
                Files.readString(REGISTRY_FILE, StandardCharsets.UTF_8));
        while (registryMatcher.find()) {
            registry.add(registryMatcher.group(1));
        }
        assertTrue(registry.size() > 50, "注册表解析结果异常（反空转）: " + registry.size());

        Map<String, Set<String>> observed = new TreeMap<>();
        List<String> detail = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walked = Files.walk(SOURCE_ROOT)) {
            files = walked.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path file : files) {
            if (file.equals(REGISTRY_FILE)) {
                continue;   // 注册表自己就是唯一允许写码的地方
            }
            String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
            String stripped = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher literalMatcher = CODE_LITERAL.matcher(stripped);
            while (literalMatcher.find()) {
                String code = literalMatcher.group(1);
                if (!registry.contains(code)) {
                    continue;
                }
                observed.computeIfAbsent(relative, k -> new TreeSet<>()).add(code);
                detail.add(relative + ":" + lineOf(stripped, literalMatcher.start()) + " \"" + code + "\"");
            }
        }

        Map<String, Set<String>> actual = new TreeMap<>();
        actual.putAll(observed);
        assertEquals(ALLOWED, actual,
                "注册表里的码只允许在下列位置以字面量出现（其余位置必须写 ErrorCode.X.code()）；"
                        + "实测命中:\n  " + String.join("\n  ", detail));
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

    /**
     * 去掉 Java 注释（行注释 / 块注释 / javadoc），字符串与字符字面量内容保留。
     * 注释字符替换为空格、换行保留 ⇒ 偏移与行号与原文件一致。
     */
    static String stripComments(final String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLineComment) {
                out.append(ch == '\n' ? '\n' : ' ');
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    out.append("  ");
                    i++;
                    continue;
                }
                out.append(ch == '\n' ? '\n' : ' ');
                continue;
            }
            if (inString) {
                out.append(ch);
                if (ch == '\\' && i + 1 < text.length()) {
                    out.append(next);
                    i++;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(ch);
                if (ch == '\\' && i + 1 < text.length()) {
                    out.append(next);
                    i++;
                } else if (ch == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                out.append(ch);
            } else if (ch == '\'') {
                inChar = true;
                out.append(ch);
            } else if (ch == '/' && next == '/') {
                inLineComment = true;
                out.append("  ");
                i++;
            } else if (ch == '/' && next == '*') {
                inBlockComment = true;
                out.append("  ");
                i++;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
