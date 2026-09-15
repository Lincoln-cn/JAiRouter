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
 * <p>覆盖：{@code system} + {@code messages} 文本量拼接、
 * 工具块（{@code tool_use} / {@code tool_result}）已计入、
 * {@code tools[]} schema 已计入、image 块仍跳过、
 * 与 {@link QuotaTokenEstimator#estimateFromText(String)} 同源（同一系数）、
 * {@code null} 请求与空内容返回 0。</p>
 *
 * <p><b>规格变更声明（v3.1.1）</b>：{@code tool_use} 块（name + input）、
 * {@code tool_result} 块（content 文本）与 {@code tools[]} 的 name + description +
 * inputSchema 序列化文本现在均计入估算（此前跳过）。
 * 这是与 Anthropic 官方 count_tokens 对齐的关键变更——Claude Code 的对话主体
 * 就是 tool_result 文件内容，不计会大幅低估导致客户端撑爆上下文。</p>
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
        @DisplayName("image 块仍跳过，tool_use/tool_result 已计入（v3.1.1 规格变更）")
        void imageSkippedToolBlocksCounted() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"text","text":"abcd"},
                        {"type":"image","source":{"type":"base64","data":"AAAA"}},
                        {"type":"tool_result","content":"ignored"}]}]}""");

            // "abcd" (4/4=1) + "ignored" (7/4=1.75) = 2.75 → ceil = 3
            // image 块跳过不计
            assertEquals(3L, AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("null 消息元素不抛异常")
        void toleratesNullMessage() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","messages":[null,{"role":"user","content":"abcd"}]}""");
            assertEquals(1L, AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("仅 tools 的请求 → 估算值 > 0")
        void toolsOnlyRequest_shouldEstimatePositive() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m","tools":[
                      {"name":"get_weather","description":"Get weather info",
                       "input_schema":{"type":"object","properties":{"location":{"type":"string"}}}}]}""");

            long estimate = AnthropicTokenEstimator.estimateRequest(request);
            assertTrue(estimate > 0,
                    "仅 tools 的请求也应有正的估算值: " + estimate);
        }

        @Test
        @DisplayName("tools schema 变大 → 估算值单调递增")
        void largerToolsSchema_shouldIncreaseEstimate() throws Exception {
            AnthropicMessagesRequest smallTool = parse("""
                    {"model":"m","tools":[
                      {"name":"a","description":"b",
                       "input_schema":{"type":"object"}}]}""");

            AnthropicMessagesRequest largeTool = parse("""
                    {"model":"m","tools":[
                      {"name":"a","description":"b",
                       "input_schema":{"type":"object",
                        "properties":{"x":{"type":"string"},"y":{"type":"integer"},
                                      "z":{"type":"boolean"},"w":{"type":"number"}}}}]}""");

            long small = AnthropicTokenEstimator.estimateRequest(smallTool);
            long large = AnthropicTokenEstimator.estimateRequest(largeTool);
            assertTrue(large > small,
                    "更大的 tools schema 应产生更大的估算: small=" + small + ", large=" + large);
        }

        @Test
        @DisplayName("tool_use 块计入（name + input JSON）")
        void toolUseBlockCounted() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m",
                     "messages":[{"role":"assistant","content":[
                        {"type":"tool_use","id":"u1","name":"search",
                         "input":{"query":"hello world"}}]}]}""");

            // "search" (6/4=1.5) + {"query":"hello world"} (~22 chars / 4 = 5.5) = 7 → ceil = 7
            long estimate = AnthropicTokenEstimator.estimateRequest(request);
            assertTrue(estimate > 0,
                    "tool_use 块应产生正的估算: " + estimate);
        }

        @Test
        @DisplayName("tool_result 长文本拉高估算")
        void toolResultLongText_shouldIncreaseEstimate() throws Exception {
            AnthropicMessagesRequest shortResult = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"tool_result","content":"ok"}]}]}""");

            String longContent = "a".repeat(400);
            AnthropicMessagesRequest longResult = parse(
                    "{\"model\":\"m\","
                            + "\"messages\":[{\"role\":\"user\",\"content\":["
                            + "{\"type\":\"tool_result\",\"content\":\"" + longContent + "\"}"
                            + "]}]}");

            long shortEstimate = AnthropicTokenEstimator.estimateRequest(shortResult);
            long longEstimate = AnthropicTokenEstimator.estimateRequest(longResult);
            assertTrue(longEstimate > shortEstimate,
                    "更长的 tool_result 应产生更大估算: short=" + shortEstimate
                            + ", long=" + longEstimate);
            // 400 chars / 4 = 100 tokens (approximately)
            assertTrue(longEstimate >= 99,
                    "400 字符的 tool_result 应产生约 100 tokens: " + longEstimate);
        }

        @Test
        @DisplayName("tool_result 数组形式的 content 也计入")
        void toolResultArrayContent_shouldCountTextBlocks() throws Exception {
            AnthropicMessagesRequest request = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"tool_result","content":[
                          {"type":"text","text":"result one"},
                          {"type":"text","text":"result two"}]}]}]}""");

            // "result one" (10/4=2.5) + "result two" (10/4=2.5) = 5
            assertEquals(5L, AnthropicTokenEstimator.estimateRequest(request));
        }

        @Test
        @DisplayName("image 块始终跳过（即使在混合内容中）")
        void imageAlwaysSkipped_evenInMixedContent() throws Exception {
            // 纯 image → 0
            AnthropicMessagesRequest pureImage = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"image","source":{"type":"base64","data":"AAAA"}}]}]}""");
            assertEquals(0L, AnthropicTokenEstimator.estimateRequest(pureImage));

            // text + image → 仅 text 计入
            AnthropicMessagesRequest mixed = parse("""
                    {"model":"m",
                     "messages":[{"role":"user","content":[
                        {"type":"text","text":"hello"},
                        {"type":"image","source":{"type":"base64","data":"BBBB"}}]}]}""");
            // "hello" = 5 / 4 = 1.25 → ceil = 2
            assertEquals(2L, AnthropicTokenEstimator.estimateRequest(mixed));
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
