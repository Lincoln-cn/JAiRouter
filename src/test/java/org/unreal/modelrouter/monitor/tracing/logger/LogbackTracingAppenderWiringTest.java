package org.unreal.modelrouter.monitor.tracing.logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * logback 配置的结构约束测试（issue #264）.
 *
 * <p>{@code TracingLogbackAppender} 内部同步做 JSON 序列化并直接写 {@code System.out}，
 * 若被裸挂在 {@code <logger>} / {@code <root>} 上，产生日志的线程（含请求线程）就要承担该开销。
 * 因此约定：它只能经 {@code AsyncAppender}（{@code ASYNC_TRACING}）挂载。</p>
 *
 * <p>本用例把该约定固化为断言 —— 将来有人把某处改回裸挂 {@code TRACING_APPENDER}，
 * 或把 {@code ASYNC_TRACING} 的参数改坏（例如关掉 {@code neverBlock}），都会直接判红。</p>
 */
@DisplayName("logback 配置：TRACING_APPENDER 必须经 AsyncAppender 挂载（issue #264）")
class LogbackTracingAppenderWiringTest {

    private static final String CONFIG_PATH = "src/main/resources/logback-spring.xml";

    @Test
    @DisplayName("不存在对 TRACING_APPENDER 的裸挂（父元素必须是 AsyncAppender 定义）")
    void tracingAppenderIsNeverAttachedRaw() throws Exception {
        final Document doc = parseConfig();

        final List<String> rawAttachments = new ArrayList<>();
        final NodeList refs = doc.getElementsByTagName("appender-ref");
        for (int i = 0; i < refs.getLength(); i++) {
            final Element ref = (Element) refs.item(i);
            if (!"TRACING_APPENDER".equals(ref.getAttribute("ref"))) {
                continue;
            }
            final Element parent = (Element) ref.getParentNode();
            final boolean viaAsyncWrapper = "appender".equals(parent.getTagName())
                    && "ch.qos.logback.classic.AsyncAppender".equals(parent.getAttribute("class"));
            if (!viaAsyncWrapper) {
                rawAttachments.add("<" + parent.getTagName()
                        + (parent.getAttribute("name").isEmpty() ? "" : " name=\"" + parent.getAttribute("name") + "\"")
                        + ">");
            }
        }

        assertTrue(rawAttachments.isEmpty(),
                "TRACING_APPENDER 仍被裸挂在这些位置（应改挂 ASYNC_TRACING）: " + rawAttachments);
    }

    @Test
    @DisplayName("ASYNC_TRACING 存在、包装 TRACING_APPENDER 且 neverBlock=true")
    void asyncTracingWrapperIsConfigured() throws Exception {
        final Document doc = parseConfig();

        final Element asyncTracing = findAppender(doc, "ASYNC_TRACING");
        assertNotNull(asyncTracing, "缺少 ASYNC_TRACING 的 appender 定义");
        assertEquals("ch.qos.logback.classic.AsyncAppender", asyncTracing.getAttribute("class"),
                "ASYNC_TRACING 必须是 AsyncAppender");

        final Element inner = firstChildElement(asyncTracing, "appender-ref");
        assertNotNull(inner, "ASYNC_TRACING 必须包一个 appender-ref");
        assertEquals("TRACING_APPENDER", inner.getAttribute("ref"),
                "ASYNC_TRACING 应包装 TRACING_APPENDER");

        assertEquals("true", childText(asyncTracing, "neverBlock"),
                "ASYNC_TRACING 必须 neverBlock=true（队列满时丢弃而不阻塞日志线程）");
        assertEquals("1024", childText(asyncTracing, "queueSize"),
                "ASYNC_TRACING 的 queueSize 应与其它异步 appender 对齐（1024）");
    }

    private static Document parseConfig() throws Exception {
        final File config = new File(CONFIG_PATH);
        assertTrue(config.isFile(), "找不到 logback 配置: " + config.getAbsolutePath());
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config);
    }

    private static Element findAppender(final Document doc, final String name) {
        final NodeList appenders = doc.getElementsByTagName("appender");
        for (int i = 0; i < appenders.getLength(); i++) {
            final Element candidate = (Element) appenders.item(i);
            if (name.equals(candidate.getAttribute("name"))) {
                return candidate;
            }
        }
        return null;
    }

    private static Element firstChildElement(final Element parent, final String tag) {
        final NodeList children = parent.getElementsByTagName(tag);
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getParentNode() == parent) {
                return (Element) children.item(i);
            }
        }
        return null;
    }

    private static String childText(final Element parent, final String tag) {
        final Element child = firstChildElement(parent, tag);
        return child == null ? null : child.getTextContent().trim();
    }
}
