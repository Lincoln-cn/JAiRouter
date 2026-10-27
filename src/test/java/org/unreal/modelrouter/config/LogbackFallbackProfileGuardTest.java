package org.unreal.modelrouter.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * issue #669 的守卫：{@code logback-spring.xml} 里所有 appender 都只在 {@code <springProfile>} 块内挂到
 * root，一旦当前 profile 不匹配任何一个块（仓库里真实存在的 {@code staging} 就是这种），appender 会全部
 * 「not referenced」，应用日志与启动失败栈一行都不出现。本测试确保存在一个不依赖具体 profile 名字的兜底块。
 */
@DisplayName("#669 logback 兜底 profile 守卫测试")
class LogbackFallbackProfileGuardTest {

    private static final String LOGBACK = "logback-spring.xml";

    @Test
    @DisplayName("存在一个排除已知 profile 的兜底 springProfile 块，并且它挂了 appender")
    void fallbackProfileProvidesRootAppenders() throws IOException {
        String xml = readLogback();

        // 兜底块的写法：name="!(dev | default | test | prod | json-logs)"
        Matcher matcher = Pattern.compile("<springProfile\\s+name=\"!\\(([^\"]*)\\)\">(.*?)</springProfile>",
                Pattern.DOTALL).matcher(xml);
        assertTrue(matcher.find(), "logback-spring.xml 里应存在 name=\"!(...)\" 形式的兜底 springProfile 块");

        String excluded = matcher.group(1);
        for (String known : new String[] {"dev", "default", "test", "prod", "json-logs"}) {
            assertTrue(excluded.contains(known),
                    "兜底块必须显式排除已有的 profile「" + known + "」，否则会与既有块重复挂载；实际：" + excluded);
        }

        String body = matcher.group(2);
        assertTrue(body.contains("<root"), "兜底块必须挂 root，否则等于没兜底");
        for (String appender : new String[] {"CONSOLE", "ASYNC_FILE", "ASYNC_ERROR"}) {
            assertTrue(body.contains("ref=\"" + appender + "\""),
                    "兜底 root 应挂 " + appender + "；实际块内容：" + body.trim());
        }
    }

    @Test
    @DisplayName("反空转：既有的四个 profile 块必须仍能被检出，否则上面的断言可能扫了个空文件")
    void knownProfileBlocksAreStillDetected() throws IOException {
        String xml = readLogback();

        assertTrue(xml.contains("<configuration"), "读到的应该是 logback 配置本身");
        assertTrue(xml.contains("<springProfile name=\"(dev | default) &amp; !json-logs\">"),
                "dev/default 块应存在");
        assertTrue(xml.contains("<springProfile name=\"test &amp; !json-logs\">"), "test 块应存在");
        assertTrue(xml.contains("<springProfile name=\"prod &amp; !json-logs\">"), "prod 块应存在");
        assertTrue(xml.contains("<springProfile name=\"json-logs\">"), "json-logs 块应存在");

        // 兜底块不应把 json-logs 也算进去（那条路径只会重复输出 JSON）
        assertFalse(xml.contains("<springProfile name=\"!\">"),
                "兜底块不能是空排除集 —— 那样会把 json-logs 的 stdout 契约也一并覆盖");
    }

    private static String readLogback() throws IOException {
        try (InputStream in = new ClassPathResource(LOGBACK).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
