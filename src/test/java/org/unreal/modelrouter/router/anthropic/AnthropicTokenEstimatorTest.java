package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.quota.QuotaTokenEstimator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnthropicTokenEstimator} 单测（v3.1 PR-4c）.
 *
 * <p>覆盖：{@code system} + {@code messages} 文本量拼接、非文本块与空块忽略、
 * 与 {@link QuotaTokenEstimator#estimateFromText(String)} 同源（同一系数）、
 * {@code null} 请求与空内容返回 0。</p>
 */
@DisplayName("Anthropic token 估算器")
class AnthropicTokenEstimatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AnthropicMessagesRequest parse(final String json) throws Exception {
        return MAPPER.readValue(json, AnthropicMessagesRequest.class);
    }

    @Nested
    @DisplayName("请求估算")
    class EstimateRequest {

        @Test
        @DisplayName("空请求/空内容 → 0")
        void emptyRequestIsZero() throws Exception {
            assertEquals(0L, AnthropicTokenEstimator.estimateRequest(null));
            assertEquals(0L, AnthropicTokenEstimator.estimateRequest(parse("{}")));
            assertEquals(0L, AnthropicTokenEstimator.estimateRequest(
                    parse("{\"model\":\"deepseek-chat\",\"messages\":[]}")));
        }

        @Test
        @DisplayName("system 与 messages 文本块一并计入")
        void countsSystemAndMessages() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"deepseek-chat","system":"你是助手",
                     "messages":[{"role":"user","content":"hello world"}]}""");

            // 4 个汉字 / 2 = 2；"hello world" 去掉空白 10 个字符 / 4 = 2.5 → 合计 ceil(4.5) = 5
            assertEquals(5L, AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("与 QuotaTokenEstimator.estimateFromText 同源（逐字符累加口径一致）")
        void sameScaleAsQuotaEstimator() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","system":"系统提示",
                     "messages":[{"role":"user","content":[{"type":"text","text":"你好，帮我写代码"}]}]}""");

            String joined = "系统提示" + "你好，帮我写代码";
            assertEquals(QuotaTokenEstimator.estimateFromText(joined),
                    AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("非文本块（image/tool_use）与空块不计入")
        void skipsNonTextBlocks() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"text","text":"abcd"},
                        {"type":"image","source":{"type":"base64","data":"AAAA"}},
                        {"type":"tool_result","content":"ignored"}]}]}""");

            // 仅 "abcd" → 4 / 4 = 1
            assertEquals(1L, AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("null 消息元素不抛异常")
        void toleratesNullMessage() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","messages":[null,{"role":"user","content":"abcd"}]}""");
            assertEquals(1L, AnthropicTokenEstimator.estimateRequest(request));
        }
    }

    @Nested
    @DisplayName("文本估算")
    class EstimateText {

        @Test
        @DisplayName("null/空串 → 0；中文 2 字符/token、英文 4 字符/token")
        void basicRatios() {
            assertEquals(0L, AnthropicTokenEstimator.estimateText(null));
            assertEquals(0L, AnthropicTokenEstimator.estimateText(""));
            assertEquals(2L, AnthropicTokenEstimator.estimateText("你好世界"));
            assertEquals(2L, AnthropicTokenEstimator.estimateText("abcdefgh"));
            assertTrue(AnthropicTokenEstimator.estimateText("混合 mixed 内容") > 0);
        }
    }
}
