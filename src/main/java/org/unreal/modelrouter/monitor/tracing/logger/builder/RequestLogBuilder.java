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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.monitor.tracing.logger.dto.RequestLogFields;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求日志构建器 - v2.16.4重构
 *
 * 负责构建请求日志的强类型RequestLogFields DTO：
 * - HTTP方法、路径、URL
 * - 客户端IP（脱敏处理）
 * - User-Agent
 * - 请求大小
 * - 请求头（脱敏处理）
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLogBuilder {

    private final TracingConfiguration tracingConfiguration;
    private final TracingSanitizationService tracingSanitizationService;

    /**
     * 构建请求日志字段
     *
     * @param request HTTP请求
     * @param context 追踪上下文
     * @return 包含RequestLogFields的Mono
     */
    public Mono<RequestLogFields> buildRequestFields(final ServerHttpRequest request,
                                                       final TracingContext context) {
        RequestLogFields.RequestLogFieldsBuilder builder = RequestLogFields.builder();

        // HTTP请求信息
        builder.method(request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
        builder.path(sanitizeUrlPath(request.getPath().value()));
        builder.url(request.getURI().toString());

        // 客户端信息（脱敏处理）
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            builder.clientIp(sanitizeClientIp(clientIp));
        }

        // User-Agent
        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent != null) {
            builder.userAgent(userAgent);
        }

        // 请求大小
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                builder.requestSize(Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                // 忽略无效的Content-Length
            }
        }

        // 请求头（脱敏处理）
        if (tracingConfiguration.getLogging().isCaptureHeaders()) {
            Map<String, String> rawHeaders = request.getHeaders().toSingleValueMap();
            return sanitizeRequestHeaders(rawHeaders)
                    .flatMap(sanitizedHeaders -> {
                        builder.headers(sanitizedHeaders);
                        RequestLogFields fields = builder.build();

                        // 应用追踪特定的脱敏
                        return tracingSanitizationService.sanitizeLogData(fieldsToMap(fields), context)
                                .map(sanitizedMap -> {
                                    // 从脱敏后的Map重建DTO
                                    return rebuildFromMap(sanitizedMap);
                                });
                    });
        } else {
            // 应用追踪特定的脱敏
            RequestLogFields fields = builder.build();
            return tracingSanitizationService.sanitizeLogData(fieldsToMap(fields), context)
                    .map(sanitizedMap -> rebuildFromMap(sanitizedMap));
        }
    }

    /**
     * 构建请求日志消息
     *
     * @return 日志消息
     */
    public String buildRequestMessage() {
        return "HTTP请求开始";
    }

    /**
     * 获取客户端IP地址
     *
     * @param request HTTP请求
     * @return 客户端IP或null
     */
    public String getClientIp(final ServerHttpRequest request) {
        // 检查X-Forwarded-For头部
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // 检查X-Real-IP头部
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 使用远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return null;
    }

    /**
     * 安全处理URL路径
     *
     * @param path 原始路径
     * @return 脱敏后的路径
     */
    public String sanitizeUrlPath(final String path) {
        if (path == null) {
            return null;
        }

        // 移除查询参数中的敏感信息
        if (path.contains("?")) {
            String[] parts = path.split("\\?", 2);
            String basePath = parts[0];
            String queryString = parts[1];

            // 脱敏查询参数
            String sanitizedQuery = sanitizeQueryString(queryString);
            return basePath + "?" + sanitizedQuery;
        }

        return path;
    }

    /**
     * 脱敏客户端IP
     *
     * @param clientIp 原始IP
     * @return 脱敏后的IP
     */
    public String sanitizeClientIp(final String clientIp) {
        if (clientIp == null) {
            return null;
        }

        // 对IPv4地址进行部分脱敏（保留前两段）
        if (clientIp.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            String[] parts = clientIp.split("\\.");
            return parts[0] + "." + parts[1] + ".xxx.xxx";
        }

        // 对IPv6地址进行部分脱敏
        if (clientIp.contains(":")) {
            String[] parts = clientIp.split(":");
            if (parts.length >= 4) {
                return parts[0] + ":" + parts[1] + "::xxxx";
            }
        }

        return clientIp;
    }

    // ========================================
    // 私有辅助方法
    // ========================================

    /**
     * 脱敏请求头
     */
    private Mono<Map<String, String>> sanitizeRequestHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Mono.just(headers);
        }

        Map<String, String> sanitizedHeaders = new HashMap<>();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSensitiveHeader(key)) {
                sanitizedHeaders.put(key, "[REDACTED]");
            } else {
                sanitizedHeaders.put(key, value);
            }
        }

        return Mono.just(sanitizedHeaders);
    }

    /**
     * 检查是否为敏感头部
     */
    private boolean isSensitiveHeader(final String headerName) {
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

    /**
     * 脱敏查询参数
     */
    private String sanitizeQueryString(final String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }

        StringBuilder sanitized = new StringBuilder();
        String[] params = queryString.split("&");

        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sanitized.append("&");
            }

            String param = params[i];
            if (param.contains("=")) {
                String[] keyValue = param.split("=", 2);
                String key = keyValue[0];
                String value = keyValue.length > 1 ? keyValue[1] : "";

                if (isSensitiveQueryParam(key)) {
                    sanitized.append(key).append("=[REDACTED]");
                } else {
                    sanitized.append(param);
                }
            } else {
                sanitized.append(param);
            }
        }

        return sanitized.toString();
    }

    /**
     * 检查是否为敏感查询参数
     */
    private boolean isSensitiveQueryParam(final String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("token")
               || lowerKey.contains("key")
               || lowerKey.contains("password")
               || lowerKey.contains("secret")
               || lowerKey.contains("auth")
               || lowerKey.contains("credential");
    }

    /**
     * 将DTO转换为Map以兼容现有的脱敏服务
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldsToMap(final RequestLogFields fields) {
        Map<String, Object> map = new HashMap<>();
        if (fields.getMethod() != null) {
            map.put("method", fields.getMethod());
        }
        if (fields.getPath() != null) {
            map.put("path", fields.getPath());
        }
        if (fields.getUrl() != null) {
            map.put("url", fields.getUrl());
        }
        if (fields.getClientIp() != null) {
            map.put("clientIp", fields.getClientIp());
        }
        if (fields.getUserAgent() != null) {
            map.put("userAgent", fields.getUserAgent());
        }
        if (fields.getRequestSize() != null) {
            map.put("requestSize", fields.getRequestSize());
        }
        if (fields.getHeaders() != null) {
            map.put("headers", fields.getHeaders());
        }
        return map;
    }

    /**
     * 从Map重建DTO
     */
    @SuppressWarnings("unchecked")
    private RequestLogFields rebuildFromMap(final Map<String, Object> map) {
        RequestLogFields.RequestLogFieldsBuilder builder = RequestLogFields.builder();

        if (map.get("method") != null) {
            builder.method((String) map.get("method"));
        }
        if (map.get("path") != null) {
            builder.path((String) map.get("path"));
        }
        if (map.get("url") != null) {
            builder.url((String) map.get("url"));
        }
        if (map.get("clientIp") != null) {
            builder.clientIp((String) map.get("clientIp"));
        }
        if (map.get("userAgent") != null) {
            builder.userAgent((String) map.get("userAgent"));
        }
        if (map.get("requestSize") != null) {
            builder.requestSize(((Number) map.get("requestSize")).longValue());
        }
        if (map.get("headers") != null) {
            builder.headers((Map<String, String>) map.get("headers"));
        }

        return builder.build();
    }
}