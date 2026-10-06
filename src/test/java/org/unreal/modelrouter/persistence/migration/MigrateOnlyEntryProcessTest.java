package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移专用入口的进程级行为（issue #193）。
 *
 * <p>退出码只能在**真实子进程**上断言 —— {@code System.exit} 一旦在测试 JVM 里被触发就会
 * 杀掉测试进程。这与既有的 {@code MigrationExitRunnerWiringTest} 是分工关系：那里只守装配条件
 * （开关打开时注册退出器），这里守另一半：**真的以什么码退出、以及会不会挂住**。</p>
 *
 * <p>两个用例分别对应 issue #193 的两条验收：</p>
 * <ol>
 *   <li>成功：只给 {@code migrate} profile（外加凭据）就能跑完迁移并**以 0 退出**，
 *       不必再额外传 {@code web-application-type=none} / {@code exit-after-run=true}。
 *       证据不靠日志文本（该 profile 组合下 {@code org.unreal.modelrouter.*} 的 INFO 会被过滤），
 *       而是**退出后用 JDBC 打开子进程写的 H2 文件**，确认 schema 真被建出来 —— 那才说明
 *       它确实跑完了迁移，而不是「提前以 0 退出」。</li>
 *   <li>失败：启动或迁移失败时**以非 0 退出且迅速结束**。这一条是能删掉
 *       {@code activeDeadlineSeconds} 的前提 —— 旧实现里失败后非守护线程会留住 JVM
 *       （本会话实测过同一场景挂住 &gt;240 秒），Job 只能靠超时强杀，成败无从判读。</li>
 * </ol>
 *
 * <p>用 {@code prod,json-logs,migrate} 三个 profile，与 K8s Job 实际使用的组合一致。</p>
 */
@DisplayName("迁移专用入口的进程级行为（issue #193）")
class MigrateOnlyEntryProcessTest {

    private static final String PROFILES = "prod,json-logs,migrate";
    private static final String MAIN_CLASS = "org.unreal.modelrouter.ModelRouterApplication";
    private static final String FAILED_FAST = "应用启动失败，进程以退出码 1 结束";
    /** 单次子进程的上限；成功路径实测约 80 秒，这里留足余量 */
    private static final long TIMEOUT_SECONDS = 240;

    @Test
    @DisplayName("成功路径：只给 migrate profile 即完成迁移并以 0 退出")
    void exitsZeroAfterMigration() throws Exception {
        Path workDir = Files.createTempDirectory("migrate-entry-ok-");
        Path dbFile = workDir.resolve("db").resolve("migrate");
        Files.createDirectories(dbFile.getParent());
        String url = "jdbc:h2:file:" + dbFile.toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE";

        Child child = launch(workDir, url);

        assertTrue(child.finished(), "迁移专用进程应在 " + TIMEOUT_SECONDS + " 秒内自行退出；"
                + "未退出说明 `migrate` profile 的「非 Web + 跑完即退出」没生效");
        assertEquals(0, child.exitCode(),
                "迁移成功后应以 0 退出。子进程输出：\n" + child.output());
        assertTrue(tableCount(url) >= 20,
                "子进程应在退出前把 schema 建出来；库文件 " + dbFile + " 里的表数不足以说明迁移跑过");
        // 建表者必须是 Flyway（issue #192）：只断言「表存在」证明不了 —— H2 上 ddl-auto: update
        // 自己也会建表。历史表里 V1/V2 两条记录才是「H2 走的是版本化迁移」的直接证据。
        assertEquals(List.of("1|SQL", "2|SQL"), migrationHistory(url),
                "H2 路径应由 Flyway 顺序执行 V1、V2。子进程输出：\n" + child.output());
    }

    @Test
    @DisplayName("失败路径：启动/迁移失败时以非 0 退出且不挂住")
    void exitsNonZeroOnFailureWithoutHanging() throws Exception {
        Path workDir = Files.createTempDirectory("migrate-entry-fail-");
        Child child = launch(workDir, "jdbc:no-such-db://127.0.0.1:1/none");

        assertTrue(child.finished(), "失败后进程仍必须自行结束（这正是能去掉 activeDeadlineSeconds 的前提）；"
                + "未结束说明非守护线程又把 JVM 留住了");
        assertNotEquals(0, child.exitCode(),
                "启动/迁移失败必须以非 0 退出，否则 K8s Job 会把失败的迁移报成成功");
        Path log = workDir.resolve("logs").resolve("jairouter-all.log");
        assertTrue(logContains(log, FAILED_FAST),
                "失败结论应来自 main 的失败分支（而不是异常直接冒出 main）。日志 " + log + " 内容：\n"
                        + child.output());
    }

    // ==================== 子进程 ====================

    private static Child launch(final Path workDir, final String datasourceUrl)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                javaBinary(),
                "-cp", System.getProperty("java.class.path"),
                MAIN_CLASS,
                "--spring.profiles.active=" + PROFILES,
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + datasourceUrl,
                "--spring.datasource.username=sa",
                // 避开生产安全前置检查：它查的是密钥/口令强度，与本用例要验的退出码无关
                "--JWT_SECRET=probe-secret-key-for-migrate-entry-0123456789",
                "--INITIAL_ADMIN_PASSWORD=ProbeAdminPwd123!"));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("JAIRouter_SKIP_AUTH_WARNING", "true");
        builder.environment().put("JAROUTER_SKIP_PASSWORD_WARNING", "true");

        Process process = builder.start();
        // 边跑边抽干 stdout：否则管道写满会把子进程卡住，测出来的「挂住」就是假的
        StringBuilder sink = new StringBuilder();
        Thread drain = new Thread(() -> drain(process, sink), "child-output-drain");
        drain.setDaemon(true);
        drain.start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
        }
        drain.join(TimeUnit.SECONDS.toMillis(10));
        return new Child(finished, finished ? process.exitValue() : -1, sink.toString());
    }

    private static void drain(final Process process, final StringBuilder sink) {
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                synchronized (sink) {
                    sink.append(buffer, 0, read);
                }
            }
        } catch (IOException ignored) {
            // 进程被强杀时读端会报错，属预期
        }
    }

    /** 子进程退出后打开它写的 H2 文件数表（IFEXISTS 保证不会顺手建一个新库） */
    private static int tableCount(final String url) throws Exception {
        String existing = url.replace("DB_CLOSE_DELAY=-1;", "") + ";IFEXISTS=TRUE";
        try (Connection connection = DriverManager.getConnection(existing, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * 子进程写的 Flyway 历史（{@code version|type}），按 {@code installed_rank} 排序。
     *
     * <p>该库的 URL 带 {@code DATABASE_TO_UPPER=FALSE}，历史表与列名都是**小写**，必须照小写引用；
     * 表里还有一行 {@code version} 为 NULL 的内部记录，排除掉。
     */
    private static List<String> migrationHistory(final String url) throws Exception {
        String existing = url.replace("DB_CLOSE_DELAY=-1;", "") + ";IFEXISTS=TRUE";
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(existing, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT version || '|' || type FROM flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
        }
        return rows;
    }

    private static boolean logContains(final Path log, final String needle) {
        if (!Files.exists(log)) {
            return false;
        }
        try {
            return Files.readString(log, StandardCharsets.UTF_8).contains(needle);
        } catch (IOException e) {
            return false;
        }
    }

    private static String javaBinary() {
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    private record Child(boolean finished, int exitCode, String output) {
    }
}
