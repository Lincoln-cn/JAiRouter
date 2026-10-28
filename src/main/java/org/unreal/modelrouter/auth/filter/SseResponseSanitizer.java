package org.unreal.modelrouter.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 流式响应（{@code text/event-stream}）逐帧脱敏器（issue #560 第二阶段）。
 *
 * <p><b>为什么不能复用整段缓冲</b>：SSE 是无限流，{@code DataBufferUtils.join} 会把整条流读进堆，
 * 既是 #594 修的 OOM 风险，也根本等不到「流结束」。所以这里做的是<b>行级流式处理</b>：
 * 按 {@code \n} 切行、只对 {@code data:} 行的负载做脱敏，其余行（{@code event:} / {@code id:} /
 * {@code retry:} / 注释 / 空行）逐字透传，SSE 分帧语义不变。</p>
 *
 * <p><b>三条硬约束</b>：</p>
 * <ol>
 *   <li><b>不破坏分帧</b>：行尾（{@code \n} 或 {@code \r\n}）按原样写回；不足一行时先攒着，
 *       等下一块数据补齐 —— 网络分块不一定按行切。</li>
 *   <li><b>失败不掐流</b>：单行脱敏异常（正则超长、服务不可用等）只回退该行为原文并告警，
 *       绝不 {@code Mono.error} —— 流式响应中途终止对客户端来说比漏一行脱敏更糟。</li>
 *   <li><b>单行有上限</b>：超过 {@code maxLineBytes} 的行不再攒（防止一个超长 delta 把堆吃满），
 *       该行原样透传并告警一次。</li>
 *   <li><b>按字符边界解码</b>（issue #684）：{@code DataBuffer} 的边界与 UTF-8 字符边界无关，
 *       一个 3 字节汉字、4 字节 emoji 经常被切成两块。每块各自 {@code new String(bytes, UTF_8)}
 *       会让两块都产出 U+FFFD 且原字节永久丢失，因此这里攒字节、只解到最后一个完整字符为止，
 *       不完整的尾部（0–3 字节）留到下一块。</li>
 * </ol>
 *
 * <p>不是 Spring Bean：每个响应体构造一个实例，内部持有「未完成行」的缓冲状态。</p>
 */
@Slf4j
final class SseResponseSanitizer {

    /** SSE 数据行前缀。 */
    private static final String DATA_PREFIX = "data:";
    /** OpenAI 协议流结束哨兵，不能被当成 JSON 去脱敏。 */
    private static final String DONE_SENTINEL = "[DONE]";
    /** 「没有残留字节」的空数组，避免每次清空都新建对象。 */
    private static final byte[] NO_INCOMPLETE_BYTES = new byte[0];

    private final SanitizationService sanitizationService;
    private final DataBufferFactory bufferFactory;
    private final int maxLineBytes;
    private final Consumer<String> warningSink;

    /** 尚未遇到换行的残行（跨 DataBuffer）。 */
    private final StringBuilder pending = new StringBuilder();
    /** 上一块末尾不完整的 UTF-8 字节（0–3 个），等下一块补齐再解码（issue #684）。 */
    private byte[] incompleteBytes = NO_INCOMPLETE_BYTES;
    /** 当前残行已超上限：该行完成后原样透传。 */
    private boolean pendingOversized;

    SseResponseSanitizer(final SanitizationService sanitizationService,
                         final DataBufferFactory bufferFactory,
                         final int maxLineBytes,
                         final Consumer<String> warningSink) {
        this.sanitizationService = sanitizationService;
        this.bufferFactory = bufferFactory;
        this.maxLineBytes = Math.max(1024, maxLineBytes);
        this.warningSink = warningSink;
    }

    /**
     * 把 SSE 字节流转换成「逐行脱敏后的字节流」。
     *
     * @param body 原始响应体（由 WebFlux 逐块推送）
     * @return 处理后的响应体；行序与分帧保持不变
     */
    Flux<DataBuffer> sanitize(final Flux<DataBuffer> body) {
        // 每次订阅都从干净的缓冲状态开始（响应体理论上只被订阅一次，但不能依赖这一点）
        return Flux.defer(() -> {
            pending.setLength(0);
            pendingOversized = false;
            incompleteBytes = NO_INCOMPLETE_BYTES;

            // concatMap 保证「切行」与「脱敏」都按到达顺序串行，避免并发改 pending
            return sanitizeSegment(body).concatWith(tail());
        });
    }

    /**
     * 处理「一段」字节流，不重置状态、也不输出尾行。
     *
     * <p>SSE 在 WebFlux 里走 {@code writeAndFlushWith}：外层是若干次 flush，每次 flush 的内层才是
     * 一个字节分片，同一个事件可能被拆到两个分片里。因此按分片处理时必须复用同一个实例，
     * 让跨分片的残行留在 {@code pending} 里，最后再统一 {@link #tail()} 收尾。</p>
     */
    Flux<DataBuffer> sanitizeSegment(final Flux<DataBuffer> body) {
        return body.concatMap(this::splitIntoLines)
                .concatMap(line -> line.raw() ? Mono.just(line.text()) : sanitizeLine(line.text()))
                .map(this::toBuffer);
    }

    /**
     * 输出末尾尚未换行的残行（流结束时调用一次）。
     */
    Flux<DataBuffer> tail() {
        return Flux.defer(this::flushTail);
    }

    /**
     * 把一块字节转成完整行；末尾残行留在 {@link #pending} 里等下一块。
     *
     * <p>#684：解码走 {@link #decodeUtf8(byte[])}，只在 UTF-8 字符边界处切，不完整的尾部字节
     * 留到下一块 —— 否则跨块的多字节字符会被两块各自替换成 U+FFFD。</p>
     */
    private Flux<Line> splitIntoLines(final DataBuffer buffer) {
        try {
            final byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return Flux.fromIterable(appendAndExtract(decodeUtf8(bytes)));
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    /**
     * 把本块字节接到上一次的残留字节后面，只解码到最后一个完整 UTF-8 字符为止（issue #684）。
     *
     * <p>残留长度最多 3 字节（UTF-8 最长 4 字节），所以这里的拼接成本是常数级，不会随流长度增长。</p>
     *
     * @param bytes 本块读到的字节（不含上次残留）
     * @return 可安全解码的文本前缀；不完整的尾部存进 {@link #incompleteBytes}
     */
    private String decodeUtf8(final byte[] bytes) {
        final byte[] source;
        if (incompleteBytes.length == 0) {
            source = bytes;
        } else {
            source = new byte[incompleteBytes.length + bytes.length];
            System.arraycopy(incompleteBytes, 0, source, 0, incompleteBytes.length);
            System.arraycopy(bytes, 0, source, incompleteBytes.length, bytes.length);
        }
        final int safeLength = completeUtf8PrefixLength(source);
        incompleteBytes = safeLength == source.length
                ? NO_INCOMPLETE_BYTES
                : Arrays.copyOfRange(source, safeLength, source.length);
        return new String(source, 0, safeLength, StandardCharsets.UTF_8);
    }

    /**
     * 计算字节数组里「最后一个完整 UTF-8 字符」的结束位置（issue #684）。
     *
     * <p>从尾部回退最多 3 个续字节（{@code 10xxxxxx}）找到首字节，再按首字节声明的长度判断它是否
     * 已经收齐：收齐则整块可解；未收齐则从该首字节处切掉，留给下一块。</p>
     *
     * <p>非法字节序列（首字节后面跟了非续字节、或整块都是续字节）不在这里修正 —— 返回整块长度，
     * 交给 {@link String#String(byte[], int, int, java.nio.charset.Charset)} 按 UTF-8 规则产出
     * U+FFFD，这与 JDK 解码器的行为一致，也不会卡住流。</p>
     *
     * @param bytes 待解码字节
     * @return 可安全解码的前缀长度（0 表示整块都还不完整）
     */
    static int completeUtf8PrefixLength(final byte[] bytes) {
        int index = bytes.length - 1;
        int continuations = 0;
        while (index >= 0 && continuations < 3 && (bytes[index] & 0xC0) == 0x80) {
            index--;
            continuations++;
        }
        if (index < 0) {
            return bytes.length;
        }
        final int expected = utf8SequenceLength(bytes[index] & 0xFF);
        if (expected <= 0) {
            return bytes.length;
        }
        final int available = bytes.length - index;
        return available >= expected ? bytes.length : index;
    }

    /**
     * UTF-8 首字节声明的序列总长度.
     *
     * @param lead 首字节（无符号）
     * @return 1–4；非法首字节返回 -1
     */
    private static int utf8SequenceLength(final int lead) {
        if ((lead & 0x80) == 0) {
            return 1;
        }
        if ((lead & 0xE0) == 0xC0) {
            return 2;
        }
        if ((lead & 0xF0) == 0xE0) {
            return 3;
        }
        if ((lead & 0xF8) == 0xF0) {
            return 4;
        }
        return -1;
    }

    /**
     * 逐字符找换行切行。
     *
     * <p>超限行不是「丢掉」，而是切到<b>直通模式</b>：已缓存的字节立刻作为原文发出，后续字节
     * 逐段原样发出，直到这一行结束 —— 这样既不把整行攒进内存，也不会丢字节或错位。</p>
     */
    private List<Line> appendAndExtract(final String chunk) {
        final List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < chunk.length(); i++) {
            if (chunk.charAt(i) != '\n') {
                continue;
            }
            final String segment = chunk.substring(start, i + 1);
            start = i + 1;
            if (pendingOversized) {
                // 直通模式：本段原样发出，行到此结束，下一行恢复脱敏
                pendingOversized = false;
                lines.add(new Line(segment, true));
                continue;
            }
            final String line = pending + segment;
            pending.setLength(0);
            if (line.length() > maxLineBytes) {
                warnOversize();
                lines.add(new Line(line, true));
            } else {
                lines.add(new Line(line, false));
            }
        }
        if (start < chunk.length()) {
            appendPending(chunk.substring(start), lines);
        }
        return lines;
    }

    private void appendPending(final String segment, final List<Line> lines) {
        if (pendingOversized) {
            // 直通模式下不攒字节：直接原样发出
            lines.add(new Line(segment, true));
            return;
        }
        if (pending.length() + segment.length() > maxLineBytes) {
            // 超限：先把已攒的前缀原样发出，再进入直通模式（告警只报一次）
            warnOversize();
            lines.add(new Line(pending + segment, true));
            pending.setLength(0);
            pendingOversized = true;
            return;
        }
        pending.append(segment);
    }

    private void warnOversize() {
        warn("SSE 单行超过上限 " + maxLineBytes + " 字节，该行跳过脱敏");
    }

    /**
     * 只处理 {@code data:} 行；脱敏失败回退原文。
     */
    private Mono<String> sanitizeLine(final String line) {
        final DataLine dataLine = DataLine.parse(line);
        if (dataLine == null) {
            return Mono.just(line);
        }
        return sanitizationService.sanitizeResponse(dataLine.payload(), "application/json")
                .map(sanitized -> dataLine.rebuild(sanitized))
                .onErrorResume(ex -> {
                    warn("SSE 帧脱敏失败，回退原文: " + ex.getClass().getSimpleName()
                            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
                    return Mono.just(line);
                });
    }

    private Flux<DataBuffer> flushTail() {
        if (incompleteBytes.length > 0) {
            // 流到这里结束 ⇒ 尾部字节永远等不到补齐，只能按替换字符收尾（#684），不能静默丢弃
            pending.append(new String(incompleteBytes, StandardCharsets.UTF_8));
            incompleteBytes = NO_INCOMPLETE_BYTES;
        }
        if (pending.length() == 0) {
            return Flux.empty();
        }
        final String tail = pending.toString();
        final boolean raw = pendingOversized;
        pending.setLength(0);
        pendingOversized = false;
        final Mono<String> out = raw ? Mono.just(tail) : sanitizeLine(tail);
        return out.map(this::toBuffer).flux();
    }

    private DataBuffer toBuffer(final String text) {
        return bufferFactory.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private void warn(final String message) {
        log.warn("{}", message);
        if (warningSink != null) {
            warningSink.accept(message);
        }
    }

    /**
     * 切好的一段：{@code raw=true} 表示已进入直通模式（超限或直通中段），原样发出。
     */
    private record Line(String text, boolean raw) {
    }

    /**
     * 拆解出的 {@code data:} 行：保留「前缀 + 可选单个空格」与行尾，只有负载被替换。
     */
    private record DataLine(String prefix, String payload, String terminator) {

        /**
         * @return 不是可脱敏的数据行时返回 {@code null}（注释行、event/id/retry、空行、
         *         {@code [DONE]} 哨兵、非 {@code data:} 前缀）
         */
        static DataLine parse(final String line) {
            if (!line.startsWith(DATA_PREFIX)) {
                return null;
            }
            final String terminator = line.endsWith("\r\n") ? "\r\n"
                    : line.endsWith("\n") ? "\n" : "";
            final int bodyEnd = line.length() - terminator.length();
            int payloadStart = DATA_PREFIX.length();
            // SSE 规范：冒号后若紧跟一个空格，该空格属于分隔符而不是数据
            if (payloadStart < bodyEnd && line.charAt(payloadStart) == ' ') {
                payloadStart++;
            }
            final String payload = line.substring(payloadStart, bodyEnd);
            if (payload.isEmpty() || DONE_SENTINEL.equals(payload.trim())) {
                return null;
            }
            return new DataLine(line.substring(0, payloadStart), payload, terminator);
        }

        String rebuild(final String sanitizedPayload) {
            return prefix + sanitizedPayload + terminator;
        }
    }
}
