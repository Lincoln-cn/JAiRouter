package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SseResponseSanitizer} 的行级处理测试（issue #560 第二阶段）。
 *
 * <p>这些用例全部在「字节 → 行 → 脱敏 → 字节」这一层跑，不依赖 WebFlux 栈：SSE 的坑几乎都在
 * 分块边界与分帧语义上，直接用多块 {@link DataBuffer} 构造比走过滤器更容易钉住。</p>
 */
class SseResponseSanitizerTest {

    private static final String PHONE = "13800000000";
    private static final String MASKED = "***********";

    private SanitizationService sanitizationService;
    private DefaultDataBufferFactory bufferFactory;
    private List<String> warnings;

    @BeforeEach
    void setUp() {
        sanitizationService = mock(SanitizationService.class);
        bufferFactory = new DefaultDataBufferFactory();
        warnings = new ArrayList<>();
        when(sanitizationService.sanitizeResponse(anyString(), eq("application/json")))
                .thenAnswer(invocation -> Mono.just(
                        invocation.<String>getArgument(0).replace(PHONE, MASKED)));
    }

    private SseResponseSanitizer sanitizer(final int maxLineBytes) {
        return new SseResponseSanitizer(sanitizationService, bufferFactory, maxLineBytes, warnings::add);
    }

    private Flux<DataBuffer> buffers(final String... chunks) {
        return Flux.fromArray(chunks).map(chunk -> bufferFactory.wrap(chunk.getBytes(StandardCharsets.UTF_8)));
    }

    private String run(final SseResponseSanitizer sanitizer, final String... chunks) {
        return sanitizer.sanitize(buffers(chunks))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block();
    }

    @Test
    @DisplayName("SSE-001: 一帧一条 data 行 —— 只替换负载，前缀/行尾/空行不变")
    void dataLineIsSanitizedFramingPreserved() {
        String output = run(sanitizer(1_048_576), "data: {\"phone\":\"" + PHONE + "\"}\n\n");

        assertEquals("data: {\"phone\":\"" + MASKED + "\"}\n\n", output);
    }

    @Test
    @DisplayName("SSE-002: event/id/注释/空行/[DONE] 全部原样透传，不调用脱敏服务")
    void nonDataLinesUntouched() {
        String frames = "event: message\nid: 7\n: keep-alive\ndata: [DONE]\n\n";
        String output = run(sanitizer(1_048_576), frames);

        assertEquals(frames, output);
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("SSE-003: 一行被网络切成两块 —— 拼回后再脱敏，不会输出半行")
    void lineSplitAcrossBuffersIsReassembled() {
        String output = run(sanitizer(1_048_576),
                "data: {\"phone\":\"1380",
                "0000000\"}\n\n");

        assertEquals("data: {\"phone\":\"" + MASKED + "\"}\n\n", output);
    }

    @Test
    @DisplayName("SSE-004: CRLF 行尾原样保留")
    void crlfTerminatorPreserved() {
        String output = run(sanitizer(1_048_576), "data: {\"phone\":\"" + PHONE + "\"}\r\n\r\n");

        assertEquals("data: {\"phone\":\"" + MASKED + "\"}\r\n\r\n", output);
    }

    @Test
    @DisplayName("SSE-005: 脱敏异常只回退该帧原文，不中断整条流")
    void sanitizerFailureFallsBackToOriginalLine() {
        when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("regex timeout")));

        String frames = "data: {\"phone\":\"" + PHONE + "\"}\n\ndata: {\"phone\":\"" + PHONE + "\"}\n\n";
        String output = run(sanitizer(1_048_576), frames);

        assertEquals(frames, output, "失败帧必须原样透传，后面的帧要继续处理");
        assertEquals(2, warnings.size(), "每一帧失败都应留一条告警");
    }

    @Test
    @DisplayName("SSE-006: 单行超过上限时该行原样透传并告警，缓冲不会被撑爆")
    void oversizedLinePassesThrough() {
        String huge = "data: {\"pad\":\"" + "x".repeat(4_096) + "\"}\n\n";
        String output = run(sanitizer(1_024), huge);

        assertEquals(huge, output);
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).contains("超过上限"));
    }

    @Test
    @DisplayName("SSE-007: 流末尾缺少换行 —— 残行也要尝试脱敏，不能直接丢弃")
    void trailingLineWithoutNewlineIsProcessed() {
        String output = run(sanitizer(1_048_576), "data: {\"phone\":\"" + PHONE + "\"}");

        assertEquals("data: {\"phone\":\"" + MASKED + "\"}", output);
    }

    @Test
    @DisplayName("SSE-008: 多帧连续到达 —— 按顺序逐帧替换，帧数不变")
    void multipleFramesKeepOrder() {
        String frames = "data: {\"i\":1,\"phone\":\"" + PHONE + "\"}\n\n"
                + "data: {\"i\":2,\"phone\":\"" + PHONE + "\"}\n\n";

        String output = run(sanitizer(1_048_576), frames);

        assertEquals("data: {\"i\":1,\"phone\":\"" + MASKED + "\"}\n\n"
                + "data: {\"i\":2,\"phone\":\"" + MASKED + "\"}\n\n", output);
    }

    @Test
    @DisplayName("SSE-009: 非 data 前缀但含手机号的行不动 —— 脱敏面只限 data 负载")
    void nonDataPayloadNotTouched() {
        String line = "retry: 3000\n";

        assertEquals(line, run(sanitizer(1_048_576), line));
    }

    @Test
    @DisplayName("SSE-010: 空流不产生任何输出")
    void emptyStreamProducesNothing() {
        StepVerifier.create(sanitizer(1_048_576).sanitize(Flux.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("SSE-011: writeAndFlushWith 的分片模式 —— 残行跨分片延续，tail 收尾")
    void segmentModeKeepsPendingAcrossFlushes() {
        SseResponseSanitizer sanitizer = sanitizer(1_048_576);
        // 第一次 flush：只到半行（"data: {\"phone\":\"1"）
        // 第二次 flush：补齐剩余部分 + 换行
        String first = "data: {\"phone\":\"" + PHONE.substring(0, 5);
        String second = PHONE.substring(5) + "\"}\n";

        String output = Flux.concat(
                        sanitizer.sanitizeSegment(buffers(first)),
                        sanitizer.sanitizeSegment(buffers(second)),
                        sanitizer.tail())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block();

        assertEquals("data: {\"phone\":\"" + MASKED + "\"}\n", output);
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("SSE-012: 分片模式的末尾残行由 tail 输出并脱敏")
    void segmentModeTailFlushedAtEnd() {
        SseResponseSanitizer sanitizer = sanitizer(1_048_576);

        String output = Flux.concat(
                        sanitizer.sanitizeSegment(buffers("data: {\"phone\":\"")),
                        sanitizer.sanitizeSegment(buffers(PHONE)),
                        sanitizer.tail())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block();

        assertEquals("data: {\"phone\":\"" + MASKED, output);
    }

    // ==================== issue #684：跨块 UTF-8 字符边界 ====================

    /**
     * 按「原始字节的绝对偏移」切块，用来构造把多字节字符切成两半的分块。
     */
    private Flux<DataBuffer> buffersAtBytes(final String text, final int... cutPoints) {
        final byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        final List<byte[]> parts = new ArrayList<>();
        int start = 0;
        for (int cut : cutPoints) {
            parts.add(Arrays.copyOfRange(raw, start, cut));
            start = cut;
        }
        parts.add(Arrays.copyOfRange(raw, start, raw.length));
        return Flux.fromIterable(parts).map(bufferFactory::wrap);
    }

    private String runBytes(final SseResponseSanitizer sanitizer, final String text, final int... cutPoints) {
        return sanitizer.sanitize(buffersAtBytes(text, cutPoints))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block();
    }

    private String joinBuffers(final Flux<DataBuffer> flux) {
        return flux.map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block();
    }

    @Test
    @DisplayName("SSE-013: 汉字被从中间切成三块 —— 不得产出 U+FFFD，内容逐字还原")
    void utf8CharSplitAcrossBuffersIsNotCorrupted() {
        String frame = "data: {\"msg\":\"中文测试\"}\n\n";
        int byteStart = "data: {\"msg\":\"".getBytes(StandardCharsets.UTF_8).length;

        String output = runBytes(sanitizer(1_048_576), frame, byteStart + 1, byteStart + 2);

        assertEquals(frame, output, "跨块汉字必须原样还原");
        assertFalse(output.contains("\uFFFD"), "不得出现替换字符");
    }

    @Test
    @DisplayName("SSE-014: 四字节 emoji 被逐字节切成五块 —— 仍然逐字还原")
    void fourByteEmojiSplitByteByByte() {
        String frame = "data: {\"msg\":\"🚀发射\"}\n\n";
        int byteStart = "data: {\"msg\":\"".getBytes(StandardCharsets.UTF_8).length;

        String output = runBytes(sanitizer(1_048_576), frame,
                byteStart + 1, byteStart + 2, byteStart + 3, byteStart + 4);

        assertEquals(frame, output);
        assertFalse(output.contains("\uFFFD"));
    }

    @Test
    @DisplayName("SSE-015: 汉字被切到两个 flush —— 残字节跨 flush 保留，tail 输出完整行")
    void incompleteBytesSurviveAcrossFlushes() {
        SseResponseSanitizer sseSanitizer = sanitizer(1_048_576);
        String frame = "data: {\"msg\":\"中\"}\n";
        byte[] raw = frame.getBytes(StandardCharsets.UTF_8);
        int cut = "data: {\"msg\":\"".getBytes(StandardCharsets.UTF_8).length + 1;

        String output = joinBuffers(Flux.concat(
                sseSanitizer.sanitizeSegment(Flux.just(bufferFactory.wrap(Arrays.copyOf(raw, cut)))),
                sseSanitizer.sanitizeSegment(Flux.just(
                        bufferFactory.wrap(Arrays.copyOfRange(raw, cut, raw.length)))),
                sseSanitizer.tail()));

        assertEquals(frame, output, "跨 flush 的汉字不能被替换或丢弃");
        assertFalse(output.contains("\uFFFD"));
    }

    @Test
    @DisplayName("SSE-016: 流结束时字节仍不完整 —— 按替换字符收尾，有效前缀不丢")
    void truncatedTrailingCharacterBecomesReplacementNotLost() {
        String frame = "data: {\"msg\":\"ab中";
        byte[] raw = frame.getBytes(StandardCharsets.UTF_8);
        byte[] truncated = Arrays.copyOf(raw, raw.length - 2);

        String output = joinBuffers(sanitizer(1_048_576).sanitize(Flux.just(bufferFactory.wrap(truncated))));

        assertTrue(output.startsWith("data: {\"msg\":\"ab"),
                "残缺字符之前的有效内容不能丢：" + output);
        assertTrue(output.endsWith("\uFFFD"),
                "流结束时的不完整字节只能按替换字符输出：" + output);
    }

    @Test
    @DisplayName("SSE-017: 完整 UTF-8 前缀长度计算 —— 汉字/emoji 的每个截断点")
    void completeUtf8PrefixLengthHandlesEveryTruncationPoint() {
        byte[] hanzi = "中".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, SseResponseSanitizer.completeUtf8PrefixLength(Arrays.copyOf(hanzi, 1)));
        assertEquals(0, SseResponseSanitizer.completeUtf8PrefixLength(Arrays.copyOf(hanzi, 2)));
        assertEquals(3, SseResponseSanitizer.completeUtf8PrefixLength(hanzi));

        byte[] emoji = "🚀".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, SseResponseSanitizer.completeUtf8PrefixLength(Arrays.copyOf(emoji, 3)));
        assertEquals(4, SseResponseSanitizer.completeUtf8PrefixLength(emoji));

        byte[] mixed = "ab中".getBytes(StandardCharsets.UTF_8);
        assertEquals(2, SseResponseSanitizer.completeUtf8PrefixLength(Arrays.copyOf(mixed, 3)));
        assertEquals(5, SseResponseSanitizer.completeUtf8PrefixLength(mixed));

        assertEquals(2, SseResponseSanitizer.completeUtf8PrefixLength("ab".getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, SseResponseSanitizer.completeUtf8PrefixLength(new byte[] {(byte) 0xFF, (byte) 0xFE}));
    }

    @Test
    @DisplayName("SSE-018: 汉字跨块时同一帧的手机号仍被脱敏 —— 解码修复不影响脱敏")
    void sanitizationStillAppliesWhenCharactersSplit() {
        String frame = "data: {\"msg\":\"中文\",\"phone\":\"" + PHONE + "\"}\n\n";
        int byteStart = "data: {\"msg\":\"".getBytes(StandardCharsets.UTF_8).length;

        String output = runBytes(sanitizer(1_048_576), frame, byteStart + 1);

        assertTrue(output.contains(MASKED), "手机号必须被替换：" + output);
        assertTrue(output.contains("中文"), "汉字必须原样保留：" + output);
        assertFalse(output.contains("\uFFFD"));
    }
}
