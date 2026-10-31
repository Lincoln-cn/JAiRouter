/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误面契约快照（质量门禁：错误体形状的「逐键」守卫）.
 *
 * <p>仓库里已有多组用例覆盖错误体的「有没有某个字段」（{@code V1ClientErrorBodyMappingTest}、
 * {@code SecurityExceptionHandlerTest}、{@code GenericErrorNoDetailLeakTest} …），但它们各自只盯
 * 自己关心的那一面。这里补的是另一维度：<b>把三个对外错误面的键集合与字面值钉死</b>，任何字段增删
 * （哪怕多一个 {@code details}、少一个 {@code code}）都会红 —— 因为客户端 SDK 是按键解析的，
 * 「多一个键」对严格解析器同样是破坏性变更。</p>
 *
 * <p>三面：</p>
 * <ol>
 *   <li>{@code /v1/**}（OpenAI 面）→ {@code {"error":{"message","type","code"}}}</li>
 *   <li>{@code /v1/messages}（Anthropic 面）→ {@code {"type":"error","error":{"type","message"}}}
 *       （协议规定不带 {@code code}）</li>
 *   <li>{@code /api/**} 等控制台面 → {@link RouterResponse}（{@code success/message/errorCode/timestamp}）</li>
 * </ol>
 *
 * @author JAiRouter Team
 * @since v3.5.8
 */
@DisplayName("错误面契约快照：三面的键集合与映射表逐键钉死")
class ErrorBodyContractSnapshotTest {

    /** 对外出现的全部错误状态码（400 参数 / 401 认证 / 403 权限 / 404 模型或资源 / 413 体积 / 429 限流 / 5xx 服务端）。 */
    private static final List<Integer> STATUSES = List.of(400, 401, 403, 404, 413, 429, 500, 502, 503);

    private static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String ANTHROPIC_PATH = "/v1/messages";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Nested
    @DisplayName("OpenAI 面（/v1/**，SDK 按 {error:{message,type,code}} 解析）")
    class OpenAiSurface {

        @Test
        @DisplayName("每个状态码的键集合与 type 映射都钉死：只允许 error.… 三个键，不多不少")
        void keysArePinnedForEveryStatus() {
            for (Integer status : STATUSES) {
                Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
                        OPENAI_PATH, status, "boom", String.valueOf(status));

                assertNotNull(body, status + " 应返回协议错误体");
                assertEquals(List.of("error"), new ArrayList<>(body.keySet()),
                        status + " 的顶层键必须恰好是 error");

                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                assertEquals(List.of("message", "type", "code"), new ArrayList<>(error.keySet()),
                        status + " 的 error 键必须恰好是 message/type/code（顺序也钉死，便于人工比对文档）");
                assertEquals("boom", error.get("message"), status + " 必须原样透出消息");
                assertEquals(String.valueOf(status), error.get("code"), status + " 的 code 必须是状态码字符串");
                assertEquals(V1ErrorBodyMapper.errorTypeOf(status), error.get("type"),
                        status + " 的 type 必须来自同一张映射表");
            }
        }

        @Test
        @DisplayName("413 在 OpenAI 面仍归 invalid_request_error（协议没有 request_too_large）")
        void payloadTooLargeStaysInvalidRequestOnOpenAiSurface() {
            Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(OPENAI_PATH, 413, "too big", "413");
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            assertEquals("invalid_request_error", error.get("type"));
        }
    }

    @Nested
    @DisplayName("Anthropic 面（/v1/messages，SDK 按 {type:error,error:{type,message}} 解析）")
    class AnthropicSurface {

        @Test
        @DisplayName("键集合钉死：顶层 type=error，error 内只有 type/message，不带 code")
        void keysArePinnedForEveryStatus() {
            for (Integer status : STATUSES) {
                Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
                        ANTHROPIC_PATH, status, "boom", String.valueOf(status));

                assertNotNull(body, status + " 应返回协议错误体");
                assertEquals(List.of("type", "error"), new ArrayList<>(body.keySet()),
                        status + " 的顶层键必须恰好是 type/error");
                assertEquals("error", body.get("type"));

                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                assertEquals(List.of("type", "message"), new ArrayList<>(error.keySet()),
                        status + " 的 error 键必须恰好是 type/message —— Anthropic 协议不带 code");
                assertFalse(error.containsKey("code"), status + " 不得出现 code 字段");
                assertEquals(V1ErrorBodyMapper.errorTypeOf(ANTHROPIC_PATH, status), error.get("type"),
                        status + " 的 type 必须来自同一张映射表");
            }
        }

        @Test
        @DisplayName("413 在 Anthropic 面用协议专用的 request_too_large（与 OpenAI 面刻意不同）")
        void payloadTooLargeUsesAnthropicSpecificType() {
            Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(ANTHROPIC_PATH, 413, "too big", "413");
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            assertEquals("request_too_large", error.get("type"));
            assertFalse(body.containsKey("code"));
        }

        @Test
        @DisplayName("/v1/messages/count_tokens 同属 Anthropic 面")
        void countTokensIsAnthropicToo() {
            Map<String, Object> body = V1ErrorBodyMapper.toErrorBody(
                    "/v1/messages/count_tokens", 429, "slow down", "429");
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            assertEquals(List.of("type", "message"), new ArrayList<>(error.keySet()));
        }
    }

    @Nested
    @DisplayName("控制台面（/api/**、/admin/**）仍走 RouterResponse")
    class ConsoleSurface {

        @Test
        @DisplayName("非 /v1 路径不返回协议错误体（返回 null 表示回落到 RouterResponse）")
        void nonV1PathsReturnNull() {
            for (String path : List.of("/api/v1/chat/completions", "/api/models", "/admin/users", "/actuator/health")) {
                assertNull(V1ErrorBodyMapper.toErrorBody(path, 429, "boom", "429"),
                        path + " 不应被 /v1 协议形状接管");
            }
        }

        @Test
        @DisplayName("RouterResponse 错误体的键集合钉死：success/message/errorCode/timestamp（不含 data）")
        void routerResponseShapeIsPinned() throws Exception {
            RouterResponse<Object> response = RouterResponse.error("请求处理失败: 429", "429");
            Map<?, ?> json = objectMapper.readValue(objectMapper.writeValueAsString(response), Map.class);

            assertEquals(List.of("success", "message", "errorCode", "timestamp"), new ArrayList<>(json.keySet()),
                    "控制台面错误体键集合变了 —— 前端与 OpenAPI 文档都按这四个键渲染");
            assertEquals(Boolean.FALSE, json.get("success"));
            assertEquals("请求处理失败: 429", json.get("message"));
            assertEquals("429", json.get("errorCode"));
            assertFalse(json.containsKey("data"), "错误响应不应带 data 字段");
            assertTrue(json.get("timestamp") instanceof String,
                    "timestamp 必须是 'yyyy-MM-dd HH:mm:ss' 字符串，不能是数字时间戳");
        }
    }

    @Nested
    @DisplayName("HTTP 状态 → type 映射表")
    class TypeTable {

        @Test
        @DisplayName("9 个对外状态码的映射逐个钉死（表变动必须显式改这里）")
        void mappingTableIsPinned() {
            Map<Integer, String> expected = new LinkedHashMap<>();
            expected.put(400, "invalid_request_error");
            expected.put(401, "authentication_error");
            expected.put(403, "permission_error");
            expected.put(404, "not_found_error");
            expected.put(413, "invalid_request_error");
            expected.put(429, "rate_limit_error");
            expected.put(500, "api_error");
            expected.put(502, "api_error");
            expected.put(503, "api_error");

            expected.forEach((status, type) ->
                    assertEquals(type, V1ErrorBodyMapper.errorTypeOf(status),
                            status + " 的映射变了 —— 客户端 SDK 依赖该 type 做重试判定"));
        }

        @Test
        @DisplayName("两侧同源：只有 413 分面差异，其余完全一致")
        void twoSurfacesDifferOnlyOn413() {
            for (Integer status : STATUSES) {
                String openai = V1ErrorBodyMapper.errorTypeOf(OPENAI_PATH, status);
                String anthropic = V1ErrorBodyMapper.errorTypeOf(ANTHROPIC_PATH, status);
                if (status == 413) {
                    assertEquals("invalid_request_error", openai);
                    assertEquals("request_too_large", anthropic);
                } else {
                    assertEquals(openai, anthropic, status + " 两侧必须同源");
                }
            }
        }
    }
}
