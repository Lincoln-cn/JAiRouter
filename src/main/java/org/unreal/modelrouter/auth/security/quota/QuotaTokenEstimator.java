package org.unreal.modelrouter.auth.security.quota;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;

/**
 * 请求侧 token 估算器（v3.1 PR-2）。
 *
 * <p>配额预留需要“本次请求预计消耗多少 token”作为上限判定的输入。仓库中不存在通用的
 * 请求侧估算器（{@code StreamingRequestProcessor#estimateTokens} 只处理响应内容），
 * 因此这里按“字符启发式”估算 prompt 侧规模：</p>
 * <ul>
 *   <li>中日韩表意文字（HAN）约 2 字符 / token，其余非空白字符约 4 字符 / token
 *       ——与 {@code StreamingRequestProcessor#estimateTokens} 保持同一系数，便于对账；</li>
 *   <li>chat 取全部 message 的 {@code content}；embedding 取 {@code input}（字符串或字符串数组）；
 *       rerank 取 {@code query} + 全部 {@code documents}；</li>
 *   <li>其余服务类型（tts / stt / image 等）无可靠文本量信号，返回 0
 *       ——此时预留只计请求数，实际 token 在结算时补记（{@code actual - 0}）。</li>
 * </ul>
 *
 * <p>已知偏差（设计取舍）：估算不含 {@code max_tokens} 等输出侧规模，也不含下游真实分词差异，
 * 因此“in-flight 请求的输出 token”不参与上限预占；结算时会按实际用量冲正。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
public final class QuotaTokenEstimator {

    /** 表意文字（中文等）每 token 字符数 */
    private static final double CHINESE_CHARS_PER_TOKEN = 2.0;

    /** 其余字符每 token 字符数 */
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;

    private QuotaTokenEstimator() {
    }

    /**
     * 估算一次请求的 prompt 侧 token 数。
     *
     * @param requestDto 原始请求 DTO（Controller 放入 exchange 的 {@code JAIR_REQUEST_DTO} 属性），
     *                   可为 {@code null} 或不认识的类型
     * @return 估算 token 数，无法估算时返回 0（非负）
     */
    public static long estimate(final Object requestDto) {
        if (requestDto instanceof ChatDTO.Request chatRequest) {
            return estimateChat(chatRequest);
        }
        if (requestDto instanceof EmbeddingDTO.Request embeddingRequest) {
            return estimateEmbeddingInput(embeddingRequest.input());
        }
        if (requestDto instanceof RerankDTO.Request rerankRequest) {
            return estimateRerank(rerankRequest);
        }
        return 0L;
    }

    /**
     * 按“中文字符 / 2 + 其余非空白字符 / 4”向上取整估算文本 token 数。
     *
     * @param text 文本，可为 {@code null}
     * @return 估算 token 数（最小 0）
     */
    public static long estimateFromText(final String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN
            + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }

    /**
     * 估算 chat 请求的 prompt token 数（全部 message content 拼接后估算）。
     *
     * @param request chat 请求
     * @return 估算 token 数
     */
    private static long estimateChat(final ChatDTO.Request request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return 0L;
        }
        final StringBuilder content = new StringBuilder();
        for (final ChatDTO.Message message : request.messages()) {
            if (message != null && message.content() != null) {
                content.append(message.content());
            }
        }
        return estimateFromText(content.toString());
    }

    /**
     * 估算 embedding 请求的 token 数（{@code input} 可为字符串或字符串数组）。
     *
     * @param input 原始 input 字段
     * @return 估算 token 数，形态不可识别时返回 0
     */
    private static long estimateEmbeddingInput(final Object input) {
        if (input instanceof CharSequence text) {
            return estimateFromText(text.toString());
        }
        if (input instanceof Iterable<?> items) {
            final StringBuilder content = new StringBuilder();
            for (final Object item : items) {
                if (item instanceof CharSequence text) {
                    content.append(text);
                }
            }
            return estimateFromText(content.toString());
        }
        return 0L;
    }

    /**
     * 估算 rerank 请求的 token 数（query + documents）。
     *
     * @param request rerank 请求
     * @return 估算 token 数
     */
    private static long estimateRerank(final RerankDTO.Request request) {
        final StringBuilder content = new StringBuilder();
        if (request.query() != null) {
            content.append(request.query());
        }
        if (request.documents() != null) {
            for (final String document : request.documents()) {
                if (document != null) {
                    content.append(document);
                }
            }
        }
        return estimateFromText(content.toString());
    }
}
