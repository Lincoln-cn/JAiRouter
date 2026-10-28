package org.unreal.modelrouter.router.adapter.request;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #725：下游响应体解析失败时，ERROR 日志不得写入响应体原文.
 *
 * <p>响应体含用户 prompt / 模型补全内容（可能还有 PII），此前
 * {@code logger.error("无法解析下游服务的响应体: {}", responseEntity.getBody(), e)} 会把整段正文
 * 落进日志并随采集长期外流。此处断言真实请求路径（{@code processRequest} → JSON 解析失败分支）
 * 只留下「长度 + SHA-256 摘要」，且异常栈仍然保留。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#725 响应体解析失败日志不落正文")
class NonStreamingParseErrorLogTest {

    /** 刻意带上像密钥、中文与 prompt 的片段，任何一段出现在日志里都算泄漏。 */
    /** 非 JSON 起始（`<`）确保 Jackson 立即抛 JsonParseException，而不是「合法 JSON + 尾部垃圾」。 */
    private static final String SECRET_BODY =
            "<html>用户私密提示词-sk-live-abcdef123456 模型补全内容</html>";

    private static final String[] SECRET_FRAGMENTS = {
        "用户私密提示词", "sk-live-abcdef123456", "模型补全内容"
    };

    private static ResponseStatusException driveUnparseableResponse() {
        final NonStreamingRequestProcessor processor = new NonStreamingRequestProcessor(
                new ObjectMapper(),
                mock(RequestBuilder.class),
                mock(AdapterMetricsRecorder.class),
                mock(TokenUsageExtractor.class));

        final WebClient client = mock(WebClient.class);
        final WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        final WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        final WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(BodyInserter.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class))
                .thenReturn(Mono.just(ResponseEntity.ok(SECRET_BODY)));

        final ModelInstance instance = new ModelInstance();
        instance.setName("inst-1");

        final ServerHttpRequest httpRequest = mock(ServerHttpRequest.class);
        when(httpRequest.getAttributes()).thenReturn(new HashMap<>(Map.of()));

        return assertThrows(ResponseStatusException.class, () -> processor.processRequest(
                Map.of("model", "gpt-4"),
                null,
                client,
                "/v1/chat/completions",
                instance,
                ServiceType.chat,
                String.class,
                "openai",
                request -> request,
                response -> response,
                null,
                httpRequest,
                null).block());
    }

    @Test
    @DisplayName("真实解析失败路径：日志只有长度与摘要，正文片段一个都不出现")
    void unparseableBodyIsNotLogged() {
        final Logger logger = (Logger) LoggerFactory.getLogger(NonStreamingRequestProcessor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            final ResponseStatusException error = driveUnparseableResponse();

            assertEquals(500, error.getStatusCode().value(), "解析失败仍按 500 返回");

            final ILoggingEvent event = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .filter(e -> e.getFormattedMessage().contains("无法解析下游服务的响应体"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("没有记录 ERROR 日志：" + appender.list));

            final String message = event.getFormattedMessage();
            assertTrue(message.contains("chars/"), "必须记长度：" + message);
            assertTrue(message.contains("sha256="), "必须记摘要：" + message);
            for (String fragment : SECRET_FRAGMENTS) {
                assertFalse(message.contains(fragment),
                        "ERROR 日志不得含响应体片段 [" + fragment + "]：" + message);
            }
            assertFalse(message.contains("<html>"),
                    "ERROR 日志不得含响应体正文（超过摘要的部分）：" + message);

            assertNotNull(event.getThrowableProxy(),
                    "异常栈必须保留（排障需要异常类型与行号）");
            assertTrue(event.getThrowableProxy().getClassName().contains("Json"),
                    "须保留 Jackson 解析异常类型：" + event.getThrowableProxy().getClassName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("摘要形态：null 体、字符/字节口径、同文同摘要、异文异摘要")
    void summaryShape() {
        assertEquals("null", NonStreamingRequestProcessor.summarizeBodyForLog(null));

        final String chinese = "中文";
        final String chineseSummary = NonStreamingRequestProcessor.summarizeBodyForLog(chinese);
        assertTrue(chineseSummary.startsWith("2 chars/6 bytes "),
                "按字符数 + UTF-8 字节数记（中文 2 字 6 字节）：" + chineseSummary);

        final String a = NonStreamingRequestProcessor.summarizeBodyForLog("abc");
        final String b = NonStreamingRequestProcessor.summarizeBodyForLog("abc");
        final String c = NonStreamingRequestProcessor.summarizeBodyForLog("abd");
        assertEquals(a, b, "同内容摘要必须稳定（可按摘要做日志对齐）");
        assertFalse(a.equals(c), "不同内容摘要必须不同");
        assertTrue(a.matches("3 chars/3 bytes sha256=[0-9a-f]{16}"),
                "摘要格式固定、且只有 16 位十六进制前缀：" + a);
    }

    @Test
    @DisplayName("大响应体摘要长度恒定：不随正文长度增长")
    void summaryLengthDoesNotGrowWithBody() {
        final String huge = "x".repeat(200_000);
        final String summary = NonStreamingRequestProcessor.summarizeBodyForLog(huge);

        assertTrue(summary.length() < 80, "摘要不得携带正文：" + summary);
        assertFalse(summary.contains("xxxx"), "摘要不得含正文片段：" + summary);
    }
}
