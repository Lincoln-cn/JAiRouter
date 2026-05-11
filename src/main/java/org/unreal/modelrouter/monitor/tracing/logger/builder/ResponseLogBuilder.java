/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.monitor.tracing.logger.builder;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.logger.dto.ResponseLogFields;

import java.util.HashMap;
import java.util.Map;

/**
 * 响应日志构建器 - v2.16.4重构
 *
 * 负责构建响应日志的强类型ResponseLogFields DTO：
 * - HTTP状态码和状态文本
 * - 响应大小
 * - 处理时长
 * - 响应头（脱敏处理）
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Component
@RequiredArgsConstructor
public class ResponseLogBuilder {

    private final TracingConfiguration tracingConfiguration;

    /**
     * 构建响应日志字段
     *
     * @param response HTTP响应
     * @param duration 处理时长（毫秒）
     * @return 强类型ResponseLogFields DTO
     */
    public ResponseLogFields buildResponseFields(final ServerHttpResponse response,
                                                   final long duration) {
        ResponseLogFields.ResponseLogFieldsBuilder builder = ResponseLogFields.builder();

        // 响应状态码
        if (response.getStatusCode() != null) {
            builder.statusCode(response.getStatusCode().value());

            // 状态文本（仅HttpStatus有getReasonPhrase方法）
            if (response.getStatusCode() instanceof HttpStatus) {
                HttpStatus httpStatus = (HttpStatus) response.getStatusCode();
                builder.statusText(httpStatus.getReasonPhrase());
            }
        }

        // 响应大小
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > 0) {
            builder.responseSize(contentLength);
        }

        // 处理时长
        builder.duration(duration);

        // 响应头（脱敏处理）
        if (tracingConfiguration.getLogging().isCaptureHeaders()) {
            Map<String, String> sanitizedHeaders = sanitizeHeaders(response.getHeaders().toSingleValueMap());
            builder.headers(sanitizedHeaders);
        }

        return builder.build();
    }

    /**
     * 构建响应日志消息
     *
     * @param duration 处理时长（毫秒）
     * @return 日志消息
     */
    public String buildResponseMessage(final long duration) {
        return String.format("HTTP请求完成，耗时: %dms", duration);
    }

    /**
     * 脱敏处理HTTP头部
     *
     * @param headers 原始头部
     * @return 脱敏后的头部
     */
    public Map<String, String> sanitizeHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }

        Map<String, String> sanitizedHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 敏感头部直接脱敏
            if (isSensitiveHeader(key)) {
                sanitizedHeaders.put(key, "***");
            } else {
                sanitizedHeaders.put(key, value);
            }
        }

        return sanitizedHeaders;
    }

    /**
     * 检查是否为敏感头部
     *
     * @param headerName 头部名称
     * @return 是否敏感
     */
    public boolean isSensitiveHeader(final String headerName) {
        if (headerName == null) {
            return false;
        }

        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization")
               || lowerName.contains("token")
               || lowerName.contains("key")
               || lowerName.contains("secret")
               || lowerName.contains("password");
    }
}