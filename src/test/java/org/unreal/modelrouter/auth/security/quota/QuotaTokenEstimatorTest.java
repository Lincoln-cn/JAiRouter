package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link QuotaTokenEstimator} 请求侧估算测试（v3.1 PR-2）。
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaTokenEstimator 请求侧估算测试")
class QuotaTokenEstimatorTest {

    @Test
    @DisplayName("chat：全部 message content 参与估算（4 英文 / 2 中文 字符每 token）")
    void chat_shouldEstimateFromMessages() {
        final ChatDTO.Request request = new ChatDTO.Request("gpt-4",
            List.of(new ChatDTO.Message("user", "abcdefgh", null),
                new ChatDTO.Message("assistant", "你好", null)),
            false, 256, 0.0, null, null, null, null, null, null, null);

        // 8 英文 → 2；2 中文 → 1
        assertEquals(3L, QuotaTokenEstimator.estimate(request));
    }

    @Test
    @DisplayName("chat：无 messages 时返回 0")
    void chatWithoutMessages_shouldReturnZero() {
        final ChatDTO.Request request = new ChatDTO.Request("gpt-4", null,
            false, null, null, null, null, null, null, null, null, null);

        assertEquals(0L, QuotaTokenEstimator.estimate(request));
    }

    @Test
    @DisplayName("embedding：input 为字符串或字符串数组均参与估算")
    void embedding_shouldEstimateFromInput() {
        final EmbeddingDTO.Request single = new EmbeddingDTO.Request("bge", "abcdefgh", null, null, null, null);
        final EmbeddingDTO.Request multiple = new EmbeddingDTO.Request("bge",
            List.of("abcd", "abcd"), null, null, null, null);

        assertEquals(2L, QuotaTokenEstimator.estimate(single));
        assertEquals(2L, QuotaTokenEstimator.estimate(multiple));
    }

    @Test
    @DisplayName("rerank：query + documents 参与估算")
    void rerank_shouldEstimateFromQueryAndDocuments() {
        final RerankDTO.Request request = new RerankDTO.Request("bge-reranker", "query",
            List.of("document"), null, null, null);

        // "querydocument" = 13 字符 → ceil(13/4) = 4
        assertEquals(4L, QuotaTokenEstimator.estimate(request));
    }

    @Test
    @DisplayName("不可估算的输入（null / 未知 DTO / 非文本 input）返回 0")
    void unknownInput_shouldReturnZero() {
        assertEquals(0L, QuotaTokenEstimator.estimate(null));
        assertEquals(0L, QuotaTokenEstimator.estimate("not-a-dto"));
        assertEquals(0L, QuotaTokenEstimator.estimate(
            new EmbeddingDTO.Request("bge", 1234, null, null, null, null)));
    }

    @Test
    @DisplayName("空白字符不计入估算")
    void whitespace_shouldBeIgnored() {
        assertEquals(0L, QuotaTokenEstimator.estimateFromText("   \n\t"));
        assertEquals(2L, QuotaTokenEstimator.estimateFromText("abcdefgh"));
    }
}
