package org.unreal.modelrouter.monitor.monitoring.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「小时统计必须经由代理调用」守卫（#857）.
 *
 * <p>背景：{@code flushHourlyStatsToDatabase()} 上有 {@code @Transactional}，但原来是用
 * {@code scheduler.scheduleAtFixedRate(this::flushHourlyStatsToDatabase, …)} 与
 * {@code shutdown()} 里的直接调用触发的 —— 两者都是**同类自调用**，不经过 Spring 代理，
 * 事务不会开启，{@code entityManager.merge(...)} 抛 {@code TransactionRequiredException}
 * 被 catch 成一条 warn，于是「小时统计」永远不落库，而日志里只有一句「保存小时统计失败」。</p>
 *
 * <p>修法是把调用改成 {@code selfProvider.getObject().flushHourlyStatsToDatabase()}：拿到的
 * {@link org.springframework.beans.factory.ObjectProvider} 返回容器里的代理对象，调用会经过事务拦截器。
 * 这类「代理被绕过」的缺陷在运行时很难被单测抓到（要起真事务管理器 + 真库才好断言落库），
 * 但它在源码里是可枚举的 —— 所以用一道源码守卫把写法钉住：一旦有人把它改回自调用，
 * 测试立刻失败并指出原因，而不是等线上发现统计表是空的。</p>
 *
 * <p>扫描前会把注释剥掉：解释性 javadoc 里出现这种写法是正常的，不能算违规。</p>
 *
 * <p>⚠️ 已知边界（如实记录）：本守卫检查的是「调用写法」，不是「事务真的提交了」。真正的落库证据
 * 需要 Spring 事务管理器 + 数据库，那属于另一类集成测试；这里只保证回归不会悄悄发生。</p>
 */
@DisplayName("#857 小时统计经由代理调用守卫")
class ExceptionHourlyStatsProxyInvocationTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/org/unreal/modelrouter/monitor/monitoring/error/ExceptionPersistenceService.java");

    private static final Pattern TRANSACTIONAL_FLUSH = Pattern.compile(
            "@Transactional\\s*\\r?\\n\\s*public\\s+void\\s+flushHourlyStatsToDatabase\\s*\\(");

    private static String rawSource() {
        try {
            return Files.readString(SOURCE, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 去掉块注释与行注释后的代码文本（逐行状态机，不用正则）。
     *
     * <p>只用于「调用写法」判定：javadoc 里解释「不要写成自调用」是应当的，若把注释也算进判据，
     * 守卫会逼着人删掉解释，反而更糟。</p>
     */
    private static String source() {
        final StringBuilder out = new StringBuilder();
        boolean inBlock = false;
        for (final String line : rawSource().split("\n", -1)) {
            final String trimmed = line.trim();
            if (inBlock) {
                final int end = trimmed.indexOf("*/");
                if (end >= 0) {
                    inBlock = false;
                    out.append(trimmed.substring(end + 2)).append('\n');
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                final int end = trimmed.indexOf("*/");
                if (end >= 0) {
                    out.append(trimmed.substring(end + 2)).append('\n');
                } else {
                    inBlock = true;
                }
                continue;
            }
            final int lineComment = line.indexOf("//");
            out.append(lineComment >= 0 ? line.substring(0, lineComment) : line).append('\n');
        }
        return out.toString();
    }

    @Test
    @DisplayName("857-000 守卫确实读到了目标源码（防空转）")
    void scannerActuallyReadsTheSource() {
        final String text = rawSource();
        assertFalse(text.isEmpty(), "读取到的源码为空，说明扫描路径失效（工作目录不是项目根？）");
        assertTrue(text.contains("class ExceptionPersistenceService"),
                "读到的不是 ExceptionPersistenceService 的源码");
        assertTrue(text.contains("flushHourlyStatsToDatabase"),
                "源码里应能找到 flushHourlyStatsToDatabase");
        assertFalse(source().isBlank(), "剥注释后的代码文本为空，说明剥离逻辑失效");
    }

    @Test
    @DisplayName("857-001 不允许用 this::flushHourlyStatsToDatabase 自调用")
    void mustNotScheduleThroughThisReference() {
        assertFalse(source().contains("this::flushHourlyStatsToDatabase"),
                "自调用不会经过 Spring 代理，@Transactional 不生效：请改为 selfProvider.getObject().flushHourlyStatsToDatabase()");
    }

    @Test
    @DisplayName("857-002 定时任务与 shutdown 都必须经由 selfProvider 调用")
    void bothTriggerPointsGoThroughTheProxy() {
        final int occurrences = countOccurrences(source(), "selfProvider.getObject().flushHourlyStatsToDatabase()");
        assertTrue(occurrences >= 2,
                "应有 2 处经由代理的调用（每 5 分钟的定时刷新 + shutdown 时的收尾刷新），实际找到 " + occurrences + " 处");
    }

    @Test
    @DisplayName("857-003 flushHourlyStatsToDatabase 仍保留 @Transactional")
    void flushMethodStaysTransactional() {
        assertTrue(TRANSACTIONAL_FLUSH.matcher(source()).find(),
                "flushHourlyStatsToDatabase 必须保留 @Transactional，否则即使经过代理也不会开启事务");
        assertEquals(1, countOccurrences(source(), "public void flushHourlyStatsToDatabase()"),
                "flushHourlyStatsToDatabase 应只有一处声明");
    }

    private static int countOccurrences(final String text, final String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
