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

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端原生面（{@code /v1/**}）网关自身错误体映射器（v3.1 PR-4d）.
 *
 * <p>{@code /v1/**} 是 OpenAI SDK / Claude Code 直接消费的协议面，网关自身的错误（配额 429、
 * 下游 5xx、400 解析失败、鉴权 401/403 …）若继续按控制台面 {@code RouterResponse} 形状输出，
 * 客户端会解析失败。本组件把「HTTP 状态 + 消息 + 既有 errorCode」翻译为客户端可解析的两种形状：</p>
 * <ul>
 *   <li>{@code /v1/messages}（Anthropic 面，含 {@code /v1/messages/count_tokens}）：
 *       {@code {"type":"error","error":{"type":"<映射>","message":"<原消息>"}}}；</li>
 *   <li>其余 {@code /v1/**}（OpenAI 面，如 {@code /v1/chat/completions}、{@code /v1/models}）：
 *       {@code {"error":{"message":"<原消息>","type":"<映射>","code":"<原 errorCode>"}}}。</li>
 * </ul>
 *
 * <p>两侧共用同一张「HTTP 状态 → type」映射表（{@link #errorTypeOf(int)}）；Anthropic 面按协议
 * <b>不带</b> {@code code} 字段，OpenAI 面保留。{@code /api/**}（控制台面）与其它路径不参与映射，
 * {@link #toErrorBody(String, int, String, String)} 返回 {@code null}，由调用方沿用
 * {@code RouterResponse} 形状。</p>
 *
 * <p>本类只负责「错误体形状」，<b>不</b>参与状态码与日志：状态码由调用方原样设置。</p>
 *
 * <p>{@code @RestControllerAdvice} 的复用入口为
 * {@link #toClientErrorResponse(ServerWebExchange, int, String, String)}（v3.1 PR-4d.1 起
 * {@code ReactiveGlobalExceptionHandler} / {@code GlobalControllerExceptionHandler} /
 * {@code SecurityExceptionHandler} 共用同一套判定与映射）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
public final class V1ErrorBodyMapper {

    /**
     * 客户端原生面路径前缀（仅此前缀下的错误体做协议形状映射）.
     */
    public static final String V1_PREFIX = "/v1/";

    /**
     * Anthropic Messages 面路径（其子路径如 {@code /v1/messages/count_tokens} 同属该面）.
     */
    public static final String ANTHROPIC_MESSAGES_PATH = "/v1/messages";

    /**
     * Anthropic 错误体外层 {@code type} 值（恒为 {@code error}）.
     */
    private static final String ANTHROPIC_OBJECT_TYPE = "error";

    /**
     * 错误类型：请求不合法（400/422 及未单独列出的 4xx）.
     */
    private static final String TYPE_INVALID_REQUEST = "invalid_request_error";

    /**
     * 错误类型：认证失败（401）.
     */
    private static final String TYPE_AUTHENTICATION = "authentication_error";

    /**
     * 错误类型：无权限（403）.
     */
    private static final String TYPE_PERMISSION = "permission_error";

    /**
     * 错误类型：资源不存在（404）.
     */
    private static final String TYPE_NOT_FOUND = "not_found_error";

    /**
     * 错误类型：限流/配额超限（429）.
     */
    private static final String TYPE_RATE_LIMIT = "rate_limit_error";

    /**
     * 错误类型：网关/下游内部错误（5xx）.
     */
    private static final String TYPE_API = "api_error";

    private V1ErrorBodyMapper() {
    }

    /**
     * 读取请求路径（空安全）.
     *
     * <p>{@code exchange} 或其中的请求为 {@code null} 时返回 {@code null}（例如仅关心响应写入的
     * 单测桩），此时一切判断都退化为「非 {@code /v1/**}」，行为与改动前一致。</p>
     *
     * @param exchange 当前交换对象，可为 {@code null}
     * @return 请求路径，不可用时为 {@code null}
     */
    public static String requestPathOf(final ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        final ServerHttpRequest request = exchange.getRequest();
        return request == null ? null : request.getPath().value();
    }

    /**
     * 是否属于客户端原生面（{@code /v1/**}）.
     *
     * @param path 请求路径，可为 {@code null}
     * @return {@code true} 表示按客户端协议形状输出错误体
     */
    public static boolean isV1Path(final String path) {
        return path != null && path.startsWith(V1_PREFIX);
    }

    /**
     * 是否属于 Anthropic Messages 面.
     *
     * @param path 请求路径，可为 {@code null}
     * @return {@code true} 表示 {@code /v1/messages} 或其子路径
     */
    public static boolean isAnthropicPath(final String path) {
        return path != null
                && (path.equals(ANTHROPIC_MESSAGES_PATH) || path.startsWith(ANTHROPIC_MESSAGES_PATH + "/"));
    }

    /**
     * HTTP 状态码 → 客户端错误类型（Anthropic / OpenAI 共用同一张表）.
     *
     * @param statusCode HTTP 状态码
     * @return 映射后的错误类型；未单独列出的 4xx/3xx 归 {@code invalid_request_error}，
     *         5xx 归 {@code api_error}
     */
    public static String errorTypeOf(final int statusCode) {
        return switch (statusCode) {
            case 400 -> TYPE_INVALID_REQUEST;
            case 401 -> TYPE_AUTHENTICATION;
            case 403 -> TYPE_PERMISSION;
            case 404 -> TYPE_NOT_FOUND;
            case 429 -> TYPE_RATE_LIMIT;
            default -> statusCode >= 500 ? TYPE_API : TYPE_INVALID_REQUEST;
        };
    }

    /**
     * 构造客户端协议形状的错误体.
     *
     * @param path       请求路径
     * @param statusCode HTTP 状态码（仅用于选类型，不参与状态码设置）
     * @param message    原错误消息（原样透出，不做包装）
     * @param errorCode  原错误码（仅 OpenAI 面带 {@code code} 字段时使用，可为 {@code null}）
     * @return Anthropic 面 / OpenAI 面错误体；非 {@code /v1/**} 路径返回 {@code null}（调用方沿用
     *         {@code RouterResponse}）
     */
    public static Map<String, Object> toErrorBody(final String path, final int statusCode,
                                                  final String message, final String errorCode) {
        if (!isV1Path(path)) {
            return null;
        }
        final String errorType = errorTypeOf(statusCode);
        final Map<String, Object> body = new LinkedHashMap<>();
        final Map<String, Object> error = new LinkedHashMap<>();
        if (isAnthropicPath(path)) {
            error.put("type", errorType);
            error.put("message", message);
            body.put("type", ANTHROPIC_OBJECT_TYPE);
        } else {
            error.put("message", message);
            error.put("type", errorType);
            error.put("code", errorCode);
        }
        body.put("error", error);
        return body;
    }

    /**
     * 构造客户端协议形状的完整错误响应（状态码 + {@code application/json} + 协议错误体）.
     *
     * <p>v3.1 PR-4d.1：{@code @RestControllerAdvice}（如
     * {@code SecurityExceptionHandler}）的 {@code @ExceptionHandler} 需要「要么协议形状、要么
     * 沿用自身既有错误体」的二选一，本方法把这套分支收敛到与
     * {@link #toErrorBody(String, int, String, String)} <b>同一张</b>映射表上，避免第二个 advice
     * 里再复制一份路径判定与 HTTP→type 映射。</p>
     *
     * <p>语义与 {@link #toErrorBody(String, int, String, String)} 完全一致：非 {@code /v1/**}
     * 路径返回 {@code null}（调用方据此回落到自己的既有错误体，控制台面契约不变）；
     * {@code /v1/**} 返回协议形状响应。状态码原样透传给 {@code ResponseEntity}，<b>不做</b>任何
     * 覆盖或归一，因此调用方既有的状态码语义保持不变。</p>
     *
     * @param exchange   当前交换对象（仅用于取请求路径；可为 {@code null}）
     * @param statusCode HTTP 状态码（原样成为响应状态码，同时用于选 type）
     * @param message    原错误消息（原样透出）
     * @param errorCode  原错误码（仅 OpenAI 面带 {@code code} 字段时使用，可为 {@code null}）
     * @return {@code /v1/**}：协议形状响应；其余路径：{@code null}
     */
    public static ResponseEntity<?> toClientErrorResponse(final ServerWebExchange exchange, final int statusCode,
                                                          final String message, final String errorCode) {
        final Map<String, Object> body = toErrorBody(requestPathOf(exchange), statusCode, message, errorCode);
        if (body == null) {
            return null;
        }
        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
