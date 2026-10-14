package org.unreal.modelrouter.config.core.validator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #381：{@code SensitiveConfigValidator} 的 errors 桶**打印与阻断解耦** ——
 * 非严格模式下错误级发现**必须可见**（此前既不打印也不阻断），而阻断语义不得被削弱。
 *
 * <p>钉住三条：① 非严格 + 硬编码密钥 ⇒ 有 ERROR 日志且**不抛**；② 严格 + 缺失密钥 ⇒ 有 ERROR **且抛**；
 * ③ 非严格 + 缺失密钥 ⇒ 只有 WARN、没有 ERROR（warning 情形不该被升级成错误噪声）。</p>
 */
@DisplayName("SensitiveConfigValidator：errors 桶的可见性与阻断语义（issue #381）")
class SensitiveConfigValidatorErrorVisibilityTest {

    /**
     * 必须 ≥32 字符**且**含硬编码关键词 —— 因为校验顺序是「空 → 长度过短 → 硬编码」
     * （{@code SensitiveConfigValidator:76-89}）：长度不足会先落到 WARN 分支，命中不了 errors 桶。
     */
    private static final String HARDCODED_LIKE = "changeme-" + "x".repeat(40);

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(SensitiveConfigValidator.class);
    }

    private static SensitiveConfigValidator validator(final String jwtSecret, final boolean strictMode) {
        SensitiveConfigValidator validator = new SensitiveConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(validator, "initialAdminPassword", "a-strong-admin-password");
        ReflectionTestUtils.setField(validator, "gpustackApiToken", "gpustack-token");
        ReflectionTestUtils.setField(validator, "strictMode", strictMode);
        return validator;
    }

    private static boolean hasLevel(ListAppender<ILoggingEvent> appender, Level level, String fragment) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment));
    }

    @Test
    @DisplayName("① 非严格模式 + 硬编码样密钥 ⇒ 打印 ERROR，但**不阻断启动**（本条修复的核心）")
    void nonStrictMode_stillLogsErrors_butDoesNotThrow() {
        Logger logger = logger();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SensitiveConfigValidator validator = validator(HARDCODED_LIKE, false);
            assertDoesNotThrow(() -> validator.onApplicationEvent(null),
                    "非严格模式不得因 errors 桶而阻断启动");
            assertTrue(hasLevel(appender, Level.ERROR, "硬编码"),
                    "错误级发现必须打印出来（issue #381 修复点）：" + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("② 严格模式 + 缺失 JWT_SECRET ⇒ 打印 ERROR **并抛异常**（阻断语义不得削弱）")
    void strictMode_missingSecret_stillThrows() {
        Logger logger = logger();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SensitiveConfigValidator validator = validator("", true);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(null),
                    "严格模式下 errors 非空必须阻断启动");
            assertTrue(hasLevel(appender, Level.ERROR, "JWT_SECRET"),
                    "阻断前必须打印错误明细：" + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("③ 非严格模式 + 缺失 JWT_SECRET ⇒ 只有 WARN、没有 ERROR（不把 warning 升级成噪声）")
    void nonStrictMode_missingSecret_isWarningOnly() {
        Logger logger = logger();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SensitiveConfigValidator validator = validator("", false);
            assertDoesNotThrow(() -> validator.onApplicationEvent(null));
            assertTrue(hasLevel(appender, Level.WARN, "JWT_SECRET"), "缺失密钥应有警告：" + appender.list);
            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR),
                    "缺失密钥在非严格模式下不得产生 ERROR：" + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
