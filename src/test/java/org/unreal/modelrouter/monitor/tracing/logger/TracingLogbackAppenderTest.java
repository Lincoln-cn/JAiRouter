package org.unreal.modelrouter.monitor.tracing.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TracingLogbackAppender} 的 JSON 输出（issue #211）。
 *
 * <p>本 appender 直接往 {@code System.out} 写（它是容器里 stdout 结构化日志的唯一写出点，
 * 见 {@code logback-spring.xml} 的 {@code json-logs} profile），因此测试捕获 stdout 后断言 JSON 字段：
 * 配了副本标识就必须带上 {@code instanceId}，没配就不能凭空写一个空字段。
 */
@DisplayName("TracingLogbackAppender 的 JSON 输出（issue #211）")
class TracingLogbackAppenderTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;
    private TracingLogbackAppender appender;

    @BeforeEach
    void setUp() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        appender = new TracingLogbackAppender();
        appender.setJsonFormat(true);
        appender.start();
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("配置了副本标识时 JSON 应带上 instanceId")
    void includesInstanceIdWhenConfigured() {
        appender.setInstanceId("jairouter-probe-1");

        appender.append(eventOf("hello"));

        final String out = captured.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("\"instanceId\":\"jairouter-probe-1\""),
                "多副本下需要按副本检索日志，缺 instanceId 就做不到：" + out);
        assertTrue(out.contains("\"message\":\"hello\""), "消息本体应保留：" + out);
    }

    @Test
    @DisplayName("未配置副本标识时不应写出空 instanceId 字段")
    void omitsInstanceIdWhenAbsent() {
        appender.setInstanceId("");

        appender.append(eventOf("hello"));

        final String out = captured.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("instanceId"), "空值不应产生字段（保持既有 JSON 形状）：" + out);
    }

    private static LoggingEvent eventOf(final String message) {
        final LoggerContext context = new LoggerContext();
        final Logger logger = context.getLogger("org.unreal.modelrouter.test");
        return new LoggingEvent("org.unreal.modelrouter.test", logger, Level.INFO, message, null, null);
    }
}
